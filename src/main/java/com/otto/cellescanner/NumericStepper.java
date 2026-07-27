package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * Reusable Numeric Stepper UI component (- [Label] +).
 * Handles drawing minus/plus buttons and rendering centered text labels
 * without relying on disabled button anti-patterns.
 */
public class NumericStepper {

    private final int idDown;
    private final int idUp;
    private int x;
    private int y;
    private int width;
    private int height;

    private StyledButton btnDown;
    private StyledButton btnUp;

    public NumericStepper(int idDown, int idUp, int x, int y, int width, int height) {
        this.idDown = idDown;
        this.idUp = idUp;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        int btnW = 22;
        this.btnDown = new StyledButton(idDown, x, y, btnW, height, "-");
        this.btnUp = new StyledButton(idUp, x + width - btnW, y, btnW, height, "+");
    }

    public StyledButton getBtnDown() {
        return btnDown;
    }

    public StyledButton getBtnUp() {
        return btnUp;
    }

    public void updatePosition(int x, int y) {
        this.x = x;
        this.y = y;
        int btnW = 22;
        this.btnDown.xPosition = x;
        this.btnDown.yPosition = y;
        this.btnUp.xPosition = x + width - btnW;
        this.btnUp.yPosition = y;
    }

    public void draw(Minecraft mc, int mouseX, int mouseY, String labelText) {
        btnDown.drawButton(mc, mouseX, mouseY);
        btnUp.drawButton(mc, mouseX, mouseY);

        FontRenderer fr = mc.fontRendererObj;
        int labelWidth = width - 48;
        int textX = x + 24 + (labelWidth - fr.getStringWidth(labelText)) / 2;
        int textY = y + (height - 8) / 2;
        fr.drawStringWithShadow(labelText, textX, textY, 0xFFFFFF);
    }
}
