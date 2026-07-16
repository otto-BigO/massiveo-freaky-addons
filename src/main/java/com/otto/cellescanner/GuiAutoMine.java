package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/** Control screen for the Auto Mine addon: on/off, mine-area picker, trash filter. */
public class GuiAutoMine extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int ID_TRASH = 2;
    private static final int ID_SET_AREA = 3;
    private static final int ID_CLEAR_AREA = 4;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    private GuiButton toggleButton;
    private GuiButton clearAreaButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 66;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_SET_AREA, left, y, PANEL_W, BTN_H, "Sæt mine-område"));
        y += BTN_H + GAP;
        this.buttonList.add(clearAreaButton = new StyledButton(ID_CLEAR_AREA, left, y, PANEL_W, BTN_H, clearLabel()));
        clearAreaButton.enabled = CelleScannerMod.config.mineAreaSet;
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_TRASH, left, y, PANEL_W, BTN_H, "Skralde Filter"));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Auto Mine: " + (CelleScannerMod.config.autoMineEnabled ? "Til" : "Fra");
    }

    private String clearLabel() {
        return CelleScannerMod.config.mineAreaSet ? "Ryd mine-område (brug standard)" : "Ryd mine-område";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_TOGGLE) {
            CelleActions.toggleAutoMine();
            toggleButton.displayString = toggleLabel();
        } else if (button.id == ID_SET_AREA) {
            // Arm set-area mode, then close so the player can right-click the two corners.
            AutoMine.beginSetArea();
            this.mc.displayGuiScreen(null);
        } else if (button.id == ID_CLEAR_AREA) {
            CelleScannerMod.config.mineAreaSet = false;
            CelleScannerMod.config.save();
            this.initGui();
        } else if (button.id == ID_TRASH) {
            this.mc.displayGuiScreen(new GuiAutoMineTrash());
        } else if (button.id == ID_BACK) {
            CelleActions.openHub();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 108;
        drawCenteredString(this.fontRendererObj, "Auto Mine", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Miner mine-området og går til depot når tasken er fuld.", cx, titleY + 12, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Automatisering - brug kun hvor serveren tillader det.", cx, titleY + 24, 0xFF8888);

        CelleConfig c = CelleScannerMod.config;
        String area = c.mineAreaSet
                ? "Område: " + c.mineAreaX1 + " " + c.mineAreaY1 + " " + c.mineAreaZ1
                        + "  til  " + c.mineAreaX2 + " " + c.mineAreaY2 + " " + c.mineAreaZ2
                : "Område: standard (indbygget)";
        drawCenteredString(this.fontRendererObj, area, cx, titleY + 40, c.mineAreaSet ? 0x7CFC7C : 0x888888);
        drawCenteredString(this.fontRendererObj, "Sæt område: klik knappen, højreklik så de 2 hjørner i verdenen.",
                cx, this.height / 2 + 62, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
