package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * "Settings tab" for the HUD/ESP - turn optional info on/off (owner name,
 * status tag, distance, seconds, ESP labels), set how many HUD lines to show,
 * and read the color/symbol legend. Opened via the "Indstillinger" button in
 * the main menu, or /celler settings.
 */
public class GuiCelleSettings extends GuiScreen {

    private static final int ID_SECONDS = 0;
    private static final int ID_OWNER = 1;
    private static final int ID_STATUS_TAG = 2;
    private static final int ID_DISTANCE = 3;
    private static final int ID_ESP_LABELS = 4;
    private static final int ID_HUD_DOWN = 5;
    private static final int ID_HUD_UP = 6;
    private static final int ID_BACK = 7;
    private static final int ID_HUD_LABEL = -1000;

    private static final int ROW_H = 24;
    private static final int ROW_COUNT = 7;
    private static final int CONTENT_H = ROW_H * ROW_COUNT;
    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton secondsButton;
    private GuiButton ownerButton;
    private GuiButton statusTagButton;
    private GuiButton distanceButton;
    private GuiButton espLabelsButton;
    private GuiButton hudEntriesLabel;

    private int legendY;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int left = centerX - PANEL_W / 2;
        int y = this.height / 2 - CONTENT_H / 2;

        this.buttonList.add(secondsButton = new StyledButton(ID_SECONDS, left, y, PANEL_W, BTN_H, secondsLabel()));
        y += ROW_H;
        this.buttonList.add(ownerButton = new StyledButton(ID_OWNER, left, y, PANEL_W, BTN_H, ownerLabel()));
        y += ROW_H;
        this.buttonList.add(statusTagButton = new StyledButton(ID_STATUS_TAG, left, y, PANEL_W, BTN_H, statusTagLabel()));
        y += ROW_H;
        this.buttonList.add(distanceButton = new StyledButton(ID_DISTANCE, left, y, PANEL_W, BTN_H, distanceLabel()));
        y += ROW_H;
        this.buttonList.add(espLabelsButton = new StyledButton(ID_ESP_LABELS, left, y, PANEL_W, BTN_H, espLabelsLabel()));
        y += ROW_H;

        // maxHudEntries stepper: - [label] +
        this.buttonList.add(new StyledButton(ID_HUD_DOWN, left, y, 20, BTN_H, "-"));
        this.buttonList.add(hudEntriesLabel = new StyledButton(ID_HUD_LABEL, left + 22, y, PANEL_W - 44, BTN_H, hudEntriesLabel()));
        hudEntriesLabel.enabled = false;
        this.buttonList.add(new StyledButton(ID_HUD_UP, left + PANEL_W - 20, y, 20, BTN_H, "+"));
        y += ROW_H;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
        y += ROW_H + 6;

        legendY = y;
    }

    private String secondsLabel() {
        return "Vis sekunder: " + (CelleScannerMod.config.showSeconds ? "Til" : "Fra");
    }

    private String ownerLabel() {
        return "Vis ejernavn: " + (CelleScannerMod.config.showOwner ? "Til" : "Fra");
    }

    private String statusTagLabel() {
        return "Vis status-mærke: " + (CelleScannerMod.config.showStatusTag ? "Til" : "Fra");
    }

    private String distanceLabel() {
        return "Vis afstand: " + (CelleScannerMod.config.showDistance ? "Til" : "Fra");
    }

    private String espLabelsLabel() {
        return "ESP celle-id label: " + (CelleScannerMod.config.espLabels ? "Til" : "Fra");
    }

    private String hudEntriesLabel() {
        return "Maks HUD-linjer: " + CelleScannerMod.config.maxHudEntries;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_SECONDS:
                CelleActions.toggleShowSeconds();
                secondsButton.displayString = secondsLabel();
                break;
            case ID_OWNER:
                CelleActions.toggleShowOwner();
                ownerButton.displayString = ownerLabel();
                break;
            case ID_STATUS_TAG:
                CelleActions.toggleShowStatusTag();
                statusTagButton.displayString = statusTagLabel();
                break;
            case ID_DISTANCE:
                CelleActions.toggleShowDistance();
                distanceButton.displayString = distanceLabel();
                break;
            case ID_ESP_LABELS:
                CelleActions.toggleEspLabels();
                espLabelsButton.displayString = espLabelsLabel();
                break;
            case ID_HUD_DOWN:
                CelleActions.adjustMaxHudEntries(-1);
                hudEntriesLabel.displayString = hudEntriesLabel();
                break;
            case ID_HUD_UP:
                CelleActions.adjustMaxHudEntries(1);
                hudEntriesLabel.displayString = hudEntriesLabel();
                break;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiCelleMenu());
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int titleY = this.height / 2 - CONTENT_H / 2 - 14;
        drawCenteredString(this.fontRendererObj, "Celle Scanner - Indstillinger", this.width / 2, titleY, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Legend: what the ESP colors and the "~" prefix mean, so the HUD/ESP
        // are self-explanatory without digging through the README.
        int x = this.width / 2 - PANEL_W / 2;
        int y = legendY;
        int lh = this.fontRendererObj.FONT_HEIGHT + 1;
        drawString(this.fontRendererObj, "Forklaring:", x, y, 0xFFFFFF);
        y += lh;
        drawString(this.fontRendererObj, "Grøn = til salg nu", x, y, 0x33FF55);
        y += lh;
        drawString(this.fontRendererObj, "Orange = solgt, bliver snart ledig", x, y, 0xFFAA00);
        y += lh;
        drawString(this.fontRendererObj, "Cyan = Celle Finder-mål", x, y, 0x55FFFF);
        y += lh;
        drawString(this.fontRendererObj, "~ foran tid/id = estimeret, ikke bekræftet endnu", x, y, 0xAAAAAA);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
