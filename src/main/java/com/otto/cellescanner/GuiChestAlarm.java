package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Control screen for the Chest Alarm addon: enable/disable it, toggle the
 * on-screen notification and the sound, edit the chat keyword it watches for,
 * and fire a test alarm. Opened from the hub (GuiAddonsHub).
 */
public class GuiChestAlarm extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_TOAST = 1;
    private static final int ID_SOUND = 2;
    private static final int ID_SAVE_KEYWORD = 3;
    private static final int ID_TEST = 4;
    private static final int ID_BACK = 5;

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int ROW_GAP = 6;

    private GuiButton toggleButton;
    private GuiButton toastButton;
    private GuiButton soundButton;
    private GuiTextField keywordField;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int left = centerX - FIELD_W / 2;
        int y = this.height / 2 - 80;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, FIELD_W, BTN_H, toggleLabel()));
        y += BTN_H + ROW_GAP;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(toastButton = new StyledButton(ID_TOAST, left, y, halfW, BTN_H, toastLabel()));
        this.buttonList.add(soundButton = new StyledButton(ID_SOUND, left + halfW + 4, y, halfW, BTN_H, soundLabel()));
        y += BTN_H + ROW_GAP + 12;

        keywordField = new GuiTextField(0, this.fontRendererObj, left, y, FIELD_W - 64, FIELD_H);
        keywordField.setMaxStringLength(48);
        keywordField.setText(CelleScannerMod.config.chestAlarmKeyword);
        this.buttonList.add(new StyledButton(ID_SAVE_KEYWORD, left + FIELD_W - 60, y - 1, 60, FIELD_H + 2, "Gem ord"));
        y += FIELD_H + ROW_GAP + 10;

        this.buttonList.add(new StyledButton(ID_TEST, left, y, halfW, BTN_H, "Test"));
        this.buttonList.add(new StyledButton(ID_BACK, left + halfW + 4, y, halfW, BTN_H, "Tilbage"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String toggleLabel() {
        return "Chest Alarm: " + (CelleScannerMod.config.chestAlarmEnabled ? "Til" : "Fra");
    }

    private String toastLabel() {
        return "Notifikation: " + (CelleScannerMod.config.chestAlarmToast ? "Til" : "Fra");
    }

    private String soundLabel() {
        return "Lyd: " + (CelleScannerMod.config.chestAlarmSound ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleChestAlarm();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_TOAST:
                CelleActions.toggleChestAlarmToast();
                toastButton.displayString = toastLabel();
                break;
            case ID_SOUND:
                CelleActions.toggleChestAlarmSound();
                soundButton.displayString = soundLabel();
                break;
            case ID_SAVE_KEYWORD:
                saveKeyword();
                break;
            case ID_TEST:
                CelleActions.testChestAlarm();
                statusLine = "Test-alarm sendt.";
                statusColor = 0x55FF55;
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    private void saveKeyword() {
        String kw = keywordField.getText() == null ? "" : keywordField.getText().trim();
        if (kw.isEmpty()) {
            statusLine = "Indtast et nøgleord først.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.setChestAlarmKeyword(kw);
        statusLine = "Nøgleord gemt.";
        statusColor = 0x55FF55;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveKeyword();
            return;
        }
        keywordField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        keywordField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        keywordField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 80 - 28;
        drawCenteredString(this.fontRendererObj, "Chest Alarm", this.width / 2, titleY, 0xFF5555);
        drawCenteredString(this.fontRendererObj, "Notifikation + lyd når en chest bliver åbnet i chatten.", this.width / 2, titleY + 12, 0xAAAAAA);

        int left = this.width / 2 - FIELD_W / 2;
        drawString(this.fontRendererObj, "Nøgleord der udløser alarmen:", left, this.height / 2 - 28, 0xAAAAAA);

        keywordField.drawTextBox();

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 60, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
