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

    private static boolean errorLogged = false;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !CelleScannerMod.config.armorHudEnabled) {
            return;
        }
        // A thrown exception in a render handler crashes the whole game, so it's
        // contained here: the HUD just goes dark and logs once instead.
        try {
            render();
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                System.err.println("[CelleScanner] Armor HUD render failed, disabling for this session: " + t);
            }
        }
    }

    private void render() {
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
            }
            row++;
        }
    }
}
