package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/** Minimal hub tile for the mod-user badge: just an on/off toggle. */
public class GuiModIcon extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 20;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Mod-ikon: " + (CelleScannerMod.config.modIconEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_TOGGLE) {
            CelleActions.toggleModIcon();
            toggleButton.displayString = toggleLabel();
        } else if (button.id == ID_BACK) {
            CelleActions.openHub();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 60;
        drawCenteredString(this.fontRendererObj, "Mod-brugere", cx, titleY, 0x55FF55);
        drawCenteredString(this.fontRendererObj, "Lilla cirkel foran andre mod-brugeres navn (test).", cx, titleY + 12, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
