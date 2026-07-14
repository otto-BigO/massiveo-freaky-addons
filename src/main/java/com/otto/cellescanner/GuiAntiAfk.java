package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the Anti-AFK addon - enable/disable it, pick which tiny
 * actions it uses, and set how often. Opened from the hub (GuiAddonsHub).
 */
public class GuiAntiAfk extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_SWING = 1;
    private static final int ID_ROTATE = 2;
    private static final int ID_JUMP = 3;
    private static final int ID_INT_DOWN = 4;
    private static final int ID_INT_UP = 5;
    private static final int ID_BACK = 6;
    private static final int ID_STRAFE = 7;
    private static final int ID_INT_LABEL = -1000;

    private static final int ROW_H = 24;
    private static final int ROW_COUNT = 7;
    private static final int CONTENT_H = ROW_H * ROW_COUNT;
    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton toggleButton;
    private GuiButton swingButton;
    private GuiButton rotateButton;
    private GuiButton jumpButton;
    private GuiButton strafeButton;
    private GuiButton intervalLabel;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int left = centerX - PANEL_W / 2;
        int y = this.height / 2 - CONTENT_H / 2;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += ROW_H;
        this.buttonList.add(swingButton = new StyledButton(ID_SWING, left, y, PANEL_W, BTN_H, swingLabel()));
        y += ROW_H;
        this.buttonList.add(rotateButton = new StyledButton(ID_ROTATE, left, y, PANEL_W, BTN_H, rotateLabel()));
        y += ROW_H;
        this.buttonList.add(jumpButton = new StyledButton(ID_JUMP, left, y, PANEL_W, BTN_H, jumpLabel()));
        y += ROW_H;
        this.buttonList.add(strafeButton = new StyledButton(ID_STRAFE, left, y, PANEL_W, BTN_H, strafeLabel()));
        y += ROW_H;

        // interval stepper: - [label] +
        this.buttonList.add(new StyledButton(ID_INT_DOWN, left, y, 20, BTN_H, "-"));
        this.buttonList.add(intervalLabel = new StyledButton(ID_INT_LABEL, left + 22, y, PANEL_W - 44, BTN_H, intervalLabel()));
        intervalLabel.enabled = false;
        this.buttonList.add(new StyledButton(ID_INT_UP, left + PANEL_W - 20, y, 20, BTN_H, "+"));
        y += ROW_H;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Anti-AFK: " + (CelleScannerMod.config.antiAfkEnabled ? "Til" : "Fra");
    }

    private String swingLabel() {
        return "Slag med hånd: " + (CelleScannerMod.config.antiAfkSwing ? "Til" : "Fra");
    }

    private String rotateLabel() {
        return "Kig frem/tilbage: " + (CelleScannerMod.config.antiAfkRotate ? "Til" : "Fra");
    }

    private String jumpLabel() {
        return "Hop: " + (CelleScannerMod.config.antiAfkJump ? "Til" : "Fra");
    }

    private String strafeLabel() {
        return "Skridt til siden: " + (CelleScannerMod.config.antiAfkStrafe ? "Til" : "Fra");
    }

    private String intervalLabel() {
        return "Interval: " + CelleScannerMod.config.antiAfkIntervalSeconds + "s";
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleAntiAfk();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_SWING:
                CelleActions.toggleAntiAfkSwing();
                swingButton.displayString = swingLabel();
                break;
            case ID_ROTATE:
                CelleActions.toggleAntiAfkRotate();
                rotateButton.displayString = rotateLabel();
                break;
            case ID_JUMP:
                CelleActions.toggleAntiAfkJump();
                jumpButton.displayString = jumpLabel();
                break;
            case ID_STRAFE:
                CelleActions.toggleAntiAfkStrafe();
                strafeButton.displayString = strafeLabel();
                break;
            case ID_INT_DOWN:
                CelleActions.adjustAntiAfkInterval(-5);
                intervalLabel.displayString = intervalLabel();
                break;
            case ID_INT_UP:
                CelleActions.adjustAntiAfkInterval(5);
                intervalLabel.displayString = intervalLabel();
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

        int titleY = this.height / 2 - CONTENT_H / 2 - 24;
        drawCenteredString(this.fontRendererObj, "Anti-AFK", this.width / 2, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Holder dig aktiv - brug på eget ansvar.", this.width / 2, titleY + 12, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
