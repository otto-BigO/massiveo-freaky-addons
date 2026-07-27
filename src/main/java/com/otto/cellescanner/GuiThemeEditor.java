package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.io.IOException;

/**
 * Live-previewable Theme & Appearance Customization screen with interactive HSV Color Picker.
 * Features 6 instant presets, card transparency controls, alert sound selector, and a full
 * interactive 2D HSV Color Picker for custom color selection!
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

    // HSV Color Picker state
    private float hue = 0.45f;
    private float sat = 1.0f;
    private float val = 1.0f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int thirdW = (PANEL_W - 8) / 3;
        int halfW = (PANEL_W - 4) / 2;

        // Initialize HSV state from config color
        int currentColor = CelleScannerMod.config != null ? CelleScannerMod.config.themeAccentColor : 0x00FF88;
        int r = (currentColor >> 16) & 0xFF;
        int g = (currentColor >> 8) & 0xFF;
        int b = currentColor & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];

        int y = cy - 128;

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

        // Space reserved for Interactive HSV Color Picker (y + 54px)
        y += 54;

        // Row 3: Card Transparency Stepper using NumericStepper
        alphaStepper = new NumericStepper(ID_ALPHA_DOWN, ID_ALPHA_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(alphaStepper.getBtnDown());
        this.buttonList.add(alphaStepper.getBtnUp());
        y += 24;

        // Row 4: Title Effect & Countdown Chime Sound
        this.buttonList.add(titleStyleBtn = new StyledButton(ID_TITLE_STYLE, left, y, halfW, BTN_H, titleStyleLabel()));
        this.buttonList.add(soundStyleBtn = new StyledButton(ID_SOUND_STYLE, left + halfW + 4, y, halfW, BTN_H, soundStyleLabel()));
        y += 50; // Space for Live Preview Card

        // Row 5: Back to Hub
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private void updatePickerFromPreset(int hexColor) {
        if (CelleScannerMod.config != null) {
            CelleScannerMod.config.themeAccentColor = hexColor;
            CelleScannerMod.config.save();
        }
        int r = (hexColor >> 16) & 0xFF;
        int g = (hexColor >> 8) & 0xFF;
        int b = hexColor & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    private void applyColour() {
        int argb = Color.HSBtoRGB(hue, sat, val) | 0xFF000000;
        if (CelleScannerMod.config != null) {
            CelleScannerMod.config.themeAccentColor = argb;
            CelleScannerMod.config.save();
        }
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
                updatePickerFromPreset(0x00FF88);
                break;
            case ID_PRESET_CYAN:
                updatePickerFromPreset(0x00E5FF);
                break;
            case ID_PRESET_PURPLE:
                updatePickerFromPreset(0xB026FF);
                break;
            case ID_PRESET_PINK:
                updatePickerFromPreset(0xFF2A85);
                break;
            case ID_PRESET_GOLD:
                updatePickerFromPreset(0xFF9900);
                break;
            case ID_PRESET_STEALTH:
                updatePickerFromPreset(0x505868);
                break;
            case ID_ALPHA_DOWN:
                CelleScannerMod.config.themeBgAlpha = Math.max(0.20f, CelleScannerMod.config.themeBgAlpha - 0.05f);
                CelleScannerMod.config.save();
                break;
            case ID_ALPHA_UP:
                CelleScannerMod.config.themeBgAlpha = Math.min(0.95f, CelleScannerMod.config.themeBgAlpha + 0.05f);
                CelleScannerMod.config.save();
                break;
            case ID_TITLE_STYLE:
                CelleScannerMod.config.themeTitleStyle = (CelleScannerMod.config.themeTitleStyle + 1) % 3;
                titleStyleBtn.displayString = titleStyleLabel();
                CelleScannerMod.config.save();
                break;
            case ID_SOUND_STYLE:
                String s = CelleScannerMod.config.alertSound;
                if (s.contains("pling")) CelleScannerMod.config.alertSound = "random.levelup";
                else if (s.contains("levelup")) CelleScannerMod.config.alertSound = "random.orb";
                else if (s.contains("orb")) CelleScannerMod.config.alertSound = "random.click";
                else CelleScannerMod.config.alertSound = "note.pling";
                soundStyleBtn.displayString = soundStyleLabel();
                CelleScannerMod.config.save();
                break;
            case ID_BACK:
                CelleActions.openHub();
                return;
            default:
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            int left = cx - PANEL_W / 2;

            // Hue Bar bounds: left, cy - 78, width = PANEL_W, height = 10
            int hueX = left;
            int hueY = cy - 78;
            int hueW = PANEL_W;
            int hueH = 10;

            if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                draggingHue = true;
                updateHueFromMouse(mouseX, hueX, hueW);
                return;
            }

            // SV Box bounds: left, cy - 64, width = PANEL_W, height = 34
            int svX = left;
            int svY = cy - 64;
            int svW = PANEL_W;
            int svH = 34;

            if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                draggingSV = true;
                updateSVFromMouse(mouseX, mouseY, svX, svY, svW, svH);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            draggingHue = false;
            draggingSV = false;
        }
    }

    private void updateHueFromMouse(int mouseX, int hueX, int hueW) {
        float sx = (float) (mouseX - hueX) / (float) hueW;
        if (sx < 0f) sx = 0f;
        if (sx > 1f) sx = 1f;
        this.hue = sx;
        applyColour();
    }

    private void updateSVFromMouse(int mouseX, int mouseY, int svX, int svY, int svW, int svH) {
        float sx = (float) (mouseX - svX) / (float) svW;
        float sy = (float) (mouseY - svY) / (float) svH;
        if (sx < 0f) sx = 0f; if (sx > 1f) sx = 1f;
        if (sy < 0f) sy = 0f; if (sy > 1f) sy = 1f;
        this.sat = sx;
        this.val = 1f - sy;
        applyColour();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;

        int titleY = cy - 142;
        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "Tema & Udseende", cx, titleY, Style.getAccentColor());

        // Handle active dragging mouse events
        if (Mouse.isButtonDown(0)) {
            int hueX = left;
            int hueY = cy - 78;
            int hueW = PANEL_W;
            if (draggingHue) {
                updateHueFromMouse(mouseX, hueX, hueW);
            }

            int svX = left;
            int svY = cy - 64;
            int svW = PANEL_W;
            int svH = 34;
            if (draggingSV) {
                updateSVFromMouse(mouseX, mouseY, svX, svY, svW, svH);
            }
        } else {
            draggingHue = false;
            draggingSV = false;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Render Interactive Hue Bar Spectrum
        int hueX = left;
        int hueY = cy - 78;
        int hueW = PANEL_W;
        int hueH = 10;
        drawHueBar(hueX, hueY, hueW, hueH);

        // Draw Hue indicator cursor line
        int cursorHueX = hueX + (int) (this.hue * hueW);
        drawRect(cursorHueX - 1, hueY - 1, cursorHueX + 1, hueY + hueH + 1, 0xFFFFFFFF);

        // Render Interactive SV Box Gradient
        int svX = left;
        int svY = cy - 64;
        int svW = PANEL_W;
        int svH = 34;
        drawSVBox(svX, svY, svW, svH, this.hue);

        // Draw SV indicator cursor dot
        int cursorSvX = svX + (int) (this.sat * svW);
        int cursorSvY = svY + (int) ((1f - this.val) * svH);
        Style.roundedRect(cursorSvX - 2, cursorSvY - 2, cursorSvX + 2, cursorSvY + 2, 0xFFFFFFFF);
        Style.roundedRect(cursorSvX - 1, cursorSvY - 1, cursorSvX + 1, cursorSvY + 1, Style.getAccentColor());

        // Draw NumericStepper label
        if (alphaStepper != null) {
            alphaStepper.draw(this.mc, mouseX, mouseY, alphaLabel());
        }

        // Live Theme Mini Preview Card
        drawLivePreviewCard(cx, cy + 24);
    }

    private void drawHueBar(int x, int y, int width, int height) {
        Style.roundedRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF14151E);
        for (int i = 0; i < width; i++) {
            float h = (float) i / (float) width;
            int rgb = Color.HSBtoRGB(h, 1.0f, 1.0f) | 0xFF000000;
            drawRect(x + i, y, x + i + 1, y + height, rgb);
        }
    }

    private void drawSVBox(int x, int y, int width, int height, float currentHue) {
        Style.roundedRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF14151E);
        int stepX = Math.max(1, width / 40);
        int stepY = Math.max(1, height / 16);

        for (int px = 0; px < width; px += stepX) {
            float s = (float) px / (float) width;
            for (int py = 0; py < height; py += stepY) {
                float v = 1.0f - ((float) py / (float) height);
                int rgb = Color.HSBtoRGB(currentHue, s, v) | 0xFF000000;
                drawRect(x + px, y + py, x + px + stepX, y + py + stepY, rgb);
            }
        }
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

        String pill = Style.getAccentFormatting() + "[ TIL ]";
        this.fontRendererObj.drawStringWithShadow(pill, x2 - 8 - this.fontRendererObj.getStringWidth(pill), y1 + 22, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
