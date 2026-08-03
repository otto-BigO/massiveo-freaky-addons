package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Settings for the VK Stealer. Everything the addon reads from the config is
 * reachable here, so nothing is file-only.
 */
public class GuiVkStealer extends GuiScreen {

    private static final int ID_ENABLED = 0;
    private static final int ID_STEAL_ONLY = 1;
    private static final int ID_THRESHOLD_DOWN = 2;
    private static final int ID_THRESHOLD_UP = 3;
    private static final int ID_DELAY_DOWN = 4;
    private static final int ID_DELAY_UP = 5;
    private static final int ID_REACH_DOWN = 6;
    private static final int ID_REACH_UP = 7;
    private static final int ID_SILENT_AIM = 8;
    private static final int ID_SMOOTH_AIM = 9;
    private static final int ID_LINE_OF_SIGHT = 10;
    private static final int ID_UNKNOWN_HP = 11;
    private static final int ID_BACK = 12;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;
    private static final int ROW = 21;

    private GuiButton enabledBtn;
    private GuiButton stealOnlyBtn;
    private GuiButton silentAimBtn;
    private GuiButton smoothAimBtn;
    private GuiButton losBtn;
    private GuiButton unknownHpBtn;

    private NumericStepper thresholdStepper;
    private NumericStepper delayStepper;
    private NumericStepper reachStepper;

    private int thresholdY, delayY, reachY;

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

        this.buttonList.add(stealOnlyBtn = new StyledButton(ID_STEAL_ONLY, left, y, PANEL_W, BTN_H, stealOnlyLabel()));
        y += ROW;

        thresholdY = y;
        thresholdStepper = new NumericStepper(ID_THRESHOLD_DOWN, ID_THRESHOLD_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(thresholdStepper.getBtnDown());
        this.buttonList.add(thresholdStepper.getBtnUp());
        y += ROW;

        delayY = y;
        delayStepper = new NumericStepper(ID_DELAY_DOWN, ID_DELAY_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(delayStepper.getBtnDown());
        this.buttonList.add(delayStepper.getBtnUp());
        y += ROW;

        reachY = y;
        reachStepper = new NumericStepper(ID_REACH_DOWN, ID_REACH_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(reachStepper.getBtnDown());
        this.buttonList.add(reachStepper.getBtnUp());
        y += ROW + 3;

        this.buttonList.add(silentAimBtn = new StyledButton(ID_SILENT_AIM, left, y, PANEL_W, BTN_H, silentAimLabel()));
        y += ROW;

        this.buttonList.add(smoothAimBtn = new StyledButton(ID_SMOOTH_AIM, left, y, PANEL_W, BTN_H, smoothAimLabel()));
        y += ROW;

        this.buttonList.add(losBtn = new StyledButton(ID_LINE_OF_SIGHT, left, y, PANEL_W, BTN_H, losLabel()));
        y += ROW;

        this.buttonList.add(unknownHpBtn = new StyledButton(ID_UNKNOWN_HP, left, y, PANEL_W, BTN_H, unknownHpLabel()));
        y += ROW + 4;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));

        refreshLabels();
    }

    private String onOff(boolean b) {
        return b ? (Style.getAccentFormatting() + "[ TIL ]") : (EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String enabledLabel() {
        return "VK Stealer  " + onOff(cfg() != null && cfg().vkStealerEnabled);
    }

    private String stealOnlyLabel() {
        return "Kun sidste hit  " + onOff(cfg() != null && cfg().vkStealOnly);
    }

    private String silentAimLabel() {
        return "Skjult sigte  " + onOff(cfg() != null && cfg().vkSilentAim);
    }

    private String smoothAimLabel() {
        boolean silent = cfg() != null && cfg().vkSilentAim;
        if (silent) {
            return EnumChatFormatting.DARK_GRAY + "Blødt sigte (kun uden skjult)";
        }
        return "Blødt sigte  " + onOff(cfg().vkSmoothAim);
    }

    private String losLabel() {
        return "Kræv frit sigte  " + onOff(cfg() != null && cfg().vkRequireLineOfSight);
    }

    private String unknownHpLabel() {
        return "Slå til uden HP-info  " + onOff(cfg() != null && cfg().vkAttackWhenHealthUnknown);
    }

    private String thresholdLabel() {
        int pct = Math.round((cfg() != null ? cfg().vkHealthThreshold : 0.30f) * 100f);
        return "Slå til under: " + pct + "%";
    }

    private String delayLabel() {
        return "Forsinkelse: " + (cfg() != null ? cfg().vkAttackDelayMs : 100) + " ms";
    }

    private String reachLabel() {
        return String.format("Rækkevidde: %.1f", cfg() != null ? cfg().vkReach : 3.0f);
    }

    private void refreshLabels() {
        if (enabledBtn != null) enabledBtn.displayString = enabledLabel();
        if (stealOnlyBtn != null) stealOnlyBtn.displayString = stealOnlyLabel();
        if (silentAimBtn != null) silentAimBtn.displayString = silentAimLabel();
        if (smoothAimBtn != null) {
            smoothAimBtn.displayString = smoothAimLabel();
            smoothAimBtn.enabled = cfg() != null && !cfg().vkSilentAim;
        }
        if (losBtn != null) losBtn.displayString = losLabel();
        if (unknownHpBtn != null) unknownHpBtn.displayString = unknownHpLabel();
        // The threshold only does anything while steal mode is on.
        boolean steal = cfg() != null && cfg().vkStealOnly;
        if (thresholdStepper != null) {
            thresholdStepper.getBtnDown().enabled = steal;
            thresholdStepper.getBtnUp().enabled = steal;
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
                c.vkStealerEnabled = !c.vkStealerEnabled;
                break;
            case ID_STEAL_ONLY:
                c.vkStealOnly = !c.vkStealOnly;
                break;
            case ID_THRESHOLD_DOWN:
                c.vkHealthThreshold = Math.max(0.05f, c.vkHealthThreshold - 0.05f);
                break;
            case ID_THRESHOLD_UP:
                c.vkHealthThreshold = Math.min(1.0f, c.vkHealthThreshold + 0.05f);
                break;
            case ID_DELAY_DOWN:
                c.vkAttackDelayMs = Math.max(50, c.vkAttackDelayMs - 10);
                break;
            case ID_DELAY_UP:
                c.vkAttackDelayMs = Math.min(500, c.vkAttackDelayMs + 10);
                break;
            case ID_REACH_DOWN:
                c.vkReach = Math.max(1.0f, Math.round((c.vkReach - 0.1f) * 10f) / 10f);
                break;
            case ID_REACH_UP:
                c.vkReach = Math.min(3.0f, Math.round((c.vkReach + 0.1f) * 10f) / 10f);
                break;
            case ID_SILENT_AIM:
                c.vkSilentAim = !c.vkSilentAim;
                break;
            case ID_SMOOTH_AIM:
                c.vkSmoothAim = !c.vkSmoothAim;
                break;
            case ID_LINE_OF_SIGHT:
                c.vkRequireLineOfSight = !c.vkRequireLineOfSight;
                break;
            case ID_UNKNOWN_HP:
                c.vkAttackWhenHealthUnknown = !c.vkAttackWhenHealthUnknown;
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

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "VK Stealer",
                cx, cy - 138, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Tager sidste hit på vagter",
                cx, cy - 126, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (thresholdStepper != null) thresholdStepper.draw(this.mc, mouseX, mouseY, thresholdLabel());
        if (delayStepper != null) delayStepper.draw(this.mc, mouseX, mouseY, delayLabel());
        if (reachStepper != null) reachStepper.draw(this.mc, mouseX, mouseY, reachLabel());

        drawStatus(cx, cy);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    /** Live line showing the roster size and whatever is currently locked. */
    private void drawStatus(int cx, int cy) {
        String line;
        EntityPlayer t = VkStealer.currentTarget;
        if (t != null) {
            float hp = VkStealer.currentTargetHealth;
            VagtRoster.Rank rank = VagtRoster.rankOf(t.getName());
            String rankName = rank != null ? rank.label() : "Vagt";
            line = Style.getAccentFormatting() + "Låst: " + EnumChatFormatting.RESET
                    + t.getName() + EnumChatFormatting.GRAY + " (" + rankName + ")"
                    + (hp >= 0f ? EnumChatFormatting.RESET + "  " + Math.round(hp) + " HP" : "");
        } else {
            line = EnumChatFormatting.DARK_GRAY + "Ingen vagt i rækkevidde  ("
                    + VagtRoster.size() + " på listen)";
        }
        drawCenteredString(this.fontRendererObj, line, cx, cy + 128, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
