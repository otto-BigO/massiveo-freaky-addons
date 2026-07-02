package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Hub tile for the Player Info addon: explains the shift + right-click gesture,
 * lets you turn the feature on/off, and lets you look a player up by name (they
 * must be loaded/nearby, so their armor and model are available). The menu
 * itself is GuiPlayerInfo.
 */
public class GuiPlayerInfoMenu extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_SEARCH = 1;
    private static final int ID_BACK = 2;

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int FIELD_H = 18;

    private GuiButton toggleButton;
    private GuiTextField nameField;
    private String status = "";
    private int statusColor = 0xAAAAAA;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 26;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 10;

        int searchBtnW = 60;
        nameField = new GuiTextField(0, this.fontRendererObj, left, y, PANEL_W - searchBtnW - 4, FIELD_H);
        nameField.setMaxStringLength(16);
        this.buttonList.add(new StyledButton(ID_SEARCH, left + PANEL_W - searchBtnW, y - 1, searchBtnW, FIELD_H + 2, "Søg"));
        y += FIELD_H + 10;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String toggleLabel() {
        return "Spiller Info: " + (CelleScannerMod.config.playerInfoEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.togglePlayerInfo();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_SEARCH:
                search();
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    private void search() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            status = "Indtast et spillernavn.";
            statusColor = 0xFF5555;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        for (Object o : mc.theWorld.playerEntities) {
            if (o instanceof EntityPlayer && ((EntityPlayer) o).getName().equalsIgnoreCase(name)) {
                mc.displayGuiScreen(new GuiPlayerInfo((EntityPlayer) o));
                return;
            }
        }
        status = "\"" + name + "\" er ikke i nærheden (skal være indenfor rækkevidde).";
        statusColor = 0xFFAA00;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            search();
            return;
        }
        nameField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 66;
        drawCenteredString(this.fontRendererObj, "Spiller Info", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Shift + højreklik en spiller, eller søg herunder.", cx, titleY + 12, 0xAAAAAA);

        nameField.drawTextBox();

        if (!status.isEmpty()) {
            drawCenteredString(this.fontRendererObj, status, cx, this.height / 2 + 40, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
