package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Armor HUD addon: draws your four equipped armor pieces (helmet down to boots)
 * with their durability next to each, and turns the number red when a piece
 * drops below the warn threshold. The vanilla item durability bar is drawn too.
 */
public class ArmorHud {

    private static final int ROW_H = 20;

    public static int lastWidth = 70;
    public static int lastHeight = 80;

    private static boolean errorLogged = false;

    private static long lastWarnSoundTime = 0L;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !CelleScannerMod.config.armorHudEnabled) {
            return;
        }
        // A thrown exception in a render handler crashes the whole game, so it's
        // contained here: the HUD just goes dark and logs once instead.
        try {
            render(event);
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                System.err.println("[CelleScanner] Armor HUD render failed, disabling for this session: " + t);
            }
        }
    }

    private void render(RenderGameOverlayEvent.Post event) {
        CelleConfig cfg = CelleScannerMod.config;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return;
        }

        // armorInventory: [0]=boots, [1]=leggings, [2]=chestplate, [3]=helmet.
        ItemStack[] armor = mc.thePlayer.inventory.armorInventory;
        FontRenderer fr = mc.fontRendererObj;
        RenderItem ri = mc.getRenderItem();
        int x = cfg.armorHudX;
        int y = cfg.armorHudY;

        boolean anyLowDurability = false;

        int pieces = 0;
        for (int i = 3; i >= 0; i--) {
            if (armor[i] != null) pieces++;
        }
        lastHeight = Math.max(20, pieces * ROW_H);
        lastWidth = pieces > 0 ? 70 : 20;

        // Pass 1: item icons + vanilla durability bar (needs GUI item lighting).
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        int row = 0;
        for (int i = 3; i >= 0; i--) {
            ItemStack s = armor[i];
            if (s == null) {
                continue;
            }
            int ry = y + row * ROW_H;
            ri.renderItemAndEffectIntoGUI(s, x, ry);
            ri.renderItemOverlayIntoGUI(fr, s, x, ry, null);
            row++;
        }
        RenderHelper.disableStandardItemLighting();

        // Pass 2: exact durability text, red when low.
        GlStateManager.disableLighting();
        row = 0;
        for (int i = 3; i >= 0; i--) {
            ItemStack s = armor[i];
            if (s == null) {
                continue;
            }
            int ry = y + row * ROW_H;
            if (s.isItemStackDamageable()) {
                int max = s.getMaxDamage();
                int remaining = max - s.getItemDamage();
                int pct = max > 0 ? (remaining * 100 / max) : 100;
                int color = pct <= cfg.armorHudWarnPercent ? 0xFF5555 : (pct <= 25 ? 0xFFFF55 : 0xFFFFFF);
                fr.drawStringWithShadow(remaining + "/" + max, x + 20, ry + 4, color);

                if (pct <= cfg.armorHudWarnPercent) {
                    anyLowDurability = true;
                }
            }
            row++;
        }

        // Pass 3: Flashing screen alert & sound warning for low durability
        if (anyLowDurability) {
            long now = System.currentTimeMillis();
            boolean flash = (now / 250) % 2 == 0;
            if (flash) {
                int cx = event.resolution.getScaledWidth() / 2;
                int cy = event.resolution.getScaledHeight() / 2 + 16;
                String alert = net.minecraft.util.EnumChatFormatting.RED + "ADVARSEL: LAV HOLDENHED!";
                fr.drawStringWithShadow(alert, cx - fr.getStringWidth(alert) / 2, cy, 0xFF5555);
            }

            if (now - lastWarnSoundTime > 8000L) { // Warn sound every 8 seconds
                lastWarnSoundTime = now;
                mc.thePlayer.playSound("random.orb", 0.6f, 0.5f);
            }
        }
    }
}
