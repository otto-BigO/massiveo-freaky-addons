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

    /** The 16 chat colours and the RGB they actually render as, used to match a custom accent. */
    private static final int[] CHAT_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };
    private static final net.minecraft.util.EnumChatFormatting[] CHAT_CODES = {
            net.minecraft.util.EnumChatFormatting.BLACK, net.minecraft.util.EnumChatFormatting.DARK_BLUE,
            net.minecraft.util.EnumChatFormatting.DARK_GREEN, net.minecraft.util.EnumChatFormatting.DARK_AQUA,
            net.minecraft.util.EnumChatFormatting.DARK_RED, net.minecraft.util.EnumChatFormatting.DARK_PURPLE,
            net.minecraft.util.EnumChatFormatting.GOLD, net.minecraft.util.EnumChatFormatting.GRAY,
            net.minecraft.util.EnumChatFormatting.DARK_GRAY, net.minecraft.util.EnumChatFormatting.BLUE,
            net.minecraft.util.EnumChatFormatting.GREEN, net.minecraft.util.EnumChatFormatting.AQUA,
            net.minecraft.util.EnumChatFormatting.RED, net.minecraft.util.EnumChatFormatting.LIGHT_PURPLE,
            net.minecraft.util.EnumChatFormatting.YELLOW, net.minecraft.util.EnumChatFormatting.WHITE
    };

    /**
     * The chat colour closest to the current accent. Text drawn with a colour code can
     * only use the 16 chat colours, so a custom accent is matched to the nearest one
     * instead of falling back to green, which used to make coloured text ignore the theme.
     */
    public static String getAccentFormatting() {
        int accent = getAccentColor() & 0xFFFFFF;
        int ar = (accent >> 16) & 0xFF, ag = (accent >> 8) & 0xFF, ab = accent & 0xFF;
        int best = 0;
        long bestDist = Long.MAX_VALUE;
        for (int i = 0; i < CHAT_RGB.length; i++) {
            int cr = (CHAT_RGB[i] >> 16) & 0xFF, cg = (CHAT_RGB[i] >> 8) & 0xFF, cb = CHAT_RGB[i] & 0xFF;
            long dr = ar - cr, dg = ag - cg, db = ab - cb;
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return CHAT_CODES[best].toString() + net.minecraft.util.EnumChatFormatting.BOLD.toString();
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

    /** Half width of the centered card. Exposed so callers do not re-derive it. */
    public static int cardHalfWidth(int screenW) {
        return Math.min(screenW / 2 - 8, 170);
    }

    /** Half height of the centered card. */
    public static int cardHalfHeight(int screenH) {
        return Math.min(screenH / 2 - 8, 150);
    }

    /**
     * A centered card the screen content sits inside.
     */
    public static void card(int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        int halfW = cardHalfWidth(screenW);
        int halfH = cardHalfHeight(screenH);
        panel(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
    }

    /**
     * A soft accent halo just outside the card. Alpha is supplied by the caller so
     * it can breathe with a slow pulse without this class holding animation state.
     */
    public static void cardGlow(int screenW, int screenH, float strength) {
        if (strength <= 0f) {
            return;
        }
        if (strength > 1f) {
            strength = 1f;
        }
        int cx = screenW / 2;
        int cy = screenH / 2;
        int halfW = cardHalfWidth(screenW);
        int halfH = cardHalfHeight(screenH);
        int accent = getAccentColor() & 0xFFFFFF;

        // Three rings, each dimmer and further out, approximating a blur.
        for (int i = 1; i <= 3; i++) {
            int a = (int) (strength * (26f / i));
            if (a <= 1) {
                continue;
            }
            int col = (a << 24) | accent;
            int pad = i * 2;
            drawOutline(cx - halfW - pad, cy - halfH - pad, cx + halfW + pad, cy + halfH + pad, col);
        }
    }
}
