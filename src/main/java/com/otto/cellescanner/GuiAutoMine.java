package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/** Control screen for the Auto Mine addon: on/off, with a use-at-your-discretion note. */
public class GuiAutoMine extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 4;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Auto Mine: " + (CelleScannerMod.config.autoMineEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_TOGGLE) {
            CelleActions.toggleAutoMine();
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
        int titleY = this.height / 2 - 64;
        drawCenteredString(this.fontRendererObj, "Auto Mine", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Miner mine-området og går til depot når tasken er fuld.", cx, titleY + 12, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Automatisering - brug kun hvor serveren tillader det.", cx, titleY + 24, 0xFF8888);
        drawCenteredString(this.fontRendererObj, "Bind en tast til 'Auto Mine' i Controls for hurtig til/fra.", cx, this.height / 2 + 44, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
