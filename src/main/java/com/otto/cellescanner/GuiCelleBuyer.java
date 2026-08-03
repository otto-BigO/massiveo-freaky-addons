package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Settings for the Celle Buyer. Everything the addon reads is reachable here, so
 * nothing is file-only.
 */
public class GuiCelleBuyer extends GuiScreen {

    private static final int ID_ENABLED = 0;
    private static final int ID_SILENT_AIM = 1;
    private static final int ID_EXTENDED_REACH = 2;
    private static final int ID_REACH_DOWN = 3;
    private static final int ID_REACH_UP = 4;
    private static final int ID_PRECLICK_DOWN = 5;
    private static final int ID_PRECLICK_UP = 6;
    private static final int ID_INTERVAL_DOWN = 7;
    private static final int ID_INTERVAL_UP = 8;
    private static final int ID_ARM_DOWN = 9;
    private static final int ID_ARM_UP = 10;
    private static final int ID_ONLY_CONFIRMED = 11;
    private static final int ID_BACK = 12;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;
    private static final int ROW = 21;

    private GuiButton enabledBtn;
    private GuiButton silentAimBtn;
    private GuiButton extendedReachBtn;
    private GuiButton onlyConfirmedBtn;

    private NumericStepper reachStepper;
    private NumericStepper preClickStepper;
    private NumericStepper intervalStepper;
    private NumericStepper armStepper;

    private int reachY, preClickY, intervalY, armY;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    private static CelleConfig cfg() {
        return MassiveOsFreakyAddons.config;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int y = cy - 108;

        this.buttonList.add(enabledBtn = new StyledButton(ID_ENABLED, left, y, PANEL_W, BTN_H, enabledLabel()));
        y += ROW + 3;

        this.buttonList.add(silentAimBtn = new StyledButton(ID_SILENT_AIM, left, y, PANEL_W, BTN_H, silentAimLabel()));
        y += ROW;

        this.buttonList.add(extendedReachBtn = new StyledButton(ID_EXTENDED_REACH, left, y, PANEL_W, BTN_H, extendedReachLabel()));
        y += ROW;

        reachY = y;
        reachStepper = new NumericStepper(ID_REACH_DOWN, ID_REACH_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(reachStepper.getBtnDown());
        this.buttonList.add(reachStepper.getBtnUp());
        y += ROW + 3;

        preClickY = y;
        preClickStepper = new NumericStepper(ID_PRECLICK_DOWN, ID_PRECLICK_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(preClickStepper.getBtnDown());
        this.buttonList.add(preClickStepper.getBtnUp());
        y += ROW;

        intervalY = y;
        intervalStepper = new NumericStepper(ID_INTERVAL_DOWN, ID_INTERVAL_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(intervalStepper.getBtnDown());
        this.buttonList.add(intervalStepper.getBtnUp());
        y += ROW;

        armY = y;
        armStepper = new NumericStepper(ID_ARM_DOWN, ID_ARM_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(armStepper.getBtnDown());
        this.buttonList.add(armStepper.getBtnUp());
        y += ROW;

        this.buttonList.add(onlyConfirmedBtn = new StyledButton(ID_ONLY_CONFIRMED, left, y, PANEL_W, BTN_H, onlyConfirmedLabel()));
        y += ROW + 4;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));

        refreshLabels();
    }

    private String onOff(boolean b) {
        return b ? (Style.getAccentFormatting() + "[ TIL ]") : (EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String enabledLabel() {
        return "Celle Buyer  " + onOff(cfg() != null && cfg().celleBuyerEnabled);
    }

    private String silentAimLabel() {
        return "Skjult sigte  " + onOff(cfg() != null && cfg().celleBuyerSilentAim);
    }

    private String extendedReachLabel() {
        return "Udvidet rækkevidde  " + onOff(cfg() != null && cfg().celleBuyerExtendedReach);
    }

    private String onlyConfirmedLabel() {
        return "Kun bekræftet timer  " + onOff(cfg() != null && cfg().celleBuyerOnlyConfirmed);
    }

    private String reachLabel() {
        if (cfg() == null) {
            return "Rækkevidde";
        }
        if (!cfg().celleBuyerExtendedReach) {
            return EnumChatFormatting.DARK_GRAY + String.format("Rækkevidde: %.1f (normal)",
                    CelleBuyer.VANILLA_REACH);
        }
        return String.format("Rækkevidde: %.1f", cfg().celleBuyerReach);
    }

    private String preClickLabel() {
        return "Forudklik: " + (cfg() != null ? cfg().celleBuyerPreClickMs : 250) + " ms";
    }

    private String intervalLabel() {
        return "Klik-interval: " + (cfg() != null ? cfg().celleBuyerClickIntervalMs : 100) + " ms";
    }

    private String armLabel() {
        return "Klargør: " + (cfg() != null ? cfg().celleBuyerArmSeconds : 60) + " s før";
    }

    private void refreshLabels() {
        if (enabledBtn != null) enabledBtn.displayString = enabledLabel();
        if (silentAimBtn != null) silentAimBtn.displayString = silentAimLabel();
        if (extendedReachBtn != null) extendedReachBtn.displayString = extendedReachLabel();
        if (onlyConfirmedBtn != null) onlyConfirmedBtn.displayString = onlyConfirmedLabel();
        // The reach value only does anything once extended reach is unlocked.
        boolean ext = cfg() != null && cfg().celleBuyerExtendedReach;
        if (reachStepper != null) {
            reachStepper.getBtnDown().enabled = ext;
            reachStepper.getBtnUp().enabled = ext;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = cfg();
        if (c == null) {
            return;
        }
        switch (button.id) {
            case ID_ENABLED:
                c.celleBuyerEnabled = !c.celleBuyerEnabled;
                break;
            case ID_SILENT_AIM:
                c.celleBuyerSilentAim = !c.celleBuyerSilentAim;
                break;
            case ID_EXTENDED_REACH:
                c.celleBuyerExtendedReach = !c.celleBuyerExtendedReach;
                break;
            case ID_REACH_DOWN:
                c.celleBuyerReach = Math.max(CelleBuyer.VANILLA_REACH,
                        Math.round((c.celleBuyerReach - 0.1f) * 10f) / 10f);
                break;
            case ID_REACH_UP:
                c.celleBuyerReach = Math.min(CelleBuyer.MAX_REACH,
                        Math.round((c.celleBuyerReach + 0.1f) * 10f) / 10f);
                break;
            case ID_PRECLICK_DOWN:
                c.celleBuyerPreClickMs = Math.max(0, c.celleBuyerPreClickMs - 50);
                break;
            case ID_PRECLICK_UP:
                c.celleBuyerPreClickMs = Math.min(1000, c.celleBuyerPreClickMs + 50);
                break;
            case ID_INTERVAL_DOWN:
                c.celleBuyerClickIntervalMs = Math.max(50, c.celleBuyerClickIntervalMs - 10);
                break;
            case ID_INTERVAL_UP:
                c.celleBuyerClickIntervalMs = Math.min(500, c.celleBuyerClickIntervalMs + 10);
                break;
            case ID_ARM_DOWN:
                c.celleBuyerArmSeconds = Math.max(5, c.celleBuyerArmSeconds - 5);
                break;
            case ID_ARM_UP:
                c.celleBuyerArmSeconds = Math.min(600, c.celleBuyerArmSeconds + 5);
                break;
            case ID_ONLY_CONFIRMED:
                c.celleBuyerOnlyConfirmed = !c.celleBuyerOnlyConfirmed;
                break;
            case ID_BACK:
                CelleActions.openHub();
                return;
            default:
                return;
        }
        c.save();
        refreshLabels();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        int halfW = Style.cardHalfWidth(this.width);
        int halfH = Style.cardHalfHeight(this.height);
        int ccx = this.width / 2;
        int ccy = this.height / 2;
        AmbientParticleEngine.INSTANCE.renderBehind(this.width, this.height,
                ccx - halfW, ccy - halfH, ccx + halfW, ccy + halfH, mouseX, mouseY);

        float breathe = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1400.0);
        Style.cardGlow(this.width, this.height, pVal * (0.45f + 0.55f * breathe));

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Celle Buyer",
                cx, cy - 138, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Køber cellen i det sekund den bliver ledig",
                cx, cy - 126, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (reachStepper != null) reachStepper.draw(this.mc, mouseX, mouseY, reachLabel());
        if (preClickStepper != null) preClickStepper.draw(this.mc, mouseX, mouseY, preClickLabel());
        if (intervalStepper != null) intervalStepper.draw(this.mc, mouseX, mouseY, intervalLabel());
        if (armStepper != null) armStepper.draw(this.mc, mouseX, mouseY, armLabel());

        drawStatus(cx, cy);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    /** Live line showing whatever is armed right now. */
    private void drawStatus(int cx, int cy) {
        String line;
        String id = CelleBuyer.currentTargetId;
        if (id == null) {
            line = EnumChatFormatting.DARK_GRAY + "Ingen celle i rækkevidde";
        } else if (CelleBuyer.currentTargetBuyable) {
            line = Style.getAccentFormatting() + "KØBER: " + EnumChatFormatting.RESET + id;
        } else {
            long ms = Math.max(0L, CelleBuyer.currentTargetFreeInMs);
            line = Style.getAccentFormatting() + "Klar: " + EnumChatFormatting.RESET + id
                    + EnumChatFormatting.GRAY + "  ledig om " + formatShort(ms)
                    + String.format("  (%.1f blokke)", CelleBuyer.currentTargetDistance);
        }
        drawCenteredString(this.fontRendererObj, line, cx, cy + 128, 0xFFFFFF);
    }

    private static String formatShort(long ms) {
        if (ms < 10000L) {
            return String.format("%.1fs", ms / 1000.0);
        }
        long s = ms / 1000L;
        if (s < 60L) {
            return s + "s";
        }
        return (s / 60L) + "m " + (s % 60L) + "s";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
