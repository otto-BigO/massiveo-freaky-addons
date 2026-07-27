package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Clean, modern control panel for the Celle Scanner addon.
 * Opened from the Massiveo's Freaky Addons hub (B) or by typing /celler.
 * Displays high-level toggles, timer controls, and special cell tools cleanly.
 */
public class GuiCelleMenu extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_NOTIFY = 1;
    private static final int ID_ESP = 2;

    private static final int ID_MIN_DOWN = 3;
    private static final int ID_MIN_UP = 4;
    private static final int ID_MAX_DOWN = 5;
    private static final int ID_MAX_UP = 6;

    private static final int ID_RELOAD = 7;
    private static final int ID_CLEAR = 8;
    private static final int ID_SPECIAL = 9;
    private static final int ID_SETTINGS = 10;
    private static final int ID_CLOSE = 11;

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleBtn;
    private GuiButton notifyBtn;
    private GuiButton espBtn;
    private GuiButton minLabelBtn;
    private GuiButton maxLabelBtn;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int halfW = (PANEL_W - 4) / 2;

        int y = cy - 105;

        // Card 1: Main Toggles
        this.buttonList.add(toggleBtn = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += 24;
        this.buttonList.add(notifyBtn = new StyledButton(ID_NOTIFY, left, y, halfW, BTN_H, notifyLabel()));
        this.buttonList.add(espBtn = new StyledButton(ID_ESP, left + halfW + 4, y, halfW, BTN_H, espLabel()));
        y += 32;

        // Card 2: Timer Range Steppers
        this.buttonList.add(new StyledButton(ID_MIN_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(minLabelBtn = new StyledButton(ID_MIN_DOWN - 100, left + 24, y, PANEL_W - 48, BTN_H, minLabel()));
        minLabelBtn.enabled = false;
        this.buttonList.add(new StyledButton(ID_MIN_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += 24;

        this.buttonList.add(new StyledButton(ID_MAX_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(maxLabelBtn = new StyledButton(ID_MAX_DOWN - 100, left + 24, y, PANEL_W - 48, BTN_H, maxLabel()));
        maxLabelBtn.enabled = false;
        this.buttonList.add(new StyledButton(ID_MAX_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += 32;

        // Card 3: Action Tools & Settings
        this.buttonList.add(new StyledButton(ID_SPECIAL, left, y, halfW, BTN_H, "Special Celler"));
        this.buttonList.add(new StyledButton(ID_SETTINGS, left + halfW + 4, y, halfW, BTN_H, "Indstillinger"));
        y += 24;

        this.buttonList.add(new StyledButton(ID_RELOAD, left, y, halfW, BTN_H, "Genindlæs"));
        this.buttonList.add(new StyledButton(ID_CLEAR, left + halfW + 4, y, halfW, BTN_H, "Ryd Cache"));
        y += 28;

        this.buttonList.add(new StyledButton(ID_CLOSE, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private String toggleLabel() {
        boolean active = CelleScannerMod.config != null && CelleScannerMod.config.enabled;
        return "Scanner Status: " + (active ? EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String notifyLabel() {
        boolean active = CelleScannerMod.config != null && CelleScannerMod.config.notify;
        return "Alarmer: " + (active ? EnumChatFormatting.GREEN + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String espLabel() {
        boolean active = CelleScannerMod.config != null && CelleScannerMod.config.espEnabled;
        return "3D ESP: " + (active ? EnumChatFormatting.GREEN + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String minLabel() {
        return "Min Timer: " + (CelleScannerMod.config != null ? CelleScannerMod.config.minHours : 0) + "t";
    }

    private String maxLabel() {
        return "Max Timer: " + (CelleScannerMod.config != null ? CelleScannerMod.config.maxHours : 0) + "t";
    }

    private static int step() {
        return isShiftKeyDown() ? 5 : 1;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleEnabled();
                toggleBtn.displayString = toggleLabel();
                break;
            case ID_NOTIFY:
                CelleActions.toggleNotify();
                notifyBtn.displayString = notifyLabel();
                break;
            case ID_ESP:
                CelleActions.toggleEsp();
                espBtn.displayString = espLabel();
                break;
            case ID_MIN_DOWN:
                CelleActions.adjustMinHours(-step());
                minLabelBtn.displayString = minLabel();
                break;
            case ID_MIN_UP:
                CelleActions.adjustMinHours(step());
                minLabelBtn.displayString = minLabel();
                break;
            case ID_MAX_DOWN:
                CelleActions.adjustMaxHours(-step());
                maxLabelBtn.displayString = maxLabel();
                break;
            case ID_MAX_UP:
                CelleActions.adjustMaxHours(step());
                maxLabelBtn.displayString = maxLabel();
                break;
            case ID_SPECIAL:
                CelleActions.openSpecialScreen();
                break;
            case ID_SETTINGS:
                CelleActions.openSettings();
                break;
            case ID_RELOAD:
                CelleActions.reloadConfig();
                this.initGui();
                break;
            case ID_CLEAR:
                CelleActions.clearCache();
                break;
            case ID_CLOSE:
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
        int cy = this.height / 2;

        int titleY = cy - 134;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Celle Scanner", cx, titleY, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Hold Shift for +/- 5 timer", cx, titleY + 12, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
