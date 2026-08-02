package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Clean, modern control panel for the Celle Scanner addon - Apple Motion Physics.
 */
public class GuiCelleMenu extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_NOTIFY = 1;
    private static final int ID_ESP = 2;

    private static final int ID_MIN_DOWN = 3;
    private static final int ID_MIN_UP = 4;
    private static final int ID_MAX_DOWN = 5;
    private static final int ID_MAX_UP = 6;

    private static final int ID_RELOAD = 7;
    private static final int ID_CLEAR = 8;
    private static final int ID_SPECIAL = 9;
    private static final int ID_SETTINGS = 10;
    private static final int ID_CLOSE = 11;

    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    private GuiButton toggleBtn;
    private GuiButton notifyBtn;
    private GuiButton espBtn;
    private GuiButton minLabelBtn;
    private GuiButton maxLabelBtn;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        this.buttonList.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int halfW = (PANEL_W - 4) / 2;

        int y = cy - 105;

        this.buttonList.add(toggleBtn = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += 24;
        this.buttonList.add(notifyBtn = new StyledButton(ID_NOTIFY, left, y, halfW, BTN_H, notifyLabel()));
        this.buttonList.add(espBtn = new StyledButton(ID_ESP, left + halfW + 4, y, halfW, BTN_H, espLabel()));
        y += 32;

        this.buttonList.add(new StyledButton(ID_MIN_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(minLabelBtn = new StyledButton(ID_MIN_DOWN - 100, left + 24, y, PANEL_W - 48, BTN_H, minLabel()));
        minLabelBtn.enabled = false;
        this.buttonList.add(new StyledButton(ID_MIN_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += 24;

        this.buttonList.add(new StyledButton(ID_MAX_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(maxLabelBtn = new StyledButton(ID_MAX_DOWN - 100, left + 24, y, PANEL_W - 48, BTN_H, maxLabel()));
        maxLabelBtn.enabled = false;
        this.buttonList.add(new StyledButton(ID_MAX_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += 32;

        this.buttonList.add(new StyledButton(ID_SPECIAL, left, y, halfW, BTN_H, "Special Celler"));
        this.buttonList.add(new StyledButton(ID_SETTINGS, left + halfW + 4, y, halfW, BTN_H, "Indstillinger"));
        y += 24;

        this.buttonList.add(new StyledButton(ID_RELOAD, left, y, halfW, BTN_H, "Genindlæs"));
        this.buttonList.add(new StyledButton(ID_CLEAR, left + halfW + 4, y, halfW, BTN_H, "Ryd Cache"));
        y += 28;

        this.buttonList.add(new StyledButton(ID_CLOSE, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private String toggleLabel() {
        boolean active = MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.enabled;
        return "Scanner Status: " + (active ? Style.getAccentFormatting() + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String notifyLabel() {
        boolean active = MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.notify;
        return "Alarmer: " + (active ? Style.getAccentFormatting() + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String espLabel() {
        boolean active = MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.espEnabled;
        return "3D ESP: " + (active ? Style.getAccentFormatting() + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    private String minLabel() {
        return "Min Timer: " + (MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.minHours : 0) + "t";
    }

    private String maxLabel() {
        return "Max Timer: " + (MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.maxHours : 0) + "t";
    }

    private static int step() {
        return isShiftKeyDown() ? 5 : 1;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleEnabled();
                toggleBtn.displayString = toggleLabel();
                break;
            case ID_NOTIFY:
                CelleActions.toggleNotify();
                notifyBtn.displayString = notifyLabel();
                break;
            case ID_ESP:
                CelleActions.toggleEsp();
                espBtn.displayString = espLabel();
                break;
            case ID_MIN_DOWN:
                CelleActions.adjustMinHours(-step());
                minLabelBtn.displayString = minLabel();
                break;
            case ID_MIN_UP:
                CelleActions.adjustMinHours(step());
                minLabelBtn.displayString = minLabel();
                break;
            case ID_MAX_DOWN:
                CelleActions.adjustMaxHours(-step());
                maxLabelBtn.displayString = maxLabel();
                break;
            case ID_MAX_UP:
                CelleActions.adjustMaxHours(step());
                maxLabelBtn.displayString = maxLabel();
                break;
            case ID_SPECIAL:
                CelleActions.openSpecialScreen();
                break;
            case ID_SETTINGS:
                CelleActions.openSettings();
                break;
            case ID_RELOAD:
                CelleActions.reloadConfig();
                this.initGui();
                break;
            case ID_CLEAR:
                CelleActions.clearCache();
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

        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        int titleY = cy - 134;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Celle Scanner", cx, titleY, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Hold Shift for +/- 5 timer", cx, titleY + 12, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
