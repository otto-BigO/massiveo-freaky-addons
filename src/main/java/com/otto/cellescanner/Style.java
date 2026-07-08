package com.otto.cellescanner;

import net.minecraft.client.gui.Gui;

/**
 * Shared look-and-feel for the addon GUIs: flat dark rounded panels and a green
 * accent, in the spirit of SkyHanni / NotEnoughUpdates rather than vanilla's
 * stone buttons. Kept deliberately small - a couple of colors and a rounded
 * rectangle helper that everything draws through.
 */
public final class Style {

    public static final int ACCENT = 0xFF4BE08C;        // mint green accent
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

    /**
     * A filled rectangle with 1px "rounded" corners (the corner pixels of the
     * top and bottom rows are pulled in by one), which reads as a soft edge
     * without needing a texture.
     */
    public static void roundedRect(int x1, int y1, int x2, int y2, int color) {
        Gui.drawRect(x1 + 1, y1, x2 - 1, y1 + 1, color);
        Gui.drawRect(x1, y1 + 1, x2, y2 - 1, color);
        Gui.drawRect(x1 + 1, y2 - 1, x2 - 1, y2, color);
    }

    /** A panel: a dark rounded body with a gradient background and glowing borders. */
    public static void panel(int x1, int y1, int x2, int y2) {
        // Outer border
        roundedRect(x1, y1, x2, y2, 0xFF18181F);
        // Inner glowing border
        roundedRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0x334BE08C); // 20% alpha accent glow
        
        // Gradient fill
        for (int y = y1 + 2; y < y2 - 2; y++) {
            float ratio = (float)(y - y1) / (y2 - y1);
            int r = (int)(0x1C * (1 - ratio) + 0x0E * ratio);
            int g = (int)(0x1C * (1 - ratio) + 0x0E * ratio);
            int b = (int)(0x24 * (1 - ratio) + 0x12 * ratio);
            int color = 0xE6000000 | (r << 16) | (g << 8) | b;
            Gui.drawRect(x1 + 2, y, x2 - 2, y + 1, color);
        }
    }

    /**
     * A centered card the screen content sits inside, sized generously but
     * clamped to the screen so it never runs off the edges. Every addon screen
     * draws this right after the dimmed background for a consistent look.
     */
    public static void card(int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = screenH / 2;
        int halfW = Math.min(cx - 8, 170);
        int halfH = Math.min(cy - 8, 150);
        panel(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
    }
}
