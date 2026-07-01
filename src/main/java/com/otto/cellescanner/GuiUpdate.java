package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the auto-updater: shows the running and latest version,
 * lets you toggle auto-update, and check for a new version on demand.
 */
public class GuiUpdate extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_CHECK = 1;
    private static final int ID_BACK = 2;

    private static final int BTN_H = 20;
    private static final int PANEL_W = 220;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 6;
        this.buttonList.add(toggleButton = new GuiButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new GuiButton(ID_CHECK, left, y, PANEL_W, BTN_H, "Tjek for opdatering nu"));
        y += BTN_H + 6;
        this.buttonList.add(new GuiButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Auto-opdatering: " + (CelleScannerMod.config.autoUpdateEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleAutoUpdate();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_CHECK:
                CelleActions.checkForUpdateNow();
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

        int cx = this.width / 2;
        int titleY = this.height / 2 - 76;
        drawCenteredString(this.fontRendererObj, "Opdatering", cx, titleY, 0x55FFFF);

        String latest = AutoUpdater.getLatestVersion();
        drawCenteredString(this.fontRendererObj, "Nuværende version: " + CelleScannerMod.VERSION, cx, titleY + 16, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Nyeste version: " + (latest != null ? latest : "?"), cx, titleY + 28, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Status: " + AutoUpdater.getStatus(), cx, titleY + 40, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
