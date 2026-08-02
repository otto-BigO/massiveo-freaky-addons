package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Discord-bot connection screen - Apple Motion Physics.
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

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_W / 2;
        int y = this.height / 2 - 68;

        CelleConfig cfg = MassiveOsFreakyAddons.config;

        urlField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W - SMALL_BTN_W - 4, FIELD_H);
        urlField.setMaxStringLength(512);
        urlField.setText(cfg.reportsWebhookUrl == null ? "" : cfg.reportsWebhookUrl);
        urlField.setFocused(true);
        this.buttonList.add(new StyledButton(ID_PASTE_URL, fieldX + FIELD_W - SMALL_BTN_W, y - 1, SMALL_BTN_W, FIELD_H + 2, "Indsæt"));
        y += FIELD_H + ROW_GAP + 4;

        this.buttonList.add(new StyledButton(ID_SAVE, fieldX, y, FIELD_W, BTN_H, "Gem"));
        y += BTN_H + ROW_GAP;

        toggleButton = new StyledButton(ID_TOGGLE, fieldX, y, FIELD_W, BTN_H, toggleLabel());
        this.buttonList.add(toggleButton);
        y += BTN_H + ROW_GAP;

        int thirdW = (FIELD_W - 8) / 3;
        this.buttonList.add(new StyledButton(ID_TEST, fieldX, y, thirdW, BTN_H, "Test"));
        this.buttonList.add(new StyledButton(ID_CLEAR, fieldX + thirdW + 4, y, thirdW, BTN_H, "Ryd"));
        this.buttonList.add(new StyledButton(ID_BACK, fieldX + (thirdW + 4) * 2, y, thirdW, BTN_H, "< Tilbage"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String toggleLabel() {
        boolean on = MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.botReportEnabled;
        return "Rapporter til Bot: " + (on ? Style.getAccentFormatting() + "[ TIL ]" : "\u00a77[ FRA ]");
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
                this.mc.displayGuiScreen(new GuiAddonsHub("Celler"));
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

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 110;
        int accent = Style.getAccentColor();
        drawCenteredString(this.fontRendererObj, "\u00a7lCelle Scanner - Discord Bot", this.width / 2, titleY, accent);
        drawCenteredString(this.fontRendererObj, "Reports-webhook url fra din CelleScannerBot instans:", this.width / 2, titleY + 12, 0xAAAAAA);

        urlField.drawTextBox();

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 64, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
