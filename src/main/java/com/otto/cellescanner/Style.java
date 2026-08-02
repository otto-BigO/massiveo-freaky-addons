package com.otto.cellescanner;

import net.minecraft.client.gui.Gui;

/**
 * Shared look-and-feel for the addon GUIs: flat dark rounded panels and customizable
 * theme accents, in the spirit of modern web design. All cards and HUD components
 * draw through Style using dynamic theme tokens from CelleConfig.
 */
public final class Style {

    public static final int ACCENT = 0xFF4BE08C;        // mint green default accent
    public static final int PANEL_BORDER = 0xFF000000;
    public static final int PANEL_BG = 0xE6101014;      // dark translucent panel

    public static final int BTN_BG = 0xFF26262E;
    public static final int BTN_BG_HOVER = 0xFF34343F;
    public static final int BTN_BG_DISABLED = 0xFF1B1B20;
    public static final int BTN_BORDER = 0xFF121216;
    public static final int TEXT = 0xFFE6E6EA;
    public static final int TEXT_HOVER = 0xFFFFFFFF;
    public static final int TEXT_DISABLED = 0xFFB2B2BA;

    private Style() {
    }

    public static int getAccentColor() {
        if (MassiveOsFreakyAddons.config != null) {
            return 0xFF000000 | (MassiveOsFreakyAddons.config.themeAccentColor & 0xFFFFFF);
        }
        return ACCENT;
    }

    public static String getAccentFormatting() {
        int accent = getAccentColor() & 0xFFFFFF;
        if (accent == 0x00E5FF) return net.minecraft.util.EnumChatFormatting.AQUA.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
        if (accent == 0xB026FF) return net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
        if (accent == 0xFF2A85) return net.minecraft.util.EnumChatFormatting.RED.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
        if (accent == 0xFF9900) return net.minecraft.util.EnumChatFormatting.GOLD.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
        if (accent == 0x505868) return net.minecraft.util.EnumChatFormatting.GRAY.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
        return net.minecraft.util.EnumChatFormatting.GREEN.toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
    }

    /**
     * Draws a 4-sided crisp border outline.
     */
    public static void drawOutline(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1 + 1, y1, x2 - 1, y1 + 1, color);       // Top border
        Gui.drawRect(x1 + 1, y2 - 1, x2 - 1, y2, color);       // Bottom border
        Gui.drawRect(x1, y1 + 1, x1 + 1, y2 - 1, color);       // Left border
        Gui.drawRect(x2 - 1, y1 + 1, x2, y2 - 1, color);       // Right border
    }

    /**
     * A filled rectangle with crisp 4-sided border lines.
     */
    public static void roundedRect(int x1, int y1, int x2, int y2, int color) {
        drawOutline(x1, y1, x2, y2, color);
        Gui.drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, color);
    }

    public static final int COLOR_CELLER = 0xFF00FF88;      // Neon Emerald
    public static final int COLOR_TRACKING = 0xFFFF3366;    // Electric Crimson
    public static final int COLOR_QOL = 0xFF00E5FF;         // Cyber Cyan
    public static final int COLOR_AUTO = 0xFFFF25D2;        // Vivid Magenta

    public static int getCategoryColor(String category) {
        if ("Celler".equalsIgnoreCase(category)) return COLOR_CELLER;
        if ("Tracking".equalsIgnoreCase(category)) return COLOR_TRACKING;
        if ("Quality of life".equalsIgnoreCase(category)) return COLOR_QOL;
        if ("Automation".equalsIgnoreCase(category)) return COLOR_AUTO;
        return getAccentColor();
    }

    /** A panel: a dark rounded body with glowing theme borders. */
    public static void panel(int x1, int y1, int x2, int y2) {
        int accent = getAccentColor();
        float alpha = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.themeBgAlpha : 0.65f;
        int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));
        int borderAlpha = Math.max(30, Math.min(255, (int) (alpha * 255)));
        int glowAlpha = Math.max(20, Math.min(255, (int) (alpha * 0.5f * 255)));

        // Outer border
        drawOutline(x1, y1, x2, y2, (borderAlpha << 24) | 0x14151E);
        // Inner glowing border with theme accent
        int glowColor = (glowAlpha << 24) | (accent & 0xFFFFFF);
        drawOutline(x1 + 1, y1 + 1, x2 - 1, y2 - 1, glowColor);

        // Dark card fill with user transparency
        int bgColor = (alphaInt << 24) | 0x0A0A0F;
        Gui.drawRect(x1 + 2, y1 + 2, x2 - 2, y2 - 2, bgColor);
    }

    /**
     * A centered card the screen content sits inside.
     */
    public static void card(int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        int halfW = Math.min(cx - 8, 170);
        int halfH = Math.min(cy - 8, 150);
        panel(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
    }
}
