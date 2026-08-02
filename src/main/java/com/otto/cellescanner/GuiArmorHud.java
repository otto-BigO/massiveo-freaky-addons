package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Control screen for the Armor HUD addon - Apple Motion Physics.
 */
public class GuiArmorHud extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_WARN_DOWN = 1;
    private static final int ID_WARN_UP = 2;
    private static final int ID_BACK = 3;

    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton toggleButton;
    private NumericStepper warnStepper;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        this.buttonList.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 24;

        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;

        warnStepper = new NumericStepper(ID_WARN_DOWN, ID_WARN_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(warnStepper.getBtnDown());
        this.buttonList.add(warnStepper.getBtnUp());
        y += BTN_H + 6;

        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private String toggleLabel() {
        boolean on = MassiveOsFreakyAddons.config.armorHudEnabled;
        return "Rustnings-HUD: " + (on ? Style.getAccentFormatting() + "[ TIL ]" : "\u00a77[ FRA ]");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleArmorHud();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_WARN_DOWN:
                CelleActions.adjustArmorHudWarn(-5);
                break;
            case ID_WARN_UP:
                CelleActions.adjustArmorHudWarn(5);
                break;
            case ID_BACK:
                CelleActions.openHub();
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 64;
        int accent = Style.getAccentColor();
        drawCenteredString(this.fontRendererObj, "\u00a7lRustnings-HUD", cx, titleY, accent);
        drawCenteredString(this.fontRendererObj, "Viser din rustnings holdbarhed på skærmen.", cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Tallet bliver rødt når en del er lav.", cx, titleY + 24, 0xAAAAAA);

        if (warnStepper != null) {
            warnStepper.draw(this.mc, mouseX, mouseY, "Advarsel under: " + MassiveOsFreakyAddons.config.armorHudWarnPercent + "%");
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
