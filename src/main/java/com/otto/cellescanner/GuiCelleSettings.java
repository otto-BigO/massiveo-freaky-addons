package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Scrollable settings tab for HUD/ESP options (owner name, status tag,
 * distance, seconds, ESP labels, max HUD entries). Features smooth viewport
 * scrolling to accommodate unlimited future settings options cleanly.
 */
public class GuiCelleSettings extends GuiScreen {

    private static final int ID_SECONDS = 0;
    private static final int ID_OWNER = 1;
    private static final int ID_STATUS_TAG = 2;
    private static final int ID_DISTANCE = 3;
    private static final int ID_ESP_LABELS = 4;
    private static final int ID_HUD_DOWN = 5;
    private static final int ID_HUD_UP = 6;
    private static final int ID_BACK = 100;

    private static final int ROW_H = 24;
    private static final int BTN_H = 18;
    private static final int PANEL_W = 210;
    private static final int VIEWPORT_H = 150;

    private GuiButton secondsButton;
    private GuiButton ownerButton;
    private GuiButton statusTagButton;
    private GuiButton distanceButton;
    private GuiButton espLabelsButton;

    private float scroll = 0f;
    private int targetScroll = 0;
    private int maxScroll = 0;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int y = this.height / 2 - 70;

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

        // maxHudEntries stepper buttons (- and +)
        this.buttonList.add(new StyledButton(ID_HUD_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(new StyledButton(ID_HUD_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += ROW_H + 10;

        // Back button (placed outside scroll area)
        this.buttonList.add(new StyledButton(ID_BACK, left, this.height / 2 + 82, PANEL_W, BTN_H, "< Tilbage"));

        int totalHeight = y - (this.height / 2 - 70);
        maxScroll = Math.max(0, totalHeight - VIEWPORT_H);
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

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getDWheel();
        if (d > 0) {
            targetScroll -= ROW_H;
        } else if (d < 0) {
            targetScroll += ROW_H;
        }
        if (targetScroll < 0) targetScroll = 0;
        if (targetScroll > maxScroll) targetScroll = maxScroll;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        float diff = targetScroll - scroll;
        if (Math.abs(diff) > 0.05f) {
            scroll += diff * 0.2f;
        } else {
            scroll = targetScroll;
        }

        int startY = this.height / 2 - 70 - (int) scroll;
        int y = startY;
        secondsButton.yPosition = y; y += ROW_H;
        ownerButton.yPosition = y; y += ROW_H;
        statusTagButton.yPosition = y; y += ROW_H;
        distanceButton.yPosition = y; y += ROW_H;
        espLabelsButton.yPosition = y;
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
                break;
            case ID_HUD_UP:
                CelleActions.adjustMaxHudEntries(1);
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
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        int titleY = cy - 124;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Indstillinger", cx, titleY, Style.getAccentColor());

        // Render viewport scissoring for scrollable content
        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();
        int scissorX = (cx - PANEL_W / 2) * scale;
        int scissorY = (this.mc.displayHeight - (cy + 75) * scale);
        int scissorW = PANEL_W * scale;
        int scissorH = VIEWPORT_H * scale;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        for (GuiButton b : this.buttonList) {
            if (b.id != ID_BACK) {
                b.drawButton(this.mc, mouseX, mouseY);
            }
        }

        // Draw HUD max entries label directly without using disabled button anti-pattern
        int hudLabelY = cy - 70 - (int) scroll + 5 * ROW_H + 4;
        drawCenteredString(this.fontRendererObj, "Maks HUD-linjer: " + CelleScannerMod.config.maxHudEntries, cx, hudLabelY, 0xFFFFFF);

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Draw Back button outside scissor
        for (GuiButton b : this.buttonList) {
            if (b.id == ID_BACK) {
                b.drawButton(this.mc, mouseX, mouseY);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
