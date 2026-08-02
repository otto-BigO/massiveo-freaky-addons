package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

public class GuiAccessKey extends GuiScreen {

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 20;
    private static final int BTN_H = 20;
    private static final int ROW_GAP = 6;

    private static final int ID_VERIFY = 1;
    private static final int ID_DISCONNECT = 2;

    private GuiTextField keyField;
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
        int y = this.height / 2 - 30;

        keyField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W, FIELD_H);
        keyField.setMaxStringLength(64);
        keyField.setText(MassiveOsFreakyAddons.config.accessKey == null ? "" : MassiveOsFreakyAddons.config.accessKey);
        keyField.setFocused(true);
        
        y += FIELD_H + ROW_GAP + 4;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_VERIFY, fieldX, y, halfW, BTN_H, "Bekræft"));
        this.buttonList.add(new StyledButton(ID_DISCONNECT, fieldX + halfW + 4, y, halfW, BTN_H, "Log af"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_VERIFY) {
            verify();
        } else if (button.id == ID_DISCONNECT) {
            disconnect();
        }
    }

    private void verify() {
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        if (key.isEmpty()) {
            statusLine = "Indtast venligst en licensnøgle.";
            statusColor = 0xFF5555;
            return;
        }

        statusLine = "Verificerer...";
        statusColor = 0xAAAAAA;

        if (AccessSystem.verifyKey(key)) {
            MassiveOsFreakyAddons.config.accessKey = key;
            MassiveOsFreakyAddons.config.save();
            statusLine = "Licens godkendt!";
            statusColor = 0x55FF55;
            
            this.mc.displayGuiScreen(null);
        } else {
            statusLine = "Ugyldig adgangskode!";
            statusColor = 0xFF5555;
        }
    }

    private void disconnect() {
        this.mc.loadWorld((net.minecraft.client.multiplayer.WorldClient)null);
        this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return;
        }

        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            verify();
            return;
        }

        keyField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        keyField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        keyField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int titleY = this.height / 2 - 80;
        int accent = Style.getAccentColor();
        drawCenteredString(this.fontRendererObj, "\u00a7lMassiveo's Addons - Licens Verification", this.width / 2, titleY, accent);
        drawCenteredString(this.fontRendererObj, "Indtast din adgangskode for at låse op:", this.width / 2, titleY + 14, 0xAAAAAA);

        keyField.drawTextBox();

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, this.height / 2 + 30, statusColor);
        }

        drawCenteredString(this.fontRendererObj, "Din HWID: " + AccessSystem.getHWID(), this.width / 2, this.height / 2 + 50, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
