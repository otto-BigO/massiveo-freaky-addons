package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Main screen for the Auto Mine addon: on/off and a Settings tab. The area
 * picker, clear-area and trash filter live in {@link GuiAutoMineSettings}.
 * Text is stacked ABOVE the buttons with fixed margins so they can't overlap.
 */
public class GuiAutoMine extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;
    private static final int ID_SETTINGS = 2;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    // Text block top; buttons start at TITLE_Y + TEXT_BLOCK_H, so the two
    // regions cannot collide no matter what text is shown.
    private static final int TITLE_Y_OFF = -84;   // relative to height/2
    private static final int TEXT_BLOCK_H = 58;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 + TITLE_Y_OFF + TEXT_BLOCK_H;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(new StyledButton(ID_SETTINGS, left, y, PANEL_W, BTN_H, "Indstillinger"));
        y += BTN_H + GAP;
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
        } else if (button.id == ID_SETTINGS) {
            this.mc.displayGuiScreen(new GuiAutoMineSettings());
        } else if (button.id == ID_BACK) {
            CelleActions.openHub();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 + TITLE_Y_OFF;
        drawCenteredString(this.fontRendererObj, "Auto Mine", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Miner mine-området og går til depot når tasken er fuld.",
                cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Automatisering - brug kun hvor serveren tillader det.",
                cx, titleY + 26, 0xFF8888);

        CelleConfig c = CelleScannerMod.config;
        String area = c.mineAreaSet
                ? "Område: " + c.mineAreaX1 + " " + c.mineAreaY1 + " " + c.mineAreaZ1
                        + "  til  " + c.mineAreaX2 + " " + c.mineAreaY2 + " " + c.mineAreaZ2
                : "Område: standard (indbygget)";
        drawCenteredString(this.fontRendererObj, area, cx, titleY + 42, c.mineAreaSet ? 0x7CFC7C : 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
