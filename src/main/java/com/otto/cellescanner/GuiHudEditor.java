package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A unified HUD editor with interactive 4-corner desktop window resizing!
 * Drag any HUD box to move it, or drag any of the 4 corner handles (⌜ ⌝ ⌞ ⌟)
 * or scroll the mouse wheel to scale HUD size smoothly (0.4x - 3.0x).
 */
public class GuiHudEditor extends GuiScreen {

    private static final int ID_RESET = 0;
    private static final int ID_BACK = 1;

    private final List<Hud> huds = new ArrayList<Hud>();
    private int draggingHud = -1;
    private int dragOffX, dragOffY;

    // Resizing state
    private int resizingHud = -1;
    private int resizingCorner = -1; // 0=TopLeft, 1=TopRight, 2=BottomLeft, 3=BottomRight
    private int initialMouseX, initialMouseY;
    private float initialScale;
    private int initialW, initialH;

    /** A movable and scalable HUD. */
    private abstract class Hud {
        final String name;

        Hud(String name) {
            this.name = name;
        }

        abstract int baseW();
        abstract int baseH();

        abstract float getScale();
        abstract void setScale(float s);

        int w() { return (int) Math.max(24, baseW() * getScale()); }
        int h() { return (int) Math.max(18, baseH() * getScale()); }

        abstract int cfgX();
        abstract int cfgY();

        abstract void setPos(int x, int y);
        abstract void resetPosAndScale();

        abstract int defX(int screenW);
        abstract int defY(int screenH);

        int x() {
            int c = cfgX();
            return c >= 0 ? c : defX(width);
        }

        int y() {
            int c = cfgY();
            return c >= 0 ? c : defY(height);
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        huds.clear();
        final CelleConfig cfg = MassiveOsFreakyAddons.config;

        huds.add(new Hud("Celle HUD") {
            int baseW() { return Math.max(90, CelleHud.lastBoxRight - CelleHud.lastBoxLeft); }
            int baseH() { return Math.max(40, CelleHud.lastBoxBottom - CelleHud.lastBoxTop); }
            float getScale() { return cfg.hudFontScale; }
            void setScale(float s) { cfg.hudFontScale = s; }
            int cfgX() { return cfg.hudX; }
            int cfgY() { return cfg.hudY; }
            void setPos(int x, int y) { cfg.hudX = x; cfg.hudY = y; }
            void resetPosAndScale() { cfg.hudX = 10; cfg.hudY = 10; cfg.hudFontScale = 1.0f; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 10; }
        });
        huds.add(new Hud("Mine Tracker") {
            int baseW() { return Math.max(130, MineTracker.lastWidth); }
            int baseH() { return Math.max(46, MineTracker.lastHeight); }
            float getScale() { return cfg.mineTrackerScale; }
            void setScale(float s) { cfg.mineTrackerScale = s; }
            int cfgX() { return cfg.mineTrackerX; }
            int cfgY() { return cfg.mineTrackerY; }
            void setPos(int x, int y) { cfg.mineTrackerX = x; cfg.mineTrackerY = y; }
            void resetPosAndScale() { cfg.mineTrackerX = 10; cfg.mineTrackerY = 120; cfg.mineTrackerScale = 1.0f; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 120; }
        });
        huds.add(new Hud("Rustnings-HUD") {
            int baseW() { return Math.max(60, ArmorHud.lastWidth); }
            int baseH() { return Math.max(30, ArmorHud.lastHeight); }
            float getScale() { return cfg.armorHudScale; }
            void setScale(float s) { cfg.armorHudScale = s; }
            int cfgX() { return cfg.armorHudX; }
            int cfgY() { return cfg.armorHudY; }
            void setPos(int x, int y) { cfg.armorHudX = x; cfg.armorHudY = y; }
            void resetPosAndScale() { cfg.armorHudX = 5; cfg.armorHudY = 180; cfg.armorHudScale = 1.0f; }
            int defX(int sw) { return 5; }
            int defY(int sh) { return 180; }
        });
        huds.add(new Hud("Item-log") {
            int baseW() { return Math.max(80, ItemPickupNotify.lastWidth); }
            int baseH() { return Math.max(30, ItemPickupNotify.lastHeight); }
            float getScale() { return cfg.itemPickupScale; }
            void setScale(float s) { cfg.itemPickupScale = s; }
            int cfgX() { return cfg.itemPickupX != null ? cfg.itemPickupX : -1; }
            int cfgY() { return cfg.itemPickupY != null ? cfg.itemPickupY : -1; }
            void setPos(int x, int y) { cfg.itemPickupX = x; cfg.itemPickupY = y; }
            void resetPosAndScale() { cfg.itemPickupX = null; cfg.itemPickupY = null; cfg.itemPickupScale = 1.0f; }
            int defX(int sw) { return sw - w() - 4; }
            int defY(int sh) { return sh - h() - 4; }
        });
        huds.add(new Hud("PvP Mine") {
            int baseW() { return Math.max(80, PvpMine.lastWidth); }
            int baseH() { return Math.max(30, PvpMine.lastHeight); }
            float getScale() { return cfg.pvpMineScale; }
            void setScale(float s) { cfg.pvpMineScale = s; }
            int cfgX() { return cfg.pvpMineX != null ? cfg.pvpMineX : -1; }
            int cfgY() { return cfg.pvpMineY != null ? cfg.pvpMineY : -1; }
            void setPos(int x, int y) { cfg.pvpMineX = x; cfg.pvpMineY = y; }
            void resetPosAndScale() { cfg.pvpMineX = null; cfg.pvpMineY = null; cfg.pvpMineScale = 1.0f; }
            int defX(int sw) { return 4; }
            int defY(int sh) { return sh - h() - 4; }
        });
        huds.add(new Hud("Debug Overlay (F12)") {
            int baseW() { return 175; }
            int baseH() { return 66; }
            float getScale() { return cfg.debugScale; }
            void setScale(float s) { cfg.debugScale = s; }
            int cfgX() { return cfg.debugX; }
            int cfgY() { return cfg.debugY; }
            void setPos(int x, int y) { cfg.debugX = x; cfg.debugY = y; }
            void resetPosAndScale() { cfg.debugX = 6; cfg.debugY = 6; cfg.debugScale = 1.0f; }
            int defX(int sw) { return 6; }
            int defY(int sh) { return 6; }
        });

        int bw = 90;
        this.buttonList.add(new StyledButton(ID_RESET, this.width / 2 - bw - 4, this.height - 24, bw, 20, "Nulstil"));
        this.buttonList.add(new StyledButton(ID_BACK, this.width / 2 + 4, this.height - 24, bw, 20, "Tilbage"));
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            int targetIdx = draggingHud >= 0 ? draggingHud : getHoveredHud(mouseX, mouseY);
            if (targetIdx >= 0) {
                Hud hud = huds.get(targetIdx);
                float curScale = hud.getScale();
                float newScale = wheel > 0 ? curScale + 0.1f : curScale - 0.1f;
                newScale = Math.max(0.4f, Math.min(3.0f, Math.round(newScale * 10f) / 10f));
                hud.setScale(newScale);
                MassiveOsFreakyAddons.config.save();
            }
        }
    }

    private int getHoveredHud(int mouseX, int mouseY) {
        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            if (mouseX >= hx && mouseX <= hx + hud.w() && mouseY >= hy && mouseY <= hy + hud.h()) {
                return i;
            }
        }
        return -1;
    }

    /** Returns 0=TopLeft, 1=TopRight, 2=BottomLeft, 3=BottomRight, or -1 if no corner hit. */
    private int getHoveredCorner(Hud hud, int mouseX, int mouseY) {
        int hx = hud.x();
        int hy = hud.y();
        int hw = hud.w();
        int hh = hud.h();
        int r = 8; // corner hit radius

        if (mouseX >= hx && mouseX <= hx + r && mouseY >= hy && mouseY <= hy + r) return 0; // Top-Left
        if (mouseX >= hx + hw - r && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + r) return 1; // Top-Right
        if (mouseX >= hx && mouseX <= hx + r && mouseY >= hy + hh - r && mouseY <= hy + hh) return 2; // Bottom-Left
        if (mouseX >= hx + hw - r && mouseX <= hx + hw && mouseY >= hy + hh - r && mouseY <= hy + hh) return 3; // Bottom-Right

        return -1;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_RESET) {
            for (Hud hud : huds) {
                hud.resetPosAndScale();
            }
            MassiveOsFreakyAddons.config.save();
        } else if (button.id == ID_BACK) {
            CelleActions.openThemeEditor();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;

        // Check corner handle hits first
        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int corner = getHoveredCorner(hud, mouseX, mouseY);
            if (corner >= 0) {
                resizingHud = i;
                resizingCorner = corner;
                initialMouseX = mouseX;
                initialMouseY = mouseY;
                initialScale = hud.getScale();
                initialW = hud.w();
                initialH = hud.h();
                return;
            }
        }

        // Check HUD box drag hits
        int targetIdx = getHoveredHud(mouseX, mouseY);
        if (targetIdx >= 0) {
            Hud hud = huds.get(targetIdx);
            draggingHud = targetIdx;
            dragOffX = mouseX - hud.x();
            dragOffY = mouseY - hud.y();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (draggingHud >= 0 || resizingHud >= 0) {
            MassiveOsFreakyAddons.config.save();
            draggingHud = -1;
            resizingHud = -1;
            resizingCorner = -1;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Handle active corner resizing drag
        if (resizingHud >= 0 && Mouse.isButtonDown(0)) {
            Hud hud = huds.get(resizingHud);
            int dx = mouseX - initialMouseX;
            int dy = mouseY - initialMouseY;

            int delta = 0;
            switch (resizingCorner) {
                case 0: delta = -dx - dy; break; // TopLeft
                case 1: delta = dx - dy; break;  // TopRight
                case 2: delta = -dx + dy; break; // BottomLeft
                case 3: delta = dx + dy; break;  // BottomRight
            }

            float newScale = initialScale + ((float) delta / (float) Math.max(initialW, initialH));
            newScale = Math.max(0.4f, Math.min(3.0f, Math.round(newScale * 20f) / 20f));
            hud.setScale(newScale);
        }

        int guideX = -1;
        int guideY = -1;

        if (draggingHud >= 0) {
            Hud hud = huds.get(draggingHud);
            int nx = mouseX - dragOffX;
            int ny = mouseY - dragOffY;
            int w = hud.w();
            int h = hud.h();

            boolean snappedX = false;
            boolean snappedY = false;

            // Snap to screen edges (4px padding)
            if (Math.abs(nx - 4) <= 6) { nx = 4; snappedX = true; }
            else if (Math.abs(nx - (this.width - w - 4)) <= 6) { nx = this.width - w - 4; snappedX = true; }

            if (Math.abs(ny - 4) <= 6) { ny = 4; snappedY = true; }
            else if (Math.abs(ny - (this.height - h - 4)) <= 6) { ny = this.height - h - 4; snappedY = true; }

            // Snap to screen centers
            int centerX = this.width / 2 - w / 2;
            if (!snappedX && Math.abs(nx - centerX) <= 6) { nx = centerX; snappedX = true; guideX = this.width / 2; }

            int centerY = this.height / 2 - h / 2;
            if (!snappedY && Math.abs(ny - centerY) <= 6) { ny = centerY; snappedY = true; guideY = this.height / 2; }

            nx = clamp(nx, 0, this.width - w);
            ny = clamp(ny, 0, this.height - h);
            hud.setPos(nx, ny);
        }

        // Draw guide lines
        if (guideX >= 0) drawRect(guideX, 0, guideX + 1, this.height, 0x554BE08C);
        if (guideY >= 0) drawRect(0, guideY, this.width, guideY + 1, 0x554BE08C);

        drawCenteredString(this.fontRendererObj, "Flyt & Skaler HUD'er", this.width / 2, 6, Style.getAccentColor());
        drawCenteredString(this.fontRendererObj, "Træk for at flytte. Træk i et hjørne (⌜ ⌝ ⌞ ⌟) for at ændre størrelse.", this.width / 2, 18, 0xAAAAAA);

        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            int hw = hud.w();
            int hh = hud.h();

            boolean active = draggingHud == i || resizingHud == i
                    || (draggingHud < 0 && resizingHud < 0 && mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh);

            drawRect(hx, hy, hx + hw, hy + hh, active ? 0x304BE08C : 0x15FFFFFF);
            Style.roundedRect(hx, hy, hx + hw, hy + hh, active ? Style.getAccentColor() : 0x55FFFFFF);

            // Draw 4 Corner Bracket Handles (⌜ ⌝ ⌞ ⌟)
            int accent = Style.getAccentColor();
            drawCornerBracket(hx, hy, hx + hw, hy + hh, active ? accent : 0x88FFFFFF);

            String label = hud.name + " (" + String.format("%.1fx", hud.getScale()) + ")";
            drawCenteredString(this.fontRendererObj, label, hx + hw / 2, hy + hh / 2 - 4, active ? Style.getAccentColor() : 0xDDFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCornerBracket(int x1, int y1, int x2, int y2, int color) {
        int l = 6; // Bracket leg length
        // Top-Left ⌜
        drawRect(x1, y1, x1 + l, y1 + 1, color);
        drawRect(x1, y1, x1 + 1, y1 + l, color);

        // Top-Right ⌝
        drawRect(x2 - l, y1, x2, y1 + 1, color);
        drawRect(x2 - 1, y1, x2, y1 + l, color);

        // Bottom-Left ⌞
        drawRect(x1, y2 - 1, x1 + l, y2, color);
        drawRect(x1, y2 - l, x1 + 1, y2, color);

        // Bottom-Right ⌟
        drawRect(x2 - l, y2 - 1, x2, y2, color);
        drawRect(x2 - 1, y2 - l, x2, y2, color);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
