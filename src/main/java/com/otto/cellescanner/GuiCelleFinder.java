package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * "Celle Finder" - highlights a celle you already scanned with a distinct cyan
 * ESP box + label and a HUD compass line, so you can walk to it even if it
 * isn't loaded or is outside the normal windows.
 *
 * Type an id (from the dashboard, a friend, wherever) and press Find, OR just
 * click one of the recently-scanned celler listed below the buttons - the
 * client already remembers every id it has seen (CellePositions), so you rarely
 * need to type anything.
 */
public class GuiCelleFinder extends GuiScreen {

    private static final int ID_PASTE = 0;
    private static final int ID_FIND = 1;
    private static final int ID_STOP = 2;
    private static final int ID_BACK = 3;
    // Recent-celle quick-pick buttons get ids RECENT_BASE + index.
    private static final int RECENT_BASE = 100;
    private static final int MAX_RECENT = 8;

    private static final int FIELD_W = 200;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int SMALL_BTN_W = 60;
    private static final int RECENT_H = 18;
    private static final int ROW_GAP = 6;

    private GuiTextField idField;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    private final List<String> recentShown = new ArrayList<String>();
    private int liveLineY;
    private int statusY;
    private int recentHintY;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.recentShown.clear();

        int centerX = this.width / 2;
        int fieldX = centerX - FIELD_W / 2;
        int y = this.height / 2 - 90;

        String carry = idField != null && idField.getText() != null
                ? idField.getText()
                : (CelleFinder.hasTarget() ? CelleFinder.getTarget() : "");
        idField = new GuiTextField(0, this.fontRendererObj, fieldX, y, FIELD_W - SMALL_BTN_W - 4, FIELD_H);
        idField.setMaxStringLength(64);
        idField.setText(carry);
        idField.setFocused(true);
        this.buttonList.add(new GuiButton(ID_PASTE, fieldX + FIELD_W - SMALL_BTN_W, y - 1, SMALL_BTN_W, FIELD_H + 2, "Indsæt"));
        y += FIELD_H + ROW_GAP + 6;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new GuiButton(ID_FIND, fieldX, y, halfW, BTN_H, "Find"));
        this.buttonList.add(new GuiButton(ID_STOP, fieldX + halfW + 4, y, halfW, BTN_H, "Stop"));
        y += BTN_H + ROW_GAP;

        this.buttonList.add(new GuiButton(ID_BACK, fieldX, y, FIELD_W, BTN_H, "Tilbage"));
        y += BTN_H + ROW_GAP + 2;

        liveLineY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 2;
        statusY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 8;

        recentHintY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 4;

        List<String> recent = CellePositions.recentIds(MAX_RECENT);
        for (int i = 0; i < recent.size(); i++) {
            String id = recent.get(i);
            recentShown.add(id);
            this.buttonList.add(new GuiButton(RECENT_BASE + i, fieldX, y, FIELD_W, RECENT_H, id));
            y += RECENT_H + 2;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= RECENT_BASE) {
            int index = button.id - RECENT_BASE;
            if (index >= 0 && index < recentShown.size()) {
                idField.setText(recentShown.get(index));
                find();
            }
            return;
        }

        switch (button.id) {
            case ID_PASTE:
                String clip = getClipboardString();
                if (clip != null) {
                    idField.setText(clip.trim());
                }
                break;
            case ID_FIND:
                find();
                break;
            case ID_STOP:
                CelleActions.clearFinderTarget();
                idField.setText("");
                statusLine = "Finder stoppet.";
                statusColor = 0xAAAAAA;
                break;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiCelleMenu());
                break;
            default:
                break;
        }
    }

    private void find() {
        String id = idField.getText() == null ? "" : idField.getText().trim();
        if (id.isEmpty()) {
            statusLine = "Indtast et celle-id først.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.setFinderTarget(id);
        if (CellePositions.get(id) == null) {
            statusLine = "\"" + id + "\" er ikke set endnu - gå rundt indtil dens skilt bliver scannet.";
            statusColor = 0xFFAA00;
        } else {
            statusLine = "Finder aktiv - se den cyan ESP-boks og HUD-linjen.";
            statusColor = 0x55FF55;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            find();
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

        int titleY = this.height / 2 - 90 - 28;
        drawCenteredString(this.fontRendererObj, "Celle Scanner - Celle Finder", this.width / 2, titleY, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "Indtast et id, eller klik en celle nedenfor:", this.width / 2, titleY + 12, 0xAAAAAA);

        idField.drawTextBox();

        String live = CelleFinder.describeTarget(Minecraft.getMinecraft());
        if (live != null) {
            drawCenteredString(this.fontRendererObj, live, this.width / 2, liveLineY, 0x55FFFF);
        }

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, this.width / 2, statusY, statusColor);
        }

        if (recentShown.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "(ingen celler scannet endnu)", this.width / 2, recentHintY, 0xAAAAAA);
        } else {
            drawCenteredString(this.fontRendererObj, "Senest scannede (klik for at finde):", this.width / 2, recentHintY, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
