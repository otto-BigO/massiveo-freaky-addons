package com.otto.cellescanner;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Lightweight screen opened with "/celler move" that lets the player
 * click-and-drag the HUD to a new position with visual grid snapping guides.
 */
public class GuiCelleHudMover extends GuiScreen {

    private final ScreenIntro screenIntro = new ScreenIntro();

    private boolean dragging = false;
    private int offsetX;
    private int offsetY;
    private boolean snappedX = false;
    private boolean snappedY = false;

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0 && isInsideHud(mouseX, mouseY)) {
            dragging = true;
            offsetX = mouseX - MassiveOsFreakyAddons.config.hudX;
            offsetY = mouseY - MassiveOsFreakyAddons.config.hudY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (dragging && this.mc != null) {
            int mouseX = Mouse.getX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
            int newX = mouseX - offsetX;
            int newY = mouseY - offsetY;

            // Snap-to-Grid Math
            snappedX = false;
            snappedY = false;

            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int boxW = CelleHud.lastBoxRight - CelleHud.lastBoxLeft;
            int boxH = CelleHud.lastBoxBottom - CelleHud.lastBoxTop;

            // Snap to Center-X
            if (Math.abs((newX + boxW / 2) - centerX) < 10) {
                newX = centerX - boxW / 2;
                snappedX = true;
            } else if (Math.abs(newX - 10) < 8) {
                newX = 10;
                snappedX = true;
            }

            // Snap to Center-Y
            if (Math.abs((newY + boxH / 2) - centerY) < 10) {
                newY = centerY - boxH / 2;
                snappedY = true;
            } else if (Math.abs(newY - 10) < 8) {
                newY = 10;
                snappedY = true;
            }

            MassiveOsFreakyAddons.config.hudX = newX;
            MassiveOsFreakyAddons.config.hudY = newY;
            clampToScreen();
        }
    }

    private void clampToScreen() {
        int boxW = CelleHud.lastBoxRight - CelleHud.lastBoxLeft;
        int boxH = CelleHud.lastBoxBottom - CelleHud.lastBoxTop;
        int maxX = this.width - boxW;
        int maxY = this.height - boxH;
        MassiveOsFreakyAddons.config.hudX = Math.max(4, Math.min(maxX + 4, MassiveOsFreakyAddons.config.hudX));
        MassiveOsFreakyAddons.config.hudY = Math.max(4, Math.min(maxY + 4, MassiveOsFreakyAddons.config.hudY));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        screenIntro.backdropOnly(this.width, this.height, mouseX, mouseY);

        // Draw Center Snap Grid Guides when dragging
        if (dragging) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            int accent = Style.getAccentColor();

            // Center vertical & horizontal guides
            drawRect(cx - 1, 0, cx + 1, this.height, snappedX ? accent : 0x44FFFFFF);
            drawRect(0, cy - 1, this.width, cy + 1, snappedY ? accent : 0x44FFFFFF);
        }

        drawCenteredString(this.fontRendererObj,
                "Træk HUD'et med musen. Snap-guide viser midterlinjer. Luk (Esc) for at gemme.",
                this.width / 2, 12, Style.getAccentColor());
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean isInsideHud(int mouseX, int mouseY) {
        return mouseX >= CelleHud.lastBoxLeft && mouseX <= CelleHud.lastBoxRight
                && mouseY >= CelleHud.lastBoxTop && mouseY <= CelleHud.lastBoxBottom;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        clampToScreen();
        MassiveOsFreakyAddons.config.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
