package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the Troll Sounds addon: master on/off, plus a toggle and a
 * "Test" button for each event (death, kill, first hit, jump, AFK).
 */
public class GuiTroll extends GuiScreen {

    private static final int ID_MASTER = 0;
    private static final int ID_BACK = 1;
    private static final int TOGGLE_BASE = 10; // event i: toggle = BASE+i*2, test = BASE+i*2+1

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int ROW_GAP = 4;

    private static final String[] EV_LABEL = {"Død", "Kill", "Første hit", "Hop", "AFK"};
    private static final String[] EV_SOUND = {
            "cellescanner:troll.death", "cellescanner:troll.kill", "cellescanner:troll.firsthit",
            "cellescanner:troll.jump", "cellescanner:troll.afk"
    };

    private GuiButton masterButton;
    private final GuiButton[] evButtons = new GuiButton[EV_LABEL.length];

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 92;

        this.buttonList.add(masterButton = new StyledButton(ID_MASTER, left, y, PANEL_W, BTN_H, masterLabel()));
        y += BTN_H + ROW_GAP + 4;

        int testW = 46;
        int toggleW = PANEL_W - testW - 4;
        for (int i = 0; i < EV_LABEL.length; i++) {
            this.buttonList.add(evButtons[i] = new StyledButton(TOGGLE_BASE + i * 2, left, y, toggleW, BTN_H, evLabel(i)));
            this.buttonList.add(new StyledButton(TOGGLE_BASE + i * 2 + 1, left + toggleW + 4, y, testW, BTN_H, "Test"));
            y += BTN_H + ROW_GAP;
        }

        y += 4;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String masterLabel() {
        return "Troll Lyde: " + (CelleScannerMod.config.trollEnabled ? "Til" : "Fra");
    }

    private String evLabel(int i) {
        return EV_LABEL[i] + ": " + (evEnabled(i) ? "Til" : "Fra");
    }

    private boolean evEnabled(int i) {
        switch (i) {
            case 0: return CelleScannerMod.config.trollDeath;
            case 1: return CelleScannerMod.config.trollKill;
            case 2: return CelleScannerMod.config.trollFirstHit;
            case 3: return CelleScannerMod.config.trollJump;
            default: return CelleScannerMod.config.trollAfk;
        }
    }

    private void evToggle(int i) {
        switch (i) {
            case 0: CelleActions.toggleTrollDeath(); break;
            case 1: CelleActions.toggleTrollKill(); break;
            case 2: CelleActions.toggleTrollFirstHit(); break;
            case 3: CelleActions.toggleTrollJump(); break;
            default: CelleActions.toggleTrollAfk(); break;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        if (id == ID_MASTER) {
            CelleActions.toggleTroll();
            masterButton.displayString = masterLabel();
            return;
        }
        if (id == ID_BACK) {
            CelleActions.openHub();
            return;
        }
        if (id >= TOGGLE_BASE) {
            int rel = id - TOGGLE_BASE;
            int ev = rel / 2;
            if (ev < 0 || ev >= EV_LABEL.length) {
                return;
            }
            if (rel % 2 == 0) {
                evToggle(ev);
                evButtons[ev].displayString = evLabel(ev);
            } else {
                CelleActions.testTroll(EV_SOUND[ev]);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 92 - 24;
        drawCenteredString(this.fontRendererObj, "Troll Lyde", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Fjollede lyde kun du hører, på dine begivenheder.", cx, titleY + 12, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
