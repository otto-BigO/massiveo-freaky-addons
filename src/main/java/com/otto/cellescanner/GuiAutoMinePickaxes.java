package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Which pickaxes Auto Mine is allowed to use. This was fixed in code before:
 * iron, gold and diamond in, wood and stone out.
 *
 * A pickaxe the server has made itself is always allowed, whatever is switched
 * on here, since it is usually the good one and there is no sensible way to
 * guess which of the five it counts as.
 */
public class GuiAutoMinePickaxes extends GuiScreen {

    private static final int ID_WOODEN = 0;
    private static final int ID_STONE = 1;
    private static final int ID_IRON = 2;
    private static final int ID_GOLDEN = 3;
    private static final int ID_DIAMOND = 4;
    private static final int ID_DUR_DOWN = 5;
    private static final int ID_DUR_UP = 6;
    private static final int ID_BACK = 7;

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    private GuiButton woodenBtn, stoneBtn, ironBtn, goldenBtn, diamondBtn;
    private NumericStepper durStepper;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    private static CelleConfig cfg() {
        return MassiveOsFreakyAddons.config;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 78;

        this.buttonList.add(woodenBtn = new StyledButton(ID_WOODEN, left, y, PANEL_W, BTN_H, ""));
        y += BTN_H + GAP;
        this.buttonList.add(stoneBtn = new StyledButton(ID_STONE, left, y, PANEL_W, BTN_H, ""));
        y += BTN_H + GAP;
        this.buttonList.add(ironBtn = new StyledButton(ID_IRON, left, y, PANEL_W, BTN_H, ""));
        y += BTN_H + GAP;
        this.buttonList.add(goldenBtn = new StyledButton(ID_GOLDEN, left, y, PANEL_W, BTN_H, ""));
        y += BTN_H + GAP;
        this.buttonList.add(diamondBtn = new StyledButton(ID_DIAMOND, left, y, PANEL_W, BTN_H, ""));
        y += BTN_H + GAP + 8;

        durStepper = new NumericStepper(ID_DUR_DOWN, ID_DUR_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(durStepper.getBtnDown());
        this.buttonList.add(durStepper.getBtnUp());
        y += BTN_H + GAP + 8;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));

        refreshLabels();
    }

    private String label(String name, Boolean on) {
        return name + "  " + (Boolean.TRUE.equals(on)
                ? (Style.getAccentFormatting() + "[ TIL ]")
                : (EnumChatFormatting.DARK_GRAY + "[ FRA ]"));
    }

    private void refreshLabels() {
        CelleConfig c = cfg();
        if (c == null) return;
        woodenBtn.displayString = label("Træ", c.autoMineUseWooden);
        stoneBtn.displayString = label("Sten", c.autoMineUseStone);
        ironBtn.displayString = label("Jern", c.autoMineUseIron);
        goldenBtn.displayString = label("Guld", c.autoMineUseGolden);
        diamondBtn.displayString = label("Diamant", c.autoMineUseDiamond);
    }

    private static int pct(CelleConfig c) {
        return c.autoMinePickaxeMinPercent == null ? 0 : c.autoMinePickaxeMinPercent.intValue();
    }

    /**
     * Reads as a share of each pickaxe's own durability, so it means the same
     * thing for a gold pickaxe with 33 points as for a diamond one with 1562.
     */
    private String durLabel() {
        CelleConfig c = cfg();
        int v = c == null ? 0 : pct(c);
        return v == 0 ? "Skift hakke ved: brug til den knækker"
                : "Skift hakke ved: " + v + "% tilbage";
    }

    private static Boolean flip(Boolean b) {
        return Boolean.TRUE.equals(b) ? Boolean.FALSE : Boolean.TRUE;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        CelleConfig c = cfg();
        if (c == null) return;
        switch (button.id) {
            case ID_WOODEN:  c.autoMineUseWooden = flip(c.autoMineUseWooden); break;
            case ID_STONE:   c.autoMineUseStone = flip(c.autoMineUseStone); break;
            case ID_IRON:    c.autoMineUseIron = flip(c.autoMineUseIron); break;
            case ID_GOLDEN:  c.autoMineUseGolden = flip(c.autoMineUseGolden); break;
            case ID_DIAMOND: c.autoMineUseDiamond = flip(c.autoMineUseDiamond); break;
            case ID_DUR_DOWN:
                c.autoMinePickaxeMinPercent = Integer.valueOf(Math.max(0, pct(c) - 5));
                break;
            case ID_DUR_UP:
                c.autoMinePickaxeMinPercent = Integer.valueOf(Math.min(90, pct(c) + 5));
                break;
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiAutoMineSettings());
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

        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Hakker",
                cx, cy - 128, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj,
                EnumChatFormatting.GRAY + "Hvad botten må mine med",
                cx, cy - 116, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (durStepper != null) durStepper.draw(this.mc, mouseX, mouseY, durLabel());

        // Turning everything off leaves the bot with nothing to hold, and it
        // would just stand there rather than say why.
        if (AutoMine.noPickaxeTypeAllowed()) {
            drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.RED + "Ingen hakker valgt, botten kan ikke mine",
                    cx, cy + 78, 0xFF5555);
        } else {
            drawCenteredString(this.fontRendererObj,
                    EnumChatFormatting.DARK_GRAY + "Serverens egne hakker bruges altid",
                    cx, cy + 78, 0x888888);
        }

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
