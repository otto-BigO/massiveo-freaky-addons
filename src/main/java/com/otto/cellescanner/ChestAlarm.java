package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Chest Alarm addon: watches incoming chat for the configured keyword (the
 * server's "CHEST-ALARM: ..." line) and, when it appears, flashes a small
 * on-screen notification and plays a note-block sound, so you notice it even
 * if chat is scrolling fast.
 */
public class ChestAlarm {

    private static final long DISPLAY_MS = 5000L;
    private static final long FADE_MS = 1000L;

    // Set when an alarm fires, read by the overlay render. Both run on the
    // client thread, so no synchronization is needed.
    private static String activeMessage = null;
    private static long activeUntil = 0L;

    /** Shows the toast + plays the sound now, honoring the toast/sound toggles. Used by the chat trigger and the GUI test button. */
    public static void fire(String message) {
        CelleConfig cfg = CelleScannerMod.config;
        if (cfg.chestAlarmToast) {
            activeMessage = message;
            activeUntil = System.currentTimeMillis() + DISPLAY_MS;
        }
        if (cfg.chestAlarmSound) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
            }
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.chestAlarmEnabled || event.message == null) {
            return;
        }
        String keyword = cfg.chestAlarmKeyword;
        if (keyword == null || keyword.isEmpty()) {
            return;
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (text == null) {
            return;
        }
        if (text.toLowerCase().contains(keyword.toLowerCase())) {
            fire(text.trim());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || activeMessage == null) {
            return;
        }
        long remaining = activeUntil - System.currentTimeMillis();
        if (remaining <= 0) {
            activeMessage = null;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        ScaledResolution sr = new ScaledResolution(mc);
        FontRenderer fr = mc.fontRendererObj;

        float alpha = remaining >= FADE_MS ? 1.0F : (float) remaining / FADE_MS;
        int textAlpha = Math.max(4, (int) (alpha * 255)) & 0xFF;
        int bgAlpha = (int) (alpha * 0.7F * 255) & 0xFF;

        String msg = activeMessage;
        int textWidth = fr.getStringWidth(msg);
        int boxW = textWidth + 12;
        int x = (sr.getScaledWidth() - boxW) / 2;
        int y = 20;

        Gui.drawRect(x, y, x + boxW, y + 16, bgAlpha << 24);
        fr.drawStringWithShadow(msg, x + 6, y + 4, (textAlpha << 24) | 0xFF5555);
    }
}
