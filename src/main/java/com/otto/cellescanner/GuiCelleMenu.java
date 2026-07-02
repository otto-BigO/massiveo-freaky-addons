package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Button-driven control panel for the Celle Scanner addon. Opened from the
 * Massiveo's Freaky Addons hub (GuiAddonsHub, keybind default: B) or by typing
 * "/celler" / "/celler menu" - everything is clickable, no chat typing needed.
 * "Tilbage" returns to the hub.
 *
 * Every button below calls into CelleActions - the exact same helper class
 * the typed /celler commands use - so clicking a button and typing the
 * matching command always behave identically, including the chat feedback
 * (which also lands in latest.log, unlike the on-screen-only debug panel).
 *
 * Layout is paired into two-column rows and the whole block is centered
 * using its own measured height (ROW_COUNT * ROW_H), instead of a fixed
 * magic-number offset. With 11 buttons stacked in single-wide rows the
 * panel could run off the bottom of the screen on higher GUI-scale
 * settings or smaller windows - this keeps it compact enough to always fit.
 */
public class GuiCelleMenu extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_NOTIFY = 1;
    private static final int ID_RELOAD = 2;
    private static final int ID_CLEAR = 3;
    private static final int ID_MOVE = 4;
    private static final int ID_DEBUG_PANEL = 5;
    private static final int ID_DEBUG_CHAT = 6;
    private static final int ID_MIN_DOWN = 7;
    private static final int ID_MIN_UP = 8;
    private static final int ID_MAX_DOWN = 9;
    private static final int ID_MAX_UP = 10;
    private static final int ID_CLOSE = 11;
    private static final int ID_ESP = 12;
    private static final int ID_SETTINGS = 13;
    private static final int ID_BOT = 14;
    private static final int ID_SPECIAL = 15;
    private static final int ID_FINDER = 16;

    private static final int ROW_H = 24;
    private static final int ROW_COUNT = 10;
    private static final int CONTENT_H = ROW_H * ROW_COUNT;
    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;
    private static final int HALF_W = (PANEL_W - 4) / 2;

    private GuiButton toggleButton;
    private GuiButton notifyButton;
    private GuiButton debugPanelButton;
    private GuiButton espButton;
    private GuiButton minLabel;
    private GuiButton maxLabel;

    private boolean showDebug = false;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int centerX = this.width / 2;
        int left = centerX - PANEL_W / 2;
        int right = centerX + 4;
        int y = this.height / 2 - CONTENT_H / 2;

        // row 0: scanner | notify
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, HALF_W, BTN_H, toggleLabel()));
        this.buttonList.add(notifyButton = new StyledButton(ID_NOTIFY, right, y, HALF_W, BTN_H, notifyLabel()));
        y += ROW_H;

        // row 1: min stepper
        this.buttonList.add(new StyledButton(ID_MIN_DOWN, left, y, 20, BTN_H, "-"));
        this.buttonList.add(minLabel = new StyledButton(ID_MIN_DOWN - 1000, left + 22, y, PANEL_W - 44, BTN_H, minLabel()));
        minLabel.enabled = false;
        this.buttonList.add(new StyledButton(ID_MIN_UP, left + PANEL_W - 20, y, 20, BTN_H, "+"));
        y += ROW_H;

        // row 2: max stepper
        this.buttonList.add(new StyledButton(ID_MAX_DOWN, left, y, 20, BTN_H, "-"));
        this.buttonList.add(maxLabel = new StyledButton(ID_MAX_DOWN - 1000, left + 22, y, PANEL_W - 44, BTN_H, maxLabel()));
        maxLabel.enabled = false;
        this.buttonList.add(new StyledButton(ID_MAX_UP, left + PANEL_W - 20, y, 20, BTN_H, "+"));
        y += ROW_H;

        // row 3: reload | clear
        this.buttonList.add(new StyledButton(ID_RELOAD, left, y, HALF_W, BTN_H, "Genindlæs"));
        this.buttonList.add(new StyledButton(ID_CLEAR, right, y, HALF_W, BTN_H, "Ryd cache"));
        y += ROW_H;

        // row 4: move | esp outline
        this.buttonList.add(new StyledButton(ID_MOVE, left, y, HALF_W, BTN_H, "Flyt HUD"));
        this.buttonList.add(espButton = new StyledButton(ID_ESP, right, y, HALF_W, BTN_H, espLabel()));
        y += ROW_H;

        // row 5: debug panel | debug to chat
        this.buttonList.add(debugPanelButton = new StyledButton(ID_DEBUG_PANEL, left, y, HALF_W, BTN_H, debugPanelLabel()));
        this.buttonList.add(new StyledButton(ID_DEBUG_CHAT, right, y, HALF_W, BTN_H, "Debug i chat"));
        y += ROW_H;

        // row 6: settings | discord bot
        this.buttonList.add(new StyledButton(ID_SETTINGS, left, y, HALF_W, BTN_H, "Indstillinger"));
        this.buttonList.add(new StyledButton(ID_BOT, right, y, HALF_W, BTN_H, "Discord Bot"));
        y += ROW_H;

        // row 7: special celler
        this.buttonList.add(new StyledButton(ID_SPECIAL, left, y, PANEL_W, BTN_H, "Special celler"));
        y += ROW_H;

        // row 8: celle finder
        this.buttonList.add(new StyledButton(ID_FINDER, left, y, PANEL_W, BTN_H, "Celle Finder"));
        y += ROW_H;

        // row 9: back to the addons hub
        this.buttonList.add(new StyledButton(ID_CLOSE, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Scanner: " + (CelleScannerMod.config.enabled ? "Til" : "Fra");
    }

    private String notifyLabel() {
        return "Notifik.: " + (CelleScannerMod.config.notify ? "Til" : "Fra");
    }

    private String debugPanelLabel() {
        return showDebug ? "Skjul panel" : "Vis panel";
    }

    private String espLabel() {
        return "ESP: " + (CelleScannerMod.config.espEnabled ? "Til" : "Fra");
    }

    /** ±5 while Shift is held, ±1 otherwise - lets the steppers cover a big range fast. */
    private static int step() {
        return isShiftKeyDown() ? 5 : 1;
    }

    private String minLabel() {
        return "Min timer: " + CelleScannerMod.config.minHours;
    }

    private String maxLabel() {
        return "Max timer: " + CelleScannerMod.config.maxHours;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleEnabled();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_NOTIFY:
                CelleActions.toggleNotify();
                notifyButton.displayString = notifyLabel();
                break;
            case ID_MIN_DOWN:
                CelleActions.adjustMinHours(-step());
                minLabel.displayString = minLabel();
                break;
            case ID_MIN_UP:
                CelleActions.adjustMinHours(step());
                minLabel.displayString = minLabel();
                break;
            case ID_MAX_DOWN:
                CelleActions.adjustMaxHours(-step());
                maxLabel.displayString = maxLabel();
                break;
            case ID_MAX_UP:
                CelleActions.adjustMaxHours(step());
                maxLabel.displayString = maxLabel();
                break;
            case ID_RELOAD:
                CelleActions.reloadConfig();
                this.initGui();
                break;
            case ID_CLEAR:
                CelleActions.clearCache();
                break;
            case ID_MOVE:
                CelleActions.openMover();
                break;
            case ID_ESP:
                CelleActions.toggleEsp();
                espButton.displayString = espLabel();
                break;
            case ID_DEBUG_PANEL:
                showDebug = !showDebug;
                debugPanelButton.displayString = debugPanelLabel();
                break;
            case ID_DEBUG_CHAT:
                CelleActions.debugDump();
                break;
            case ID_SETTINGS:
                CelleActions.openSettings();
                break;
            case ID_BOT:
                CelleActions.openBotScreen();
                break;
            case ID_SPECIAL:
                CelleActions.openSpecialScreen();
                break;
            case ID_FINDER:
                CelleActions.openFinderScreen();
                break;
            case ID_CLOSE:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int titleY = this.height / 2 - CONTENT_H / 2 - 22;
        drawCenteredString(this.fontRendererObj, "Celle Scanner", this.width / 2, titleY, 0x55FF55);
        drawCenteredString(this.fontRendererObj, "Hold Shift for +/-5 timer", this.width / 2, titleY + 10, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (showDebug) {
            drawDebugList();
        }
    }

    private void drawDebugList() {
        GlStateManager.disableLighting();

        List<Celle> all = new ArrayList<Celle>(CelleScannerMod.scanner.getCache().values());
        Collections.sort(all, new Comparator<Celle>() {
            @Override
            public int compare(Celle a, Celle b) {
                return Long.valueOf(a.liveRemainingSeconds()).compareTo(b.liveRemainingSeconds());
            }
        });

        int x = 8;
        int y = 8;
        int lineHeight = this.fontRendererObj.FONT_HEIGHT + 2;
        int panelWidth = 240;
        int maxRows = Math.min(all.size(), 18);
        int panelHeight = (maxRows + 1) * lineHeight + 8;

        drawRect(x - 4, y - 4, x + panelWidth, y + panelHeight, 0x99000000);

        drawString(this.fontRendererObj, "Cache: " + all.size() + " celler", x, y, 0xFFFF55);
        y += lineHeight;

        for (int i = 0; i < maxRows; i++) {
            Celle c = all.get(i);
            String ownerPart = c.owner != null ? " (" + c.owner + ")" : "";
            String line = c.celleId + " [" + c.status + "] " + CelleHud.formatDuration(c)
                    + " underrettet=" + c.notified + ownerPart;
            drawString(this.fontRendererObj, line, x, y, 0xFFFFFF);
            y += lineHeight;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
