package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Mine Profit & Jernmalm Tracker HUD Addon.
 * Tracks Iron Ore (Jernmalm) in inventory, mining rate (Jernmalm/min),
 * and estimated DBs (Diamond Blocks) earned.
 */
public class MineTracker {

    public static final MineTracker INSTANCE = new MineTracker();

    public static int lastWidth = 140;
    public static int lastHeight = 52;

    private int currentIronOre = 0;
    private int estimatedDbs = 0;

    private final List<Long> mineTimestamps = new ArrayList<Long>();
    private int previousIronCount = -1;

    private MineTracker() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        // Calculate Iron Ore in inventory
        int ironCount = 0;
        Item ironItem = Item.getItemFromBlock(Blocks.iron_ore);

        for (int slot = 0; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() == ironItem) {
                ironCount += stack.stackSize;
            }
        }

        long now = System.currentTimeMillis();

        if (previousIronCount != -1 && ironCount > previousIronCount) {
            int added = ironCount - previousIronCount;
            for (int k = 0; k < added; k++) {
                mineTimestamps.add(now);
            }
        }
        previousIronCount = ironCount;
        currentIronOre = ironCount;

        // Purge timestamps older than 60 seconds
        while (!mineTimestamps.isEmpty() && (now - mineTimestamps.get(0)) > 60000) {
            mineTimestamps.remove(0);
        }

        int ratio = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.ironPerDbRatio : 64;
        ratio = Math.max(1, ratio);
        estimatedDbs = currentIronOre / ratio;
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;

        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.mineTrackerEnabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        FontRenderer fr = mc.fontRendererObj;
        float scale = cfg.mineTrackerScale;
        int x = cfg.mineTrackerX;
        int y = cfg.mineTrackerY;

        int jernPerMin = mineTimestamps.size();

        String lineTitle = "§lMine Tracker";
        String lineIron = "Iron Ore: " + currentIronOre + "  (" + jernPerMin + "/min)";
        String lineDb = "DB Estimat: " + estimatedDbs + " DBs";

        int textW = Math.max(fr.getStringWidth("Mine Tracker"), Math.max(fr.getStringWidth(lineIron), fr.getStringWidth(lineDb)));
        int boxW = Math.max(130, textW + 12);
        int boxH = 46;

        lastWidth = (int) (boxW * scale);
        lastHeight = (int) (boxH * scale);

        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, 1.0f);

        int sx = (int) (x / scale);
        int sy = (int) (y / scale);
        int accent = Style.getAccentColor();

        float alpha = cfg.themeBgAlpha;
        int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

        Style.roundedRect(sx, sy, sx + boxW, sy + boxH, 0xFF14151E);
        Style.roundedRect(sx + 1, sy + 1, sx + boxW - 1, sy + boxH - 1, (0x66 << 24) | (accent & 0xFFFFFF));
        Style.roundedRect(sx + 2, sy + 2, sx + boxW - 2, sy + boxH - 2, (alphaInt << 24) | 0x0A0A0F);

        fr.drawStringWithShadow(lineTitle, sx + 6, sy + 6, accent);
        fr.drawStringWithShadow(lineIron, sx + 6, sy + 18, 0xFFFFFF);
        fr.drawStringWithShadow(lineDb, sx + 6, sy + 30, 0x55FFFF);

        GL11.glPopMatrix();
    }
}
