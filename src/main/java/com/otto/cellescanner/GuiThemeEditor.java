package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Clean, customizable Theme & Personalization Editor screen.
 * Allows each player to customize UI theme presets, card transparency,
 * title animation effects, alert sound chimes, and Auto Armour settings.
 */
public class GuiThemeEditor extends GuiScreen {

    private static final int ID_PRESET_EMERALD = 0;
    private static final int ID_PRESET_CYAN = 1;
    private static final int ID_PRESET_PURPLE = 2;
    private static final int ID_PRESET_PINK = 3;
    private static final int ID_PRESET_GOLD = 4;
    private static final int ID_PRESET_STEALTH = 5;

    private static final int ID_ALPHA_DOWN = 6;
    private static final int ID_ALPHA_UP = 7;
    private static final int ID_TITLE_STYLE = 8;
    private static final int ID_SOUND_STYLE = 9;
    private static final int ID_AUTO_ARMOR = 10;
    private static final int ID_BACK = 11;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;

    private GuiButton alphaLabelBtn;
    private GuiButton titleStyleBtn;
    private GuiButton soundStyleBtn;
    private GuiButton autoArmorBtn;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int thirdW = (PANEL_W - 8) / 3;
        int halfW = (PANEL_W - 4) / 2;

        int y = cy - 110;

        // Row 1: Preset Themes Group A
        this.buttonList.add(new StyledButton(ID_PRESET_EMERALD, left, y, thirdW, BTN_H, "Emerald"));
        this.buttonList.add(new StyledButton(ID_PRESET_CYAN, left + thirdW + 4, y, thirdW, BTN_H, "Cyan"));
        this.buttonList.add(new StyledButton(ID_PRESET_PURPLE, left + (thirdW + 4) * 2, y, thirdW, BTN_H, "Lilla"));
        y += 22;

        // Row 2: Preset Themes Group B
        this.buttonList.add(new StyledButton(ID_PRESET_PINK, left, y, thirdW, BTN_H, "Pink"));
        this.buttonList.add(new StyledButton(ID_PRESET_GOLD, left + thirdW + 4, y, thirdW, BTN_H, "Guld"));
        this.buttonList.add(new StyledButton(ID_PRESET_STEALTH, left + (thirdW + 4) * 2, y, thirdW, BTN_H, "Mørk"));
        y += 26;

        // Row 3: Card Transparency Stepper
        this.buttonList.add(new StyledButton(ID_ALPHA_DOWN, left, y, 22, BTN_H, "-"));
        this.buttonList.add(alphaLabelBtn = new StyledButton(ID_ALPHA_DOWN - 100, left + 24, y, PANEL_W - 48, BTN_H, alphaLabel()));
        alphaLabelBtn.enabled = false;
        this.buttonList.add(new StyledButton(ID_ALPHA_UP, left + PANEL_W - 22, y, 22, BTN_H, "+"));
        y += 24;

        // Row 4: Title Effect & Sound Selector
        this.buttonList.add(titleStyleBtn = new StyledButton(ID_TITLE_STYLE, left, y, halfW, BTN_H, titleStyleLabel()));
        this.buttonList.add(soundStyleBtn = new StyledButton(ID_SOUND_STYLE, left + halfW + 4, y, halfW, BTN_H, soundStyleLabel()));
        y += 24;

        // Row 5: Auto Armour Quick Equip
        this.buttonList.add(autoArmorBtn = new StyledButton(ID_AUTO_ARMOR, left, y, PANEL_W, BTN_H, autoArmorLabel()));
        y += 28;

        // Row 6: Back to Hub
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private String alphaLabel() {
        int percent = Math.round((CelleScannerMod.config != null ? CelleScannerMod.config.themeBgAlpha : 0.65f) * 100f);
        return "Kort-Synlighed: " + percent + "%";
    }

    private String titleStyleLabel() {
        int s = CelleScannerMod.config != null ? CelleScannerMod.config.themeTitleStyle : 0;
        String name = s == 0 ? "Regnbue" : (s == 1 ? "Pulserende" : "Statisk");
        return "Titel: " + name;
    }

    private String soundStyleLabel() {
        String s = CelleScannerMod.config != null ? CelleScannerMod.config.alertSound : "note.pling";
        String name = s.contains("pling") ? "Pling" : (s.contains("levelup") ? "Level Up" : (s.contains("orb") ? "Orb" : "Klik"));
        return "Lyd: " + name;
    }

    private String autoArmorLabel() {
        boolean active = CelleScannerMod.config != null && CelleScannerMod.config.autoArmorEnabled;
        return "Auto Armour Hotkey: " + (active ? EnumChatFormatting.GREEN + "[ TIL ]" : EnumChatFormatting.DARK_GRAY + "[ FRA ]");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_PRESET_EMERALD:
                CelleScannerMod.config.themeAccentColor = 0x00FF88;
                break;
            case ID_PRESET_CYAN:
                CelleScannerMod.config.themeAccentColor = 0x00E5FF;
                break;
            case ID_PRESET_PURPLE:
                CelleScannerMod.config.themeAccentColor = 0xB026FF;
                break;
            case ID_PRESET_PINK:
                CelleScannerMod.config.themeAccentColor = 0xFF2A85;
                break;
            case ID_PRESET_GOLD:
                CelleScannerMod.config.themeAccentColor = 0xFF9900;
                break;
            case ID_PRESET_STEALTH:
                CelleScannerMod.config.themeAccentColor = 0x505868;
                break;
            case ID_ALPHA_DOWN:
                CelleScannerMod.config.themeBgAlpha = Math.max(0.20f, CelleScannerMod.config.themeBgAlpha - 0.05f);
                alphaLabelBtn.displayString = alphaLabel();
                break;
            case ID_ALPHA_UP:
                CelleScannerMod.config.themeBgAlpha = Math.min(0.95f, CelleScannerMod.config.themeBgAlpha + 0.05f);
                alphaLabelBtn.displayString = alphaLabel();
                break;
            case ID_TITLE_STYLE:
                CelleScannerMod.config.themeTitleStyle = (CelleScannerMod.config.themeTitleStyle + 1) % 3;
                titleStyleBtn.displayString = titleStyleLabel();
                break;
            case ID_SOUND_STYLE:
                String s = CelleScannerMod.config.alertSound;
                if (s.contains("pling")) CelleScannerMod.config.alertSound = "random.levelup";
                else if (s.contains("levelup")) CelleScannerMod.config.alertSound = "random.orb";
                else if (s.contains("orb")) CelleScannerMod.config.alertSound = "random.click";
                else CelleScannerMod.config.alertSound = "note.pling";
                soundStyleBtn.displayString = soundStyleLabel();
                break;
            case ID_AUTO_ARMOR:
                CelleScannerMod.config.autoArmorEnabled = !CelleScannerMod.config.autoArmorEnabled;
                autoArmorBtn.displayString = autoArmorLabel();
                break;
            case ID_BACK:
                CelleActions.openHub();
                return;
            default:
                break;
        }

        CelleScannerMod.config.save();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        int titleY = cy - 134;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Tema & Udseende", cx, titleY, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.GRAY + "Tilpas farver, effekter og lyd", cx, titleY + 12, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
