package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Settings tab for Auto Mine: the mine-area picker, clear-area and the trash
 * filter. Opened from {@link GuiAutoMine}; Tilbage returns there. Same fixed
 * text/button split as the main screen so nothing can overlap.
 */
public class GuiAutoMineSettings extends GuiScreen {

    private static final int ID_SET_AREA = 0;
    private static final int ID_CLEAR_AREA = 1;
    private static final int ID_TRASH = 2;
    private static final int ID_BACK = 3;
    private static final int ID_CRAZY = 4;
    private static final int ID_TUNING = 5;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    private static final int TITLE_Y_OFF = -92;   // relative to height/2
    private static final int TEXT_BLOCK_H = 46;

    private GuiButton clearAreaButton;
    private GuiButton crazyButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 + TITLE_Y_OFF + TEXT_BLOCK_H;
        this.buttonList.add(new StyledButton(ID_SET_AREA, left, y, PANEL_W, BTN_H, "Sæt mine-område"));
        y += BTN_H + GAP;
        this.buttonList.add(clearAreaButton = new StyledButton(ID_CLEAR_AREA, left, y, PANEL_W, BTN_H, clearLabel()));
        clearAreaButton.enabled = CelleScannerMod.config.mineAreaSet;
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_TRASH, left, y, PANEL_W, BTN_H, "Skralde Filter"));
        y += BTN_H + GAP;
        this.buttonList.add(crazyButton = new StyledButton(ID_CRAZY, left, y, PANEL_W, BTN_H, crazyLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_TUNING, left, y, PANEL_W, BTN_H, "Finjustering (afstand, ræk, hakke)"));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String clearLabel() {
        return CelleScannerMod.config.mineAreaSet ? "Ryd mine-område (brug standard)" : "Ryd mine-område";
    }

    private String crazyLabel() {
        return "Crazy mode: " + (CelleScannerMod.config.autoMineCrazy ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_SET_AREA) {
            // Arm set-area mode, then close so the player can right-click the two corners.
            AutoMine.beginSetArea();
            this.mc.displayGuiScreen(null);
        } else if (button.id == ID_CLEAR_AREA) {
            CelleScannerMod.config.mineAreaSet = false;
            CelleScannerMod.config.save();
            this.initGui();
        } else if (button.id == ID_TRASH) {
            this.mc.displayGuiScreen(new GuiAutoMineTrash());
        } else if (button.id == ID_CRAZY) {
            CelleScannerMod.config.autoMineCrazy = !CelleScannerMod.config.autoMineCrazy;
            CelleScannerMod.config.save();
            crazyButton.displayString = crazyLabel();
        } else if (button.id == ID_TUNING) {
            this.mc.displayGuiScreen(new GuiAutoMineTuning());
        } else if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(new GuiAutoMine());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 + TITLE_Y_OFF;
        drawCenteredString(this.fontRendererObj, "Auto Mine - Indstillinger", cx, titleY, 0xFFD24B);

        CelleConfig c = CelleScannerMod.config;
        String area = c.mineAreaSet
                ? "Område: " + c.mineAreaX1 + " " + c.mineAreaY1 + " " + c.mineAreaZ1
                        + "  til  " + c.mineAreaX2 + " " + c.mineAreaY2 + " " + c.mineAreaZ2
                : "Område: standard (indbygget)";
        drawCenteredString(this.fontRendererObj, area, cx, titleY + 18, c.mineAreaSet ? 0x7CFC7C : 0x888888);
        drawCenteredString(this.fontRendererObj, "Sæt område: klik knappen, højreklik så de 2 hjørner i verdenen.",
                cx, titleY + 30, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
