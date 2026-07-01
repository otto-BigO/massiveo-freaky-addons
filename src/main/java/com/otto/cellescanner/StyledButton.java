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

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        int x1 = this.xPosition;
        int y1 = this.yPosition;
        int x2 = this.xPosition + this.width;
        int y2 = this.yPosition + this.height;

        int fill;
        int text;
        int border = Style.BTN_BORDER;
        if (!this.enabled) {
            fill = Style.BTN_BG_DISABLED;
            text = Style.TEXT_DISABLED;
        } else if (this.hovered) {
            fill = Style.BTN_BG_HOVER;
            text = Style.TEXT_HOVER;
            border = Style.ACCENT;
        } else {
            fill = Style.BTN_BG;
            text = Style.TEXT;
        }

        Style.roundedRect(x1, y1, x2, y2, border);
        Style.roundedRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

        drawCenteredString(fr, this.displayString, x1 + this.width / 2, y1 + (this.height - 8) / 2, text);
    }
}
