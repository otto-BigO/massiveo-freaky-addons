package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Settings for the nametag ESP.
 *
 * The two that matter are the scale divisor, which decides how fast a name
 * grows as it gets further away, and the distance limit, which stops a busy
 * area turning into a wall of overlapping text.
 */
public class GuiNameEsp extends GuiScreen {

    private static final int ID_ENABLED = 0;
    private static final int ID_THROUGH = 1;
    private static final int ID_COLOR = 2;
    private static final int ID_ONLY_KNOWN = 3;
    private static final int ID_SCALE_DOWN = 4;
    private static final int ID_SCALE_UP = 5;
    private static final int ID_DIST_DOWN = 6;
    private static final int ID_DIST_UP = 7;
    private static final int ID_BACK = 8;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;
    private static final int ROW = 21;

    private GuiButton enabledBtn, throughBtn, colorBtn, onlyKnownBtn;
    private NumericStepper scaleStepper, distStepper;

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
        int left = cx - PANEL_W / 2;
        int y = this.height / 2 - 84;

        this.buttonList.add(enabledBtn = new StyledButton(ID_ENABLED, left, y, PANEL_W, BTN_H, ""));
        y += ROW + 4;

        this.buttonList.add(throughBtn = new StyledButton(ID_THROUGH, left, y, PANEL_W, BTN_H, ""));
        y += ROW;

        this.buttonList.add(colorBtn = new StyledButton(ID_COLOR, left, y, PANEL_W, BTN_H, ""));
        y += ROW;

        this.buttonList.add(onlyKnownBtn = new StyledButton(ID_ONLY_KNOWN, left, y, PANEL_W, BTN_H, ""));
        y += ROW + 4;

        scaleStepper = new NumericStepper(ID_SCALE_DOWN, ID_SCALE_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(scaleStepper.getBtnDown());
        this.buttonList.add(scaleStepper.getBtnUp());
        y += ROW;

        distStepper = new NumericStepper(ID_DIST_DOWN, ID_DIST_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(distStepper.getBtnDown());
        this.buttonList.add(distStepper.getBtnUp());
        y += ROW + 6;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));

        refreshLabels();
    }

    private String onOff(boolean b) {
        return b ? (Style.getAccentFormatting() + "[ TIL ]") : (EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private void refreshLabels() {
        CelleConfig c = cfg();
        if (c == null) return;
        enabledBtn.displayString = "Navne ESP  " + onOff(c.playerEspEnabled);
        throughBtn.displayString = "Gennem vægge  " + onOff(Boolean.TRUE.equals(c.nameEspThroughWalls));
        colorBtn.displayString = "Farve efter rolle  " + onOff(Boolean.TRUE.equals(c.nameEspColorByRelation));
        onlyKnownBtn.displayString = "Kun bande og vagter  " + onOff(Boolean.TRUE.equals(c.nameEspOnlyKnown));
    }

    /** Smaller divisor means the name grows faster with distance. */
    private String scaleLabel() {
        CelleConfig c = cfg();
        int v = c == null ? 8 : c.nameEspScaleDivisor;
        return "Vokser med afstand: " + (v <= 4 ? "hurtigt" : v >= 20 ? "langsomt" : "middel") + " (" + v + ")";
    }

    private String distLabel() {
        CelleConfig c = cfg();
        int v = c == null ? 0 : c.nameEspMaxDistance;
        return "Maks afstand: " + (v == 0 ? "ingen grænse" : v + " blokke");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = cfg();
        if (c == null) return;
        switch (button.id) {
            case ID_ENABLED:
                c.playerEspEnabled = !c.playerEspEnabled;
                break;
            case ID_THROUGH:
                c.nameEspThroughWalls = Boolean.TRUE.equals(c.nameEspThroughWalls) ? Boolean.FALSE : Boolean.TRUE;
                break;
            case ID_COLOR:
                c.nameEspColorByRelation = Boolean.TRUE.equals(c.nameEspColorByRelation) ? Boolean.FALSE : Boolean.TRUE;
                break;
            case ID_ONLY_KNOWN:
                c.nameEspOnlyKnown = Boolean.TRUE.equals(c.nameEspOnlyKnown) ? Boolean.FALSE : Boolean.TRUE;
                break;
            case ID_SCALE_DOWN:
                c.nameEspScaleDivisor = Math.max(2, c.nameEspScaleDivisor - 2);
                break;
            case ID_SCALE_UP:
                c.nameEspScaleDivisor = Math.min(40, c.nameEspScaleDivisor + 2);
                break;
            case ID_DIST_DOWN:
                c.nameEspMaxDistance = Math.max(0, c.nameEspMaxDistance - 16);
                break;
            case ID_DIST_UP:
                c.nameEspMaxDistance = Math.min(256, c.nameEspMaxDistance + 16);
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

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Navne ESP",
                cx, cy - 128, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Navneskilte gennem vægge, læsbare på afstand",
                cx, cy - 116, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (scaleStepper != null) scaleStepper.draw(this.mc, mouseX, mouseY, scaleLabel());
        if (distStepper != null) distStepper.draw(this.mc, mouseX, mouseY, distLabel());

        // The palette is shared with Bande ESP, so say where to change it rather
        // than offering a second set of colours that could disagree.
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.DARK_GRAY + "Farverne deles med Bande ESP",
                cx, cy + 92, 0x888888);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
