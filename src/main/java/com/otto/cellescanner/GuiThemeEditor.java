package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;

/**
 * Live-previewable Theme & Appearance Customization screen with interactive HSV Color Picker.
 * Features 6 instant presets, card transparency controls, interactive Opacity Slider, alert sound selector,
 * and a mouse-draggable Live Theme Preview Card!
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
    private static final int ID_HUD_EDITOR = 10;
    private static final int ID_BACK = 11;

    private static final int PANEL_W = 230;
    private static final int BTN_H = 18;

    /**
     * The opacity range, shared by the slider and the stepper. They used to disagree
     * (the slider went to 1.00, the stepper stopped at 0.95) and the slider clamped a
     * raw 0-1 fraction, which left the first fifth of the bar doing nothing.
     */
    private static final float ALPHA_MIN = 0.20f;
    private static final float ALPHA_MAX = 1.00f;

    private static final int PREVIEW_W = 180;
    private static final int PREVIEW_H = 42;

    /**
     * Picker geometry, laid out once in initGui. These used to be re-derived from
     * magic offsets in three separate places (hit testing, drag handling and
     * drawing), so a change to one of them moved the visuals away from the region
     * that actually responded to the mouse.
     */
    private int hueX, hueY, hueW, hueH;
    private int svX, svY, svW, svH;
    private int alphaBarX, alphaBarY, alphaBarW, alphaBarH;
    private int labelColorY, labelAlphaY;

    private NumericStepper alphaStepper;
    private GuiButton titleStyleBtn;
    private GuiButton soundStyleBtn;

    // HSV Color Picker state
    private float hue = 0.45f;
    private float sat = 1.0f;
    private float val = 1.0f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;
    private boolean draggingAlpha = false;

    // Draggable Preview Card state
    private int previewX = -1;
    private int previewY = -1;
    private boolean draggingPreview = false;
    private int dragOffX = 0;
    private int dragOffY = 0;

    private final AnimationValue panelAnim = new AnimationValue(0f);

    @Override
    public void initGui() {
        this.buttonList.clear();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int left = cx - PANEL_W / 2;
        int thirdW = (PANEL_W - 8) / 3;
        int halfW = (PANEL_W - 4) / 2;

        if (previewX < 0 || previewY < 0) {
            previewX = cx + PANEL_W / 2 + 12;
            previewY = cy - 78;
        }
        // On a narrow screen the default sits past the right edge, where it could
        // never be grabbed. Re-clamp on every init so a resize cannot strand it.
        clampPreview();

        // Initialize HSV state from config color
        int currentColor = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.themeAccentColor : 0x00FF88;
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

        // Accent colour section: label, hue bar, saturation/value box
        labelColorY = y;
        y += 11;
        hueX = left; hueY = y; hueW = PANEL_W; hueH = 10;
        y += 14;
        svX = left; svY = y; svW = PANEL_W; svH = 30;
        y += 34;

        // Opacity section: label, then the slider the stepper below also drives
        labelAlphaY = y;
        y += 11;
        alphaBarX = left; alphaBarY = y; alphaBarW = PANEL_W; alphaBarH = 10;
        y += 14;

        // Row 3: Card Transparency Stepper using NumericStepper
        alphaStepper = new NumericStepper(ID_ALPHA_DOWN, ID_ALPHA_UP, left, y, PANEL_W, BTN_H);
        this.buttonList.add(alphaStepper.getBtnDown());
        this.buttonList.add(alphaStepper.getBtnUp());
        y += 24;

        // Row 4: Title Effect & Countdown Chime Sound
        this.buttonList.add(titleStyleBtn = new StyledButton(ID_TITLE_STYLE, left, y, halfW, BTN_H, titleStyleLabel()));
        this.buttonList.add(soundStyleBtn = new StyledButton(ID_SOUND_STYLE, left + halfW + 4, y, halfW, BTN_H, soundStyleLabel()));
        y += 24;

        // Row 5: Flyt & Skaler HUD'er button
        this.buttonList.add(new StyledButton(ID_HUD_EDITOR, left, y, PANEL_W, BTN_H, "Flyt & Skaler HUD'er >"));
        y += 28;

        // Row 6: Back to Hub
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
    }

    private void updatePickerFromPreset(int hexColor) {
        if (MassiveOsFreakyAddons.config != null) {
            MassiveOsFreakyAddons.config.themeAccentColor = hexColor;
            MassiveOsFreakyAddons.config.save();
        }
        int r = (hexColor >> 16) & 0xFF;
        int g = (hexColor >> 8) & 0xFF;
        int b = hexColor & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    /**
     * Updates the live colour only. Saving happens once the drag ends, because
     * config.save() serialises the whole config and rewrites the file, and calling
     * it from a drag handler wrote that file on every rendered frame.
     */
    private void applyColour() {
        int argb = Color.HSBtoRGB(hue, sat, val) | 0xFF000000;
        if (MassiveOsFreakyAddons.config != null) {
            MassiveOsFreakyAddons.config.themeAccentColor = argb;
        }
    }

    /** Slider position (0-1) to an opacity inside the usable range. */
    private static float alphaFromFraction(float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return ALPHA_MIN + t * (ALPHA_MAX - ALPHA_MIN);
    }

    /** The inverse, so the cursor sits under the pointer instead of drifting. */
    private static float fractionFromAlpha(float alpha) {
        float t = (alpha - ALPHA_MIN) / (ALPHA_MAX - ALPHA_MIN);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        return t;
    }

    private static float currentAlpha() {
        float a = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.themeBgAlpha : 0.65f;
        if (a < ALPHA_MIN) a = ALPHA_MIN;
        if (a > ALPHA_MAX) a = ALPHA_MAX;
        return a;
    }

    private String alphaLabel() {
        int percent = Math.round(currentAlpha() * 100f);
        return "Synlighed: " + percent + "%";
    }

    private String titleStyleLabel() {
        int s = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.themeTitleStyle : 0;
        String name = s == 0 ? "Regnbue" : (s == 1 ? "Pulserende" : "Statisk");
        return "Titel: " + name;
    }

    private String soundStyleLabel() {
        String s = MassiveOsFreakyAddons.config != null ? MassiveOsFreakyAddons.config.alertSound : "note.pling";
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
                MassiveOsFreakyAddons.config.themeBgAlpha = Math.max(ALPHA_MIN, currentAlpha() - 0.05f);
                MassiveOsFreakyAddons.config.save();
                break;
            case ID_ALPHA_UP:
                MassiveOsFreakyAddons.config.themeBgAlpha = Math.min(ALPHA_MAX, currentAlpha() + 0.05f);
                MassiveOsFreakyAddons.config.save();
                break;
            case ID_TITLE_STYLE:
                MassiveOsFreakyAddons.config.themeTitleStyle = (MassiveOsFreakyAddons.config.themeTitleStyle + 1) % 3;
                titleStyleBtn.displayString = titleStyleLabel();
                MassiveOsFreakyAddons.config.save();
                break;
            case ID_SOUND_STYLE:
                String s = MassiveOsFreakyAddons.config.alertSound;
                if (s.contains("pling")) MassiveOsFreakyAddons.config.alertSound = "random.levelup";
                else if (s.contains("levelup")) MassiveOsFreakyAddons.config.alertSound = "random.orb";
                else if (s.contains("orb")) MassiveOsFreakyAddons.config.alertSound = "random.click";
                else MassiveOsFreakyAddons.config.alertSound = "note.pling";
                soundStyleBtn.displayString = soundStyleLabel();
                MassiveOsFreakyAddons.config.save();
                // Play it. A sound picker that makes no sound tells you nothing
                // about what you just picked.
                MassiveOsFreakyAddons.playAlert(1.0F, 1.0F);
                break;
            case ID_HUD_EDITOR:
                CelleActions.openHudEditor();
                return;
            case ID_BACK:
                CelleActions.openHub();
                return;
            default:
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // The preview card is grabbed before the buttons get a look in. It can be
        // dragged over them, and letting both run pressed the button underneath.
        if (mouseButton == 0
                && mouseX >= previewX && mouseX <= previewX + PREVIEW_W
                && mouseY >= previewY && mouseY <= previewY + PREVIEW_H) {
            draggingPreview = true;
            dragOffX = mouseX - previewX;
            dragOffY = mouseY - previewY;
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                draggingHue = true;
                updateHueFromMouse(mouseX, hueX, hueW);
                return;
            }
            if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                draggingSV = true;
                updateSVFromMouse(mouseX, mouseY, svX, svY, svW, svH);
                return;
            }
            if (mouseX >= alphaBarX && mouseX <= alphaBarX + alphaBarW
                    && mouseY >= alphaBarY && mouseY <= alphaBarY + alphaBarH) {
                draggingAlpha = true;
                updateAlphaFromMouse(mouseX, alphaBarX, alphaBarW);
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            if (draggingHue || draggingSV || draggingAlpha) {
                if (MassiveOsFreakyAddons.config != null) {
                    MassiveOsFreakyAddons.config.save();
                }
            }
            draggingHue = false;
            draggingSV = false;
            draggingAlpha = false;
            draggingPreview = false;
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

    private void updateAlphaFromMouse(int mouseX, int alphaX, int alphaW) {
        float t = (float) (mouseX - alphaX) / (float) alphaW;
        if (MassiveOsFreakyAddons.config != null) {
            MassiveOsFreakyAddons.config.themeBgAlpha = alphaFromFraction(t);
        }
    }

    /** Keeps the draggable preview card on screen so it can always be grabbed again. */
    private void clampPreview() {
        int maxX = Math.max(0, this.width - PREVIEW_W);
        int maxY = Math.max(0, this.height - PREVIEW_H);
        if (previewX > maxX) previewX = maxX;
        if (previewY > maxY) previewY = maxY;
        if (previewX < 0) previewX = 0;
        if (previewY < 0) previewY = 0;
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
        int left = cx - PANEL_W / 2;

        int titleY = cy - 142;
        // Drawn in the selected title style, so changing the setting shows
        // itself on the very screen the setting lives on.
        String heading = EnumChatFormatting.BOLD + "Tema & Udseende";
        Style.drawThemedTitle(this.fontRendererObj, heading,
                cx - this.fontRendererObj.getStringWidth(heading) / 2, titleY);

        // Handle active dragging mouse events
        if (Mouse.isButtonDown(0)) {
            if (draggingHue) {
                updateHueFromMouse(mouseX, hueX, hueW);
            }
            if (draggingSV) {
                updateSVFromMouse(mouseX, mouseY, svX, svY, svW, svH);
            }
            if (draggingAlpha) {
                updateAlphaFromMouse(mouseX, alphaBarX, alphaBarW);
            }
            if (draggingPreview) {
                previewX = mouseX - dragOffX;
                previewY = mouseY - dragOffY;
                clampPreview();
            }
        } else {
            draggingHue = false;
            draggingSV = false;
            draggingAlpha = false;
            draggingPreview = false;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Section labels, so the bars read as controls rather than loose strips
        int labelColor = 0xFF9AA0AE;
        this.fontRendererObj.drawString("ACCENTFARVE", left, labelColorY, labelColor);
        this.fontRendererObj.drawString("SYNLIGHED", left, labelAlphaY, labelColor);

        // Render Interactive Hue Bar Spectrum
        drawHueBar(hueX, hueY, hueW, hueH);

        // Draw Hue indicator cursor line
        int cursorHueX = hueX + (int) (this.hue * hueW);
        drawRect(cursorHueX - 1, hueY - 1, cursorHueX + 1, hueY + hueH + 1, 0xFFFFFFFF);

        // Render Interactive SV Box Gradient
        drawSVBox(svX, svY, svW, svH, this.hue);

        // Draw SV indicator cursor dot
        int cursorSvX = svX + (int) (this.sat * svW);
        int cursorSvY = svY + (int) ((1f - this.val) * svH);
        Style.roundedRect(cursorSvX - 2, cursorSvY - 2, cursorSvX + 2, cursorSvY + 2, 0xFFFFFFFF);
        Style.roundedRect(cursorSvX - 1, cursorSvY - 1, cursorSvX + 1, cursorSvY + 1, Style.getAccentColor());

        // Render Interactive Opacity Slider Bar
        int alphaX = alphaBarX;
        int alphaY = alphaBarY;
        int alphaW = alphaBarW;
        int alphaH = alphaBarH;
        drawOpacityBar(alphaX, alphaY, alphaW, alphaH);

        // Draw Opacity indicator cursor line, using the same mapping as the drag
        // handler so the cursor lands under the pointer at both ends of the bar.
        int cursorAlphaX = alphaX + (int) (fractionFromAlpha(currentAlpha()) * alphaW);
        drawRect(cursorAlphaX - 1, alphaY - 1, cursorAlphaX + 1, alphaY + alphaH + 1, 0xFFFFFFFF);

        // Draw NumericStepper label
        if (alphaStepper != null) {
            alphaStepper.draw(this.mc, mouseX, mouseY, alphaLabel());
        }

        // Draggable Live Theme Mini Preview Card
        drawLivePreviewCard(previewX, previewY);

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
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

    private void drawOpacityBar(int x, int y, int width, int height) {
        Style.roundedRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF14151E);
        int accent = Style.getAccentColor();
        for (int i = 0; i < width; i++) {
            // The gradient spans the same range the slider sets, so what the bar
            // shows at a position is what clicking there actually gives you.
            float a = alphaFromFraction((float) i / (float) width);
            int alphaInt = Math.max(20, Math.min(255, (int) (a * 255)));
            int col = (alphaInt << 24) | (accent & 0xFFFFFF);
            drawRect(x + i, y, x + i + 1, y + height, col);
        }
    }

    private void drawLivePreviewCard(int x1, int y1) {
        int x2 = x1 + PREVIEW_W;
        int y2 = y1 + PREVIEW_H;

        int accent = Style.getAccentColor();
        int alphaInt = Math.max(0, Math.min(255, (int) (currentAlpha() * 255)));

        // Outer & Inner border with dynamic accent
        Style.roundedRect(x1, y1, x2, y2, 0xFF14151E);
        Style.roundedRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, (0x66 << 24) | (accent & 0xFFFFFF));
        Style.roundedRect(x1 + 2, y1 + 2, x2 - 2, y2 - 2, (alphaInt << 24) | 0x0A0A0F);

        // Sample text & active pill
        this.fontRendererObj.drawStringWithShadow("Live Forhåndsvisning (Træk mig)", x1 + 8, y1 + 6, accent);
        this.fontRendererObj.drawStringWithShadow("A-12  (0h 14m)", x1 + 8, y1 + 22, 0xCCCCCC);

        String pill = Style.getAccentFormatting() + "[ TIL ]";
        this.fontRendererObj.drawStringWithShadow(pill, x2 - 8 - this.fontRendererObj.getStringWidth(pill), y1 + 22, 0xFFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
