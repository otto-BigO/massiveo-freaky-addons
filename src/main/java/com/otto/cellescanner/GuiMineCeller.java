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
 * Control screen for the Mine Celler addon. Fetch your celler from the server
 * with "/ce find", toggle their gold ESP, add/clear ids, and click a celle in
 * the list to point the Finder compass at it and walk there.
 */
public class GuiMineCeller extends GuiScreen {

    private static final int ID_FETCH = 0;
    private static final int ID_ESP = 1;
    private static final int ID_ADD = 2;
    private static final int ID_CLEAR = 3;
    private static final int ID_BACK = 4;
    private static final int FIND_BASE = 100;
    private static final int MAX_ROWS = 4;

    private static final int FIELD_W = 150;
    private static final int FIELD_H = 18;
    private static final int BTN_H = 20;
    private static final int ROW_GAP = 6;

    private GuiTextField idField;
    private GuiButton espButton;
    private String statusLine = "";
    private int statusColor = 0xAAAAAA;

    private final List<String> shownIds = new ArrayList<String>();
    private int shownCount = -1;
    private int listHintY;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.shownIds.clear();

        int centerX = this.width / 2;
        int left = centerX - 146; // Left aligned controls
        int y = this.height / 2 - 102;

        this.buttonList.add(new StyledButton(ID_FETCH, left, y, FIELD_W, BTN_H, "Hent mine celler (/ce find)"));
        y += BTN_H + ROW_GAP;
        this.buttonList.add(espButton = new StyledButton(ID_ESP, left, y, FIELD_W, BTN_H, espLabel()));
        y += BTN_H + ROW_GAP + 4;

        String carry = idField != null && idField.getText() != null ? idField.getText() : "";
        idField = new GuiTextField(0, this.fontRendererObj, left, y, FIELD_W - 54, FIELD_H);
        idField.setMaxStringLength(64);
        idField.setText(carry);
        this.buttonList.add(new StyledButton(ID_ADD, left + FIELD_W - 50, y - 1, 50, FIELD_H + 2, "Tilf\u00f8j"));
        y += FIELD_H + ROW_GAP + 4;

        int halfW = (FIELD_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_CLEAR, left, y, halfW, BTN_H, "Ryd alle"));
        this.buttonList.add(new StyledButton(ID_BACK, left + halfW + 4, y, halfW, BTN_H, "Tilbage"));
        y += BTN_H + ROW_GAP + 2;

        listHintY = y;
        y += this.fontRendererObj.FONT_HEIGHT + 4;

        List<String> ids = CelleScannerMod.config.myCelleIds;
        shownCount = ids.size();
        int shown = Math.min(ids.size(), MAX_ROWS);
        for (int i = 0; i < shown; i++) {
            String id = ids.get(i);
            shownIds.add(id);
            boolean known = CellePositions.get(id) != null;
            String label = "-> " + id + (known ? "" : " (ikke scannet)");
            this.buttonList.add(new StyledButton(FIND_BASE + i, left, y, FIELD_W, FIELD_H, label));
            y += FIELD_H + 2;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String espLabel() {
        return "ESP: " + (CelleScannerMod.config.mineCellerEspEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= FIND_BASE) {
            int index = button.id - FIND_BASE;
            if (index >= 0 && index < shownIds.size()) {
                String id = shownIds.get(index);
                CelleActions.setFinderTarget(id);
                statusLine = "Finder s\u00f8ger nu efter " + id + ".";
                statusColor = 0x55FFFF;
            }
            return;
        }

        switch (button.id) {
            case ID_FETCH:
                CelleActions.fetchMyCeller();
                statusLine = "Henter celler...";
                statusColor = 0xAAAAAA;
                break;
            case ID_ESP:
                CelleActions.toggleMineCellerEsp();
                espButton.displayString = espLabel();
                break;
            case ID_ADD:
                addCurrent();
                break;
            case ID_CLEAR:
                CelleActions.clearMyCeller();
                statusLine = "Listen er ryddet.";
                statusColor = 0xAAAAAA;
                this.initGui();
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    private void addCurrent() {
        String id = idField.getText() == null ? "" : idField.getText().trim();
        if (id.isEmpty()) {
            statusLine = "Indtast et celle-id f\u00f8rst.";
            statusColor = 0xFF5555;
            return;
        }
        CelleActions.addMyCelle(id);
        idField.setText("");
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
        if (shownCount != CelleScannerMod.config.myCelleIds.size()) {
            this.initGui();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int titleY = cy - 126;

        drawCenteredString(this.fontRendererObj, "Mine Celler", cx, titleY, 0x55FF55);

        idField.drawTextBox();
        List<String> ids = CelleScannerMod.config.myCelleIds;

        // The 2D radar/minimap that used to sit here was removed - a new one
        // may come back later. For now Mine Celler is just the list below.

        // Left list hints
        int leftC = cx - 146;
        int listLeft = leftC + FIELD_W / 2;
        if (ids.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "(ingen celler)", listLeft, listHintY, 0xAAAAAA);
        } else {
            drawCenteredString(this.fontRendererObj, ids.size() + " celle(r) - klik for at s\u00f8ge:", listLeft, listHintY, 0xAAAAAA);
            if (ids.size() > MAX_ROWS) {
                drawCenteredString(this.fontRendererObj, "+ " + (ids.size() - MAX_ROWS) + " mere", listLeft, this.height / 2 + 118, 0x888888);
            }
        }

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, statusLine, cx, this.height / 2 + 132, statusColor);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
