package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;

/**
 * Apple iOS-Style Animated Toggle Switch Component.
 * Features 140ms smooth knob sliding physics, theme color blending,
 * and micro hover scaling.
 */
public class AnimatedToggle extends GuiButton {

    private boolean state;
    private final AnimationValue knobAnim;
    private final AnimationValue hoverAnim;

    public AnimatedToggle(int id, int x, int y, int width, int height, String label, boolean initialState) {
        super(id, x, y, width, height, label);
        this.state = initialState;
        this.knobAnim = new AnimationValue(initialState ? 1.0f : 0.0f);
        this.hoverAnim = new AnimationValue(0.0f);
    }

    public boolean getState() {
        return state;
    }

    public void setState(boolean newState) {
        if (this.state != newState) {
            this.state = newState;
            knobAnim.animateTo(newState ? 1.0f : 0.0f, 140);
        }
    }

    public void toggleState() {
        setState(!state);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition
                && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

        if (this.enabled && this.hovered) {
            hoverAnim.animateTo(1.0f, 120);
        } else {
            hoverAnim.animateTo(0.0f, 160);
        }

        float kProgress = knobAnim.getValue();
        float hFade = hoverAnim.getValue();

        int switchW = 28;
        int switchH = 14;
        int switchX = this.xPosition + this.width - switchW;
        int switchY = this.yPosition + (this.height - switchH) / 2;

        // Blend track background: dark gray -> theme accent color
        int accent = Style.getAccentColor();
        int accR = (accent >> 16) & 0xFF;
        int accG = (accent >> 8) & 0xFF;
        int accB = accent & 0xFF;

        int rBg = (int) (0x22 + (accR - 0x22) * kProgress);
        int gBg = (int) (0x22 + (accG - 0x22) * kProgress);
        int bBg = (int) (0x2A + (accB - 0x2A) * kProgress);
        int trackColor = 0xFF000000 | (rBg << 16) | (gBg << 8) | bBg;

        // Draw track
        Style.roundedRect(switchX, switchY, switchX + switchW, switchY + switchH, trackColor);

        // Calculate smooth knob sliding X position
        int knobMinX = switchX + 2;
        int knobMaxX = switchX + switchW - 12;
        int knobX = (int) (knobMinX + (knobMaxX - knobMinX) * kProgress);
        int knobY = switchY + 2;

        // Knob color & micro hover glow
        int knobColor = hovered ? 0xFFFFFFFF : 0xFFE0E0E6;
        Style.roundedRect(knobX, knobY, knobX + 10, knobY + 10, knobColor);

        // Label string on left side
        int textY = this.yPosition + (this.height - 8) / 2;
        fr.drawString(this.displayString, this.xPosition, textY, hovered ? 0xFFFFFFFF : 0xFFDDDDDD, false);
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        boolean pressed = super.mousePressed(mc, mouseX, mouseY);
        if (pressed) {
            toggleState();
            ClickParticleEngine.INSTANCE.spawnBurst(mouseX, mouseY, Style.getAccentColor());
        }
        return pressed;
    }
}
