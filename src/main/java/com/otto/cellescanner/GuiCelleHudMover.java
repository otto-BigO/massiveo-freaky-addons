package com.otto.cellescanner;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * Lightweight screen opened with "/celler move" that lets the player
 * click-and-drag the HUD to a new position. Position is saved to the
 * config file when this screen is closed.
 */
public class GuiCelleHudMover extends GuiScreen {

    private boolean dragging = false;
    private int offsetX;
    private int offsetY;

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0 && isInsideHud(mouseX, mouseY)) {
            dragging = true;
            offsetX = mouseX - CelleScannerMod.config.hudX;
            offsetY = mouseY - CelleScannerMod.config.hudY;
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
            CelleScannerMod.config.hudX = mouseX - offsetX;
            CelleScannerMod.config.hudY = mouseY - offsetY;
            clampToScreen();
        }
    }

    /**
     * Keep the whole HUD box on screen so it can never be dragged fully out of
     * reach (which previously left no way back except hand-editing the config).
     * The box is drawn from (hudX - 4, hudY - 4); CelleHud publishes its real
     * rendered size, so derive the box width/height from that.
     */
    private void clampToScreen() {
        int boxW = CelleHud.lastBoxRight - CelleHud.lastBoxLeft;
        int boxH = CelleHud.lastBoxBottom - CelleHud.lastBoxTop;
        int maxX = this.width - boxW;
        int maxY = this.height - boxH;
        // hudX/hudY are the text origin; the box starts 4px up and left of it.
        CelleScannerMod.config.hudX = Math.max(4, Math.min(maxX + 4, CelleScannerMod.config.hudX));
        CelleScannerMod.config.hudY = Math.max(4, Math.min(maxY + 4, CelleScannerMod.config.hudY));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRendererObj,
                "Træk HUD'et med musen. Luk denne skærm (Esc) for at gemme placeringen.",
                this.width / 2, 10, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean isInsideHud(int mouseX, int mouseY) {
        // Grab anywhere inside the box the HUD actually drew last frame, rather
        // than a fixed 110x100 guess that no longer matches the now
        // content-sized box (too small with long owner names / many entries,
        // too big when nearly empty).
        return mouseX >= CelleHud.lastBoxLeft && mouseX <= CelleHud.lastBoxRight
                && mouseY >= CelleHud.lastBoxTop && mouseY <= CelleHud.lastBoxBottom;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        clampToScreen();
        CelleScannerMod.config.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
