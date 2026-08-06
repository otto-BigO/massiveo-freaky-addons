package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

/**
 * Apple-Style UI Animated Button for Minecraft 1.8.9 Forge.
 * Replicates Apple motion physics: smooth 120ms cubic hover interpolation,
 * micro scale-press physics (0.97x -> 1.00x -> 1.02x), and 4-sided crisp glowing theme borders.
 */
public class StyledButton extends GuiButton {

    private static final long PRESS_MS = 180L;

    private final AnimationValue hoverAnim = new AnimationValue(0f);
    private final AnimationValue scaleAnim = new AnimationValue(1.0f);
    /**
     * While a press is playing, the hover target is not applied. Without this the
     * next frame overwrote the press compression with the hover scale, so the
     * click never actually looked like it depressed.
     */
    private long pressUntilMs = 0L;

    /**
     * Overall opacity, so a whole group of buttons can fade in or out together.
     * Everything the button draws is multiplied by this.
     */
    private float alpha = 1.0f;

    public void setAlpha(float a) {
        this.alpha = a < 0f ? 0f : (a > 1f ? 1f : a);
    }

    public StyledButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible || alpha <= 0.01f) {
            return;
        }
        int alphaBits = ((int) (alpha * 255.0f) & 0xFF) << 24;
        FontRenderer fr = mc.fontRendererObj;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        boolean pressing = System.currentTimeMillis() < pressUntilMs;
        if (this.enabled && this.hovered) {
            hoverAnim.animateTo(1.0f, 120);
            if (!pressing) {
                scaleAnim.animateTo(1.02f, 120);
            }
        } else {
            hoverAnim.animateTo(0.0f, 160);
            if (!pressing) {
                scaleAnim.animateTo(1.00f, 160);
            }
        }

        float fade = hoverAnim.getValue();
        float scale = scaleAnim.getValue();

        int x1 = this.xPosition;
        int y1 = this.yPosition;
        int x2 = this.xPosition + this.width;
        int y2 = this.yPosition + this.height;

        int fill;
        int text;
        int border;

        if (!this.enabled) {
            fill = alphaBits | (Style.BTN_BG_DISABLED & 0x00FFFFFF);
            text = alphaBits | (Style.TEXT_DISABLED & 0x00FFFFFF);
            border = alphaBits | (Style.BTN_BORDER & 0x00FFFFFF);
        } else {
            int rF = (int) (0x26 + (0x36 - 0x26) * fade);
            int gF = (int) (0x26 + (0x36 - 0x26) * fade);
            int bF = (int) (0x2E + (0x42 - 0x2E) * fade);
            fill = alphaBits | (rF << 16) | (gF << 8) | bF;

            int accent = Style.getAccentColor();
            int accR = (accent >> 16) & 0xFF;
            int accG = (accent >> 8) & 0xFF;
            int accB = accent & 0xFF;

            int rB = (int) (0x12 + (accR - 0x12) * fade);
            int gB = (int) (0x12 + (accG - 0x12) * fade);
            int bB = (int) (0x16 + (accB - 0x16) * fade);
            border = alphaBits | (rB << 16) | (gB << 8) | bB;

            int rT = (int) (0xE6 + (0xFF - 0xE6) * fade);
            int gT = (int) (0xE6 + (0xFF - 0xE6) * fade);
            int bT = (int) (0xEA + (0xFF - 0xEA) * fade);
            text = alphaBits | (rT << 16) | (gT << 8) | bT;
        }

        float cx = x1 + this.width / 2.0f;
        float cy = y1 + this.height / 2.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0f);
        GL11.glScalef(scale, scale, 1.0f);
        GL11.glTranslatef(-cx, -cy, 0.0f);

        // Draw 4-sided crisp border outline
        Style.drawOutline(x1, y1, x2, y2, border);

        // Inner fill
        Gui.drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

        int textX = x1 + (this.width - fr.getStringWidth(this.displayString)) / 2;
        int textY = y1 + (this.height - 8) / 2;
        fr.drawString(this.displayString, textX, textY, text, false);

        GL11.glPopMatrix();
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean pressed = super.mousePressed(mc, mouseX, mouseY);
        if (pressed) {
            pressUntilMs = System.currentTimeMillis() + PRESS_MS;
            scaleAnim.setValueInstant(0.95f);
            scaleAnim.animateTo(1.02f, PRESS_MS);
            ClickParticleEngine.INSTANCE.spawnBurst(mouseX, mouseY, Style.getAccentColor());
        }
        return pressed;
    }
}
