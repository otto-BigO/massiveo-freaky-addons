package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * Live-previewable Theme & Appearance Customization screen.
 * Renders an interactive live preview card displaying live theme accents,
 * headers, status indicators, and card transparency in real-time.
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
    private static final int ID_BACK = 10;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;

    private NumericStepper alphaStepper;
    private GuiButton titleStyleBtn;
    private GuiButton soundStyleBtn;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int thirdW = (PANEL_W - 8) / 3;
        int halfW = (PANEL_W - 4) / 2;

        int y = cy - 105;

        // Row 1: Theme Presets Row 1 (Emerald, Cyan, Lilla)
        this.buttonList.add(new StyledButton(ID_PRESET_EMERALD, left, y, thirdW, BTN_H, "Emerald"));
        this.buttonList.add(new StyledButton(ID_PRESET_CYAN, left + thirdW + 4, y, thirdW, BTN_H, "Cyan"));
        this.buttonList.add(new StyledButton(ID_PRESET_PURPLE, left + (thirdW + 4) * 2, y, thirdW, BTN_H, "Lilla"));
        y += 22;

        // Row 2: Theme Presets Row 2 (Pink, Guld, Mørk)
        this.buttonList.add(new StyledButton(ID_PRESET_PINK, left, y, thirdW, BTN_H, "Pink"));
        this.buttonList.add(new StyledButton(ID_PRESET_GOLD, left + thirdW + 4, y, thirdW, BTN_H, "Guld"));
        this.buttonList.add(new StyledButton(ID_PRESET_STEALTH, left + (thirdW + 4) * 2, y, thirdW, BTN_H, "Mørk"));
        y += 26;

        // Row 3: Card Transparency Stepper using NumericStepper
        alphaStepper = new NumericStepper(ID_ALPHA_DOWN, ID_ALPHA_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(alphaStepper.getBtnDown());
        this.buttonList.add(alphaStepper.getBtnUp());
        y += 24;

        // Row 4: Title Effect & Countdown Chime Sound
        this.buttonList.add(titleStyleBtn = new StyledButton(ID_TITLE_STYLE, left, y, halfW, BTN_H, titleStyleLabel()));
        this.buttonList.add(soundStyleBtn = new StyledButton(ID_SOUND_STYLE, left + halfW + 4, y, halfW, BTN_H, soundStyleLabel()));
        y += 72; // Space for Live Preview Card

        // Row 5: Back to Hub
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private String alphaLabel() {
        int percent = Math.round((CelleScannerMod.config != null ? CelleScannerMod.config.themeBgAlpha : 0.65f) * 100f);
        return "Synlighed: " + percent + "%";
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
                break;
            case ID_ALPHA_UP:
                CelleScannerMod.config.themeBgAlpha = Math.min(0.95f, CelleScannerMod.config.themeBgAlpha + 0.05f);
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

        int titleY = cy - 128;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Tema & Udseende", cx, titleY, Style.getAccentColor());

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw NumericStepper label
        if (alphaStepper != null) {
            alphaStepper.draw(this.mc, mouseX, mouseY, alphaLabel());
        }

        // Live Theme Mini Preview Card
        drawLivePreviewCard(cx, cy - 31);
    }

    private void drawLivePreviewCard(int cx, int topY) {
        int w = 180;
        int h = 42;
        int x1 = cx - w / 2;
        int y1 = topY;
        int x2 = cx + w / 2;
        int y2 = topY + h;

        int accent = Style.getAccentColor();
        float alpha = CelleScannerMod.config != null ? CelleScannerMod.config.themeBgAlpha : 0.65f;
        int alphaInt = Math.max(0, Math.min(255, (int) (alpha * 255)));

        // Outer & Inner border with dynamic accent
        Style.roundedRect(x1, y1, x2, y2, 0xFF14151E);
        Style.roundedRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, (0x66 << 24) | (accent & 0xFFFFFF));
        Style.roundedRect(x1 + 2, y1 + 2, x2 - 2, y2 - 2, (alphaInt << 24) | 0x0A0A0F);

        // Sample text & active pill
        this.fontRendererObj.drawStringWithShadow("Live Tema-Forhåndsvisning", x1 + 8, y1 + 6, accent);
        this.fontRendererObj.drawStringWithShadow("A-12  (0h 14m)", x1 + 8, y1 + 22, 0xCCCCCC);

        String pill = EnumChatFormatting.GREEN + "[ TIL ]";
        this.fontRendererObj.drawStringWithShadow(pill, x2 - 8 - this.fontRendererObj.getStringWidth(pill), y1 + 22, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
