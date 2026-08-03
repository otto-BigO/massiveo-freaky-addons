package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Reporting on/off.
 *
 * The webhook ships with the mod and is not editable, so this screen is only a
 * switch plus a test button. There is deliberately no field for the url: every
 * client reports into the same channel, and letting one point somewhere else
 * would quietly drop that player out of the shared picture.
 */
public class GuiCelleBot extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_TEST = 1;
    private static final int ID_BACK = 2;

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleButton;
    private String statusLine = "";

    private final ScreenIntro screenIntro = new ScreenIntro();

    private static boolean enabled() {
        return MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.botReportEnabled;
    }

    private String toggleLabel() {
        return "Rapportering: " + (enabled()
                ? Style.getAccentFormatting() + "TIL"
                : EnumChatFormatting.DARK_GRAY + "FRA");
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        screenIntro.restart();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int y = cy - 10;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += 26;

        int halfW = (PANEL_W - 4) / 2;
        this.buttonList.add(new StyledButton(ID_TEST, left, y, halfW, BTN_H, "Test forbindelse"));
        this.buttonList.add(new StyledButton(ID_BACK, left + halfW + 4, y, halfW, BTN_H, "< Tilbage"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleBotReport();
                toggleButton.displayString = toggleLabel();
                statusLine = enabled()
                        ? "Rapportering slået til."
                        : "Rapportering slået fra. Dine celler deles ikke.";
                break;
            case ID_TEST:
                if (!enabled()) {
                    statusLine = "Slå rapportering til først.";
                    break;
                }
                statusLine = "Sender test-rapport...";
                CelleActions.testBotConnection();
                break;
            case ID_BACK:
                CelleActions.openHub();
                return;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        screenIntro.begin(this.width, this.height, mouseX, mouseY);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Celle Bot",
                cx, cy - 76, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Deler dine scannede celler med de andre",
                cx, cy - 62, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.DARK_GRAY + "Alle bruger den samme kanal. Intet at indstille.",
                cx, cy - 50, 0x888888);

        drawCenteredString(this.fontRendererObj,
                (enabled() ? Style.getAccentFormatting() + "Sender data"
                           : EnumChatFormatting.DARK_GRAY + "Sender ikke data"),
                cx, cy - 30, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (!statusLine.isEmpty()) {
            drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + statusLine,
                    cx, cy + 46, 0xAAAAAA);
        }

        screenIntro.end();
        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
