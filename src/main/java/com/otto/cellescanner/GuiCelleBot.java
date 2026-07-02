package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Discord-bot connection screen. Posts this client's local scan results
 * into a Discord webhook pointed at the companion CelleScannerBot's
 * dedicated "reports" channel (see that project's README) - a single url
 * field, entered here rather than through chat since a webhook url is
 * exactly the kind of string that gets silently truncated by Minecraft's
 * vanilla chat input length cap (see the webhook-URL saga this screen was
 * originally built for).
 */
public class GuiCelleBot extends GuiScreen {

    private static final int ID_PASTE_URL = 0;
    private static final int ID_SAVE = 2;
    private static final int ID_TOGGLE = 3;
    private static final int ID_TEST = 4;
    private static final int ID_CLEAR = 5;
    private static final int ID_BACK = 6;

    private static final int FIELD_W = 260;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int SMALL_BTN_W = 70;
    private static final int ROW_GAP = 6;

    private GuiTextField urlField;
    private GuiButton toggleButton;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_W / 2;
        int y = this.height / 2 - 98;

        CelleConfig cfg = CelleScannerMod.config;

        urlField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W - SMALL_BTN_W - 4, FIELD_H);
        urlField.setMaxStringLength(512);
        urlField.setText(cfg.reportsWebhookUrl == null ? "" : cfg.reportsWebhookUrl);
        urlField.setFocused(true);
        this.buttonList.add(new StyledButton(ID_PASTE_URL, fieldX + FIELD_W - SMALL_BTN_W, y - 1, SMALL_BTN_W, FIELD_H + 2, "Indsæt"));
        y += FIELD_H + ROW_GAP + 4;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_SAVE, fieldX, y, FIELD_W, BTN_H, "Gem"));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, fieldX, y, halfW, BTN_H, toggleLabel()));
        this.buttonList.add(new StyledButton(ID_TEST, fieldX + halfW + 4, y, halfW, BTN_H, "Test forbindelse"));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(new StyledButton(ID_CLEAR, fieldX, y, halfW, BTN_H, "Ryd"));
        this.buttonList.add(new StyledButton(ID_BACK, fieldX + halfW + 4, y, halfW, BTN_H, "Tilbage"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String toggleLabel() {
        return "Rapportering: " + (CelleScannerMod.config.botReportEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_PASTE_URL:
                pasteInto(urlField);
                break;
            case ID_SAVE:
                save();
                break;
            case ID_TOGGLE:
                CelleActions.toggleBotReport();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_TEST:
                CelleActions.testBotConnection();
                statusLine = "Sender test-rapport - tjek chatten for resultatet.";
                statusColor = 0xAAAAAA;
                break;
            case ID_CLEAR:
                urlField.setText("");
                CelleActions.clearBotReport();
                toggleButton.displayString = toggleLabel();
                statusLine = "Indstillinger ryddet.";
                statusColor = 0xAAAAAA;
                break;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiCelleMenu());
                break;
            default:
                break;
        }
    }

    private void pasteInto(GuiTextField field) {
        String clip = getClipboardString();
        if (clip != null) {
            field.setText(clip.trim());
        }
    }

    private void save() {
        String url = urlField.getText() == null ? "" : urlField.getText().trim();
        if (url.isEmpty()) {
            statusLine = "Indtast webhook-url'en først.";
            statusColor = 0xFF5555;
            return;
        }
        if (!url.toLowerCase().startsWith("http")) {
            statusLine = "Det ligner ikke en gyldig url.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.setReportsWebhookUrl(url);
        toggleButton.displayString = toggleLabel();
        statusLine = "Gemt og aktiveret.";
        statusColor = 0x55FF55;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            save();
            return;
        }
        urlField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        urlField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        urlField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 98 - 28;
        drawCenteredString(this.fontRendererObj, "Celle Scanner - Discord Bot", this.width / 2, titleY, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "Reports-webhook url fra din CelleScannerBot instans:", this.width / 2, titleY + 12, 0xAAAAAA);

        urlField.drawTextBox();

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 84, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
