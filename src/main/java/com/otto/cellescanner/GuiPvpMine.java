package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/** Control screen for the PvP Mine watcher: on/off and the entry alert. */
public class GuiPvpMine extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_ALERT = 1;
    private static final int ID_BACK = 2;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;
    private GuiButton alertButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 12;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(alertButton = new StyledButton(ID_ALERT, left, y, PANEL_W, BTN_H, alertLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "PvP Mine: " + (CelleScannerMod.config.pvpMineEnabled ? "Til" : "Fra");
    }

    private String alertLabel() {
        return "Alarm når nogen er i minen: " + (CelleScannerMod.config.pvpMineAlert ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.togglePvpMine();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_ALERT:
                CelleActions.togglePvpMineAlert();
                alertButton.displayString = alertLabel();
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 68;
        drawCenteredString(this.fontRendererObj, "PvP Mine", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Drop-timer (skilt) + alarm når en spiller er i minen.", cx, titleY + 12, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "HUD vises nederst til venstre.", cx, titleY + 22, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
