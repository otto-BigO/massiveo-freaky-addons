package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Fine-tuning tab for Auto Mine: numeric knobs (how close before mining, mining
 * reach, when to swap a worn pickaxe) plus the drop-collect toggle. Each numeric
 * row is a - [value] + stepper. Opened from {@link GuiAutoMineSettings}.
 */
public class GuiAutoMineTuning extends GuiScreen {

    // Stepper button ids: DOWN = base+0, UP = base+1, disabled value = base+2.
    private static final int APPROACH = 10;
    private static final int REACH = 20;
    private static final int PICK = 30;
    private static final int ID_COLLECT = 2;
    private static final int ID_HUMAN = 3;
    private static final int ID_JITTER = 4;
    private static final int ID_STAFF = 5;
    private static final int ID_SCAFFOLD = 6;
    private static final int ID_BACK = 7;

    // Ranges / steps.
    private static final double APPROACH_MIN = 1.5, APPROACH_MAX = 4.0, APPROACH_STEP = 0.2;
    private static final double REACH_MIN = 3.0, REACH_MAX = 4.5, REACH_STEP = 0.1; // capped legit
    private static final int PICK_MIN = 0, PICK_MAX = 500, PICK_STEP = 10;

    private static final int PANEL_W = 240;
    private static final int BTN_H = 18;
    private static final int GAP = 4;
    private static final int STEP_W = 22;

    private static final int TITLE_Y_OFF = -110;
    private static final int TEXT_BLOCK_H = 26;

    private GuiButton approachLabel, reachLabel, pickLabel, collectButton, humanButton, jitterButton, staffButton, scaffoldButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 + TITLE_Y_OFF + TEXT_BLOCK_H;

        approachLabel = addStepper(APPROACH, left, y, approachLabel());
        y += BTN_H + GAP;
        reachLabel = addStepper(REACH, left, y, reachLabel());
        y += BTN_H + GAP;
        pickLabel = addStepper(PICK, left, y, pickLabel());
        y += BTN_H + GAP;
        this.buttonList.add(collectButton = new StyledButton(ID_COLLECT, left, y, PANEL_W, BTN_H, collectLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(humanButton = new StyledButton(ID_HUMAN, left, y, PANEL_W, BTN_H, humanLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(jitterButton = new StyledButton(ID_JITTER, left, y, PANEL_W, BTN_H, jitterLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(staffButton = new StyledButton(ID_STAFF, left, y, PANEL_W, BTN_H, staffLabel()));
        y += BTN_H + GAP;
        this.buttonList.add(scaffoldButton = new StyledButton(ID_SCAFFOLD, left, y, PANEL_W, BTN_H, scaffoldLabel()));
        y += BTN_H + GAP + 2;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    /** A "- [value] +" row; returns the (disabled) value-label button so we can update it. */
    private GuiButton addStepper(int base, int left, int y, String label) {
        this.buttonList.add(new StyledButton(base, left, y, STEP_W, BTN_H, "-"));
        GuiButton valueBtn = new StyledButton(base + 2, left + STEP_W + 2, y, PANEL_W - 2 * (STEP_W + 2), BTN_H, label);
        valueBtn.enabled = false;
        this.buttonList.add(valueBtn);
        this.buttonList.add(new StyledButton(base + 1, left + PANEL_W - STEP_W, y, STEP_W, BTN_H, "+"));
        return valueBtn;
    }

    private String approachLabel() {
        return "Mine-afstand: " + String.format("%.1f", CelleScannerMod.config.autoMineApproachDist);
    }

    private String reachLabel() {
        return "Ræk-vidde: " + String.format("%.1f", CelleScannerMod.config.autoMineReach);
    }

    private String pickLabel() {
        int v = CelleScannerMod.config.autoMinePickaxeMin;
        return "Skift hakke ved: " + (v == 0 ? "0 (til den knækker)" : v + " holdbarhed");
    }

    private String collectLabel() {
        boolean on = CelleScannerMod.config.autoMineCollectDrops == null || CelleScannerMod.config.autoMineCollectDrops;
        return "Saml drops (jern): " + (on ? "Til" : "Fra");
    }

    private String humanLabel() {
        boolean on = CelleScannerMod.config.autoMineHumanizedDelays == null || CelleScannerMod.config.autoMineHumanizedDelays;
        return "Humaniserede pauses: " + (on ? "Til (40-110ms)" : "Fra");
    }

    private String jitterLabel() {
        boolean on = CelleScannerMod.config.autoMineAimJitter == null || CelleScannerMod.config.autoMineAimJitter;
        return "Naturlig sigte-drift (Anti-Cheat): " + (on ? "Til" : "Fra");
    }

    private String staffLabel() {
        boolean alert = CelleScannerMod.config.autoMineStaffAlert == null || CelleScannerMod.config.autoMineStaffAlert;
        boolean dc = CelleScannerMod.config.autoMineStaffDisconnect != null && CelleScannerMod.config.autoMineStaffDisconnect;
        return "Staff-sikring: " + (dc ? "Auto-Disconnect" : (alert ? "Advarsel Lyd" : "Fra"));
    }

    private String scaffoldLabel() {
        boolean on = CelleScannerMod.config.autoMineSmartScaffold == null || CelleScannerMod.config.autoMineSmartScaffold;
        return "Smart Scaffold / Hul-Udbrud: " + (on ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = CelleScannerMod.config;
        int id = button.id;
        if (id == APPROACH || id == APPROACH + 1) {
            c.autoMineApproachDist = clampD(c.autoMineApproachDist + (id == APPROACH ? -APPROACH_STEP : APPROACH_STEP),
                    APPROACH_MIN, APPROACH_MAX);
            approachLabel.displayString = approachLabel();
            c.save();
        } else if (id == REACH || id == REACH + 1) {
            c.autoMineReach = clampD(c.autoMineReach + (id == REACH ? -REACH_STEP : REACH_STEP), REACH_MIN, REACH_MAX);
            reachLabel.displayString = reachLabel();
            c.save();
        } else if (id == PICK || id == PICK + 1) {
            int v = c.autoMinePickaxeMin + (id == PICK ? -PICK_STEP : PICK_STEP);
            c.autoMinePickaxeMin = Math.max(PICK_MIN, Math.min(PICK_MAX, v));
            pickLabel.displayString = pickLabel();
            c.save();
        } else if (id == ID_COLLECT) {
            boolean on = c.autoMineCollectDrops == null || c.autoMineCollectDrops;
            c.autoMineCollectDrops = !on;
            collectButton.displayString = collectLabel();
            c.save();
        } else if (id == ID_HUMAN) {
            boolean on = c.autoMineHumanizedDelays == null || c.autoMineHumanizedDelays;
            c.autoMineHumanizedDelays = !on;
            humanButton.displayString = humanLabel();
            c.save();
        } else if (id == ID_JITTER) {
            boolean on = c.autoMineAimJitter == null || c.autoMineAimJitter;
            c.autoMineAimJitter = !on;
            jitterButton.displayString = jitterLabel();
            c.save();
        } else if (id == ID_STAFF) {
            boolean alert = c.autoMineStaffAlert == null || c.autoMineStaffAlert;
            boolean dc = c.autoMineStaffDisconnect != null && c.autoMineStaffDisconnect;
            if (alert && !dc) {
                c.autoMineStaffAlert = true;
                c.autoMineStaffDisconnect = true;
            } else if (alert && dc) {
                c.autoMineStaffAlert = false;
                c.autoMineStaffDisconnect = false;
            } else {
                c.autoMineStaffAlert = true;
                c.autoMineStaffDisconnect = false;
            }
            staffButton.displayString = staffLabel();
            c.save();
        } else if (id == ID_SCAFFOLD) {
            boolean on = c.autoMineSmartScaffold == null || c.autoMineSmartScaffold;
            c.autoMineSmartScaffold = !on;
            scaffoldButton.displayString = scaffoldLabel();
            c.save();
        } else if (id == ID_BACK) {
            this.mc.displayGuiScreen(new GuiAutoMineSettings());
        }
    }

    /** Round to one decimal so the stepper doesn't accumulate float drift. */
    private static double clampD(double v, double lo, double hi) {
        double r = Math.round(v * 10.0) / 10.0;
        return Math.max(lo, Math.min(hi, r));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 + TITLE_Y_OFF;
        drawCenteredString(this.fontRendererObj, "Auto Mine - Finjustering", cx, titleY, 0xFFD24B);
        drawCenteredString(this.fontRendererObj, "Ræk-vidde er begrænset til lovlig værdi.", cx, titleY + 14, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
