package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;

/**
 * GUI settings tab (opened from the gear in the hub). Meant to hold all the
 * move/customize options for the mod's on-screen GUIs. For now it's a shell with
 * the existing HUD-move screen and a note that more is coming.
 */
public class GuiGuiSettings extends GuiScreen {

    private static final int ID_MOVE_HUD       = 0;
    private static final int ID_BACK           = 1;
    private static final int ID_DEBUG          = 2;
    private static final int ID_DEBUG_LOG      = 3;
    private static final int ID_DEBUG_COPY     = 4;
    private static final int ID_FLIP_TEST      = 5;
    private static final int PANEL_W           = 220;
    private static final int BTN_H             = 20;
    // Width split for the "Debug → Fil" row: toggle gets TOGGLE_W, copy gets the rest.
    private static final int COPY_W            = 50;
    private static final int GAP               = 4;
    private static final int TOGGLE_W          = PANEL_W - COPY_W - GAP;

    private GuiButton debugButton;
    private GuiButton debugLogButton;
    private GuiButton debugCopyButton;

    /** Feedback shown briefly after a copy, null when idle. */
    private String copyFeedback = null;
    private long   copyFeedbackUntil = 0;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 65;

        this.buttonList.add(new StyledButton(ID_MOVE_HUD, left, y, PANEL_W, BTN_H, "Flyt HUD'er"));
        y += BTN_H + 6;

        this.buttonList.add(debugButton = new StyledButton(ID_DEBUG, left, y, PANEL_W, BTN_H, debugLabel()));
        y += BTN_H + 6;

        // "Debug → Fil" toggle + copy button on the same row.
        this.buttonList.add(debugLogButton = new StyledButton(ID_DEBUG_LOG, left, y, TOGGLE_W, BTN_H, debugLogLabel()));
        this.buttonList.add(debugCopyButton = new StyledButton(ID_DEBUG_COPY, left + TOGGLE_W + GAP, y, COPY_W, BTN_H, "Kopier"));
        y += BTN_H + 6;

        this.buttonList.add(new StyledButton(ID_FLIP_TEST, left, y, PANEL_W, BTN_H, "Test Flip Case"));
        y += BTN_H + 6;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    // ------------------------------------------------------------------
    // Labels
    // ------------------------------------------------------------------

    private String debugLabel() {
        boolean on = CelleScannerMod.config.debugEnabled != null && CelleScannerMod.config.debugEnabled;
        return "Debug: " + (on ? "Til" : "Fra");
    }

    private String debugLogLabel() {
        boolean on = Boolean.TRUE.equals(CelleScannerMod.config.debugLogEnabled);
        return "Debug \u2192 Fil: " + (on ? "Til" : "Fra");
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_MOVE_HUD:
                CelleActions.openHudEditor();
                break;

            case ID_DEBUG: {
                boolean on = CelleScannerMod.config.debugEnabled != null && CelleScannerMod.config.debugEnabled;
                CelleScannerMod.config.debugEnabled = !on;
                CelleScannerMod.config.save();
                debugButton.displayString = debugLabel();
                if (!on) {
                    DebugLog.openSession();
                }
                break;
            }

            case ID_DEBUG_LOG: {
                boolean on = Boolean.TRUE.equals(CelleScannerMod.config.debugLogEnabled);
                CelleScannerMod.config.debugLogEnabled = !on;
                CelleScannerMod.config.save();
                debugLogButton.displayString = debugLogLabel();
                if (!on) {
                    DebugLog.openSession();
                    CelleActions.message("Debug log: " + DebugLog.getFilePath());
                }
                break;
            }

            case ID_DEBUG_COPY:
                copyLogToClipboard();
                break;

            case ID_FLIP_TEST:
                FlipCaseGui gui = new FlipCaseGui(this.mc.getSession().getUsername(), "FreakyVille");
                gui.setWinner("FreakyVille");
                this.mc.displayGuiScreen(gui);
                break;

            case ID_BACK:
                CelleActions.openHub();
                break;

            default:
                break;
        }
    }

    /**
     * Reads the entire debug log file and puts it on the system clipboard.
     * Shows brief feedback text above the button ("Kopieret!" / "Fil mangler" / "Fejl").
     */
    private void copyLogToClipboard() {
        String path = DebugLog.getFilePath();
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            showCopyFeedback("\u00a7cFil mangler");
            return;
        }
        try {
            byte[] raw = Files.readAllBytes(file.toPath());
            String content = new String(raw, Charset.forName("UTF-8"));
            if (content.isEmpty()) {
                showCopyFeedback("\u00a7eLog er tom");
                return;
            }
            GuiScreen.setClipboardString(content);
            // Count lines for a friendly confirmation.
            int lines = 1;
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '\n') lines++;
            }
            showCopyFeedback("\u00a7aKopieret! (" + lines + " linjer)");
        } catch (IOException e) {
            showCopyFeedback("\u00a7cFejl: " + e.getMessage());
        }
    }

    private void showCopyFeedback(String text) {
        copyFeedback = text;
        copyFeedbackUntil = System.currentTimeMillis() + 2500;
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 115;
        drawCenteredString(this.fontRendererObj, "GUI Indstillinger", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Flyt og tilpas mod'ens GUI'er.", cx, titleY + 12, 0xAAAAAA);

        // Copy feedback or file path footer.
        if (copyFeedback != null && System.currentTimeMillis() < copyFeedbackUntil) {
            drawCenteredString(this.fontRendererObj, copyFeedback, cx, this.height / 2 + 90, 0xFFFFFF);
        } else {
            copyFeedback = null;
            if (Boolean.TRUE.equals(CelleScannerMod.config.debugLogEnabled)) {
                String path = DebugLog.getFilePath();
                String label = path.length() > 50 ? "..." + path.substring(path.length() - 47) : path;
                drawCenteredString(this.fontRendererObj, "\u00a77" + label, cx, this.height / 2 + 90, 0x888888);
            } else {
                drawCenteredString(this.fontRendererObj, "Flere indstillinger kommer snart.", cx, this.height / 2 + 90, 0x888888);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
