package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;

/**
 * A drop-in replacement for the vanilla stone GuiButton that draws a flat dark
 * rounded button with a green accent on hover, matching Style. Constructor
 * signature matches GuiButton so it can be swapped in directly. Disabled
 * buttons render as flat labels (several screens use a disabled button as a
 * value readout in a stepper row).
 */
public class StyledButton extends GuiButton {

    public StyledButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    private float fadeFactor = 0f;
    private long lastTime = -1;

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        long now = System.currentTimeMillis();
        if (lastTime < 0) {
            lastTime = now;
        }
        float dt = (now - lastTime) / 1000f;
        lastTime = now;

        if (this.enabled && this.hovered) {
            fadeFactor += dt * 5.0f; // fade in over 200ms
        } else {
            fadeFactor -= dt * 5.0f; // fade out over 200ms
        }
        if (fadeFactor < 0f) fadeFactor = 0f;
        if (fadeFactor > 1f) fadeFactor = 1f;

        int x1 = this.xPosition;
        int y1 = this.yPosition;
        int x2 = this.xPosition + this.width;
        int y2 = this.yPosition + this.height;

        int fill;
        int text;
        int border;

        if (!this.enabled) {
            fill = Style.BTN_BG_DISABLED;
            text = Style.TEXT_DISABLED;
            border = Style.BTN_BORDER;
        } else {
            // Blend fill: BTN_BG -> BTN_BG_HOVER
            int rF = (int) (0x26 + (0x34 - 0x26) * fadeFactor);
            int gF = (int) (0x26 + (0x34 - 0x26) * fadeFactor);
            int bF = (int) (0x2E + (0x3F - 0x2E) * fadeFactor);
            fill = 0xFF000000 | (rF << 16) | (gF << 8) | bF;

            // Blend border: BTN_BORDER -> ACCENT
            int rB = (int) (0x12 + (0x4B - 0x12) * fadeFactor);
            int gB = (int) (0x12 + (0xE0 - 0x12) * fadeFactor);
            int bB = (int) (0x16 + (0x8C - 0x16) * fadeFactor);
            border = 0xFF000000 | (rB << 16) | (gB << 8) | bB;

            // Blend text: TEXT -> TEXT_HOVER
            int rT = (int) (0xE6 + (0xFF - 0xE6) * fadeFactor);
            int gT = (int) (0xE6 + (0xFF - 0xE6) * fadeFactor);
            int bT = (int) (0xEA + (0xFF - 0xEA) * fadeFactor);
            text = 0xFF000000 | (rT << 16) | (gT << 8) | bT;
        }

        Style.roundedRect(x1, y1, x2, y2, border);
        Style.roundedRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

        drawCenteredString(fr, this.displayString, x1 + this.width / 2, y1 + (this.height - 8) / 2, text);
    }
}
