package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Special-celle list management - Apple Motion Physics.
 */
public class GuiCelleSpecial extends GuiScreen {

    private static final int ID_ADD = 0;
    private static final int ID_CLEAR = 1;
    private static final int ID_BACK = 2;
    private static final int REMOVE_BASE = 100;
    private static final int MAX_REMOVE_ROWS = 4;

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int REMOVE_H = 18;
    private static final int ROW_GAP = 6;

    private GuiTextField idField;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    private final List<String> shownIds = new ArrayList<String>();
    private int listHintY;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.shownIds.clear();

        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_W / 2;
        int y = this.height / 2 - 100;

        String carry = idField != null && idField.getText() != null ? idField.getText() : "";
        idField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W, FIELD_H);
        idField.setMaxStringLength(64);
        idField.setText(carry);
        idField.setFocused(true);

        y += FIELD_H + ROW_GAP + 4;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_ADD, fieldX, y, halfW, BTN_H, "Tilføj"));
        this.buttonList.add(new StyledButton(ID_CLEAR, fieldX + halfW + 4, y, halfW, BTN_H, "Ryd alle"));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(new StyledButton(ID_BACK, fieldX, y, FIELD_W, BTN_H, "< Tilbage"));
        y += BTN_H + ROW_GAP + 4;

        listHintY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 4;

        List<String> ids = MassiveOsFreakyAddons.config.specialCelleIds;
        int shown = Math.min(ids.size(), MAX_REMOVE_ROWS);
        for (int i = 0; i < shown; i++) {
            String id = ids.get(i);
            shownIds.add(id);
            this.buttonList.add(new StyledButton(REMOVE_BASE + i, fieldX, y, FIELD_W, REMOVE_H, "Fjern  " + id));
            y += REMOVE_H + 2;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= REMOVE_BASE) {
            int index = button.id - REMOVE_BASE;
            if (index >= 0 && index < shownIds.size()) {
                String id = shownIds.get(index);
                CelleActions.removeSpecialCelle(id);
                statusLine = "\"" + id + "\" fjernet.";
                statusColor = 0xAAAAAA;
                this.initGui();
            }
            return;
        }

        switch (button.id) {
            case ID_ADD:
                addCurrent();
                break;
            case ID_CLEAR:
                CelleActions.clearSpecialCelles();
                statusLine = "Listen er ryddet.";
                statusColor = 0xAAAAAA;
                this.initGui();
                break;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiAddonsHub("Celler"));
                break;
            default:
                break;
        }
    }

    private void addCurrent() {
        String id = idField.getText() == null ? "" : idField.getText().trim();
        if (id.isEmpty()) {
            statusLine = "Indtast et celle-id først.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.addSpecialCelle(id);
        idField.setText("");
        statusLine = "\"" + id + "\" tilføjet.";
        statusColor = 0x55FF55;
        this.initGui();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            addCurrent();
            return;
        }
        idField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        idField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        idField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 100 - 22;
        int accent = Style.getAccentColor();
        drawCenteredString(this.fontRendererObj, "\u00a7lCelle Scanner - Special Celler", this.width / 2, titleY, accent);
        drawCenteredString(this.fontRendererObj, "Indtast et celle-id og tryk Tilføj (Enter virker også):", this.width / 2, titleY + 12, 0xAAAAAA);

        idField.drawTextBox();

        List<String> ids = MassiveOsFreakyAddons.config.specialCelleIds;
        if (ids.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "(ingen special-celler sat)", this.width / 2, listHintY, 0xAAAAAA);
        } else {
            String hint = ids.size() + " special-celle(r) - klik Fjern for at slette:";
            drawCenteredString(this.fontRendererObj, hint, this.width / 2, listHintY, 0xAAAAAA);
            if (ids.size() > MAX_REMOVE_ROWS) {
                int extra = ids.size() - MAX_REMOVE_ROWS;
                drawCenteredString(this.fontRendererObj,
                        "+ " + extra + " mere (fjern via /celler special remove <id>)",
                        this.width / 2, this.height / 2 + 118, 0x888888);
            }
        }

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 132, statusColor);
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
