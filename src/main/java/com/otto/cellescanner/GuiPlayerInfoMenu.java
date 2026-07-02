package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Hub tile for the Player Info addon: explains the shift + right-click gesture
 * and lets you turn the feature on/off. The actual menu is GuiPlayerInfo.
 */
public class GuiPlayerInfoMenu extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 6;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Spiller Info: " + (CelleScannerMod.config.playerInfoEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.togglePlayerInfo();
                toggleButton.displayString = toggleLabel();
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
        int titleY = this.height / 2 - 60;
        drawCenteredString(this.fontRendererObj, "Spiller Info", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Shift + højreklik en spiller for at se", cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "deres rustning og fortryllelser.", cx, titleY + 24, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
