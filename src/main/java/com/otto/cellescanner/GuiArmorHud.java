package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the Armor HUD addon: toggle it and set the warn threshold.
 * Opened from the hub.
 */
public class GuiArmorHud extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_WARN_DOWN = 1;
    private static final int ID_WARN_UP = 2;
    private static final int ID_BACK = 3;
    private static final int ID_WARN_LABEL = -1000;

    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton toggleButton;
    private GuiButton warnLabel;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 24;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;

        this.buttonList.add(new StyledButton(ID_WARN_DOWN, left, y, 20, BTN_H, "-"));
        this.buttonList.add(warnLabel = new StyledButton(ID_WARN_LABEL, left + 22, y, PANEL_W - 44, BTN_H, warnLabel()));
        warnLabel.enabled = false;
        this.buttonList.add(new StyledButton(ID_WARN_UP, left + PANEL_W - 20, y, 20, BTN_H, "+"));
        y += BTN_H + 6;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Rustnings-HUD: " + (CelleScannerMod.config.armorHudEnabled ? "Til" : "Fra");
    }

    private String warnLabel() {
        return "Advarsel under: " + CelleScannerMod.config.armorHudWarnPercent + "%";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleArmorHud();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_WARN_DOWN:
                CelleActions.adjustArmorHudWarn(-5);
                warnLabel.displayString = warnLabel();
                break;
            case ID_WARN_UP:
                CelleActions.adjustArmorHudWarn(5);
                warnLabel.displayString = warnLabel();
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
        int titleY = this.height / 2 - 64;
        drawCenteredString(this.fontRendererObj, "Rustnings-HUD", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Viser din rustnings holdbarhed på skærmen.", cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Tallet bliver rødt når en del er lav.", cx, titleY + 24, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
