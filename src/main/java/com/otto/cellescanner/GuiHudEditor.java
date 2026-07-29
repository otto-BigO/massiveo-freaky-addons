package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive HUD Editor with real HUD previews and 4-corner desktop window resizing!
 * Renders the REAL HUD content inside each box so you see exactly how it looks while
 * moving and scaling it (0.4x - 3.0x).
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

    private abstract class Hud {
        final String name;

        Hud(String name) {
            this.name = name;
        }

        abstract int baseW();
        abstract int baseH();

        abstract float getScale();
        abstract void setScale(float s);

        int w() { return (int) Math.max(30, baseW() * getScale()); }
        int h() { return (int) Math.max(20, baseH() * getScale()); }

        abstract int cfgX();
        abstract int cfgY();

        abstract void setPos(int x, int y);
        abstract void resetPosAndScale();

        abstract int defX(int screenW);
        abstract int defY(int screenH);

        abstract void drawContent(GuiHudEditor gui, int x, int y, float scale);

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

        // 1. Celle Scanner HUD
        huds.add(new Hud("Celle Scanner") {
            int baseW() { return 140; }
            int baseH() { return 64; }
            float getScale() { return cfg.hudFontScale; }
            void setScale(float s) { cfg.hudFontScale = s; }
            int cfgX() { return cfg.hudX; }
            int cfgY() { return cfg.hudY; }
            void setPos(int x, int y) { cfg.hudX = x; cfg.hudY = y; }
            void resetPosAndScale() { cfg.hudX = 10; cfg.hudY = 10; cfg.hudFontScale = 1.0f; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 10; }

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();
                float alpha = cfg.themeBgAlpha;
                int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

                Style.roundedRect(x, y, x + 140, y + 64, 0xFF14151E);
                Style.roundedRect(x + 1, y + 1, x + 139, y + 63, (0x66 << 24) | (accent & 0xFFFFFF));
                Style.roundedRect(x + 2, y + 2, x + 138, y + 62, (alphaInt << 24) | 0x0A0A0F);

                gui.fontRendererObj.drawStringWithShadow("§lCelle Scanner", x + 6, y + 6, accent);
                gui.fontRendererObj.drawStringWithShadow("KOMMER SNART", x + 6, y + 18, 0xFFAA00);
                gui.fontRendererObj.drawStringWithShadow("A-12 (0h 14m)", x + 6, y + 30, 0xFFFFFF);
                gui.fontRendererObj.drawStringWithShadow("B-04 (1h 05m)", x + 6, y + 44, 0xAAAAAA);
            }
        });

        // 2. Mine Tracker
        huds.add(new Hud("Mine Tracker") {
            int baseW() { return 140; }
            int baseH() { return 48; }
            float getScale() { return cfg.mineTrackerScale; }
            void setScale(float s) { cfg.mineTrackerScale = s; }
            int cfgX() { return cfg.mineTrackerX; }
            int cfgY() { return cfg.mineTrackerY; }
            void setPos(int x, int y) { cfg.mineTrackerX = x; cfg.mineTrackerY = y; }
            void resetPosAndScale() { cfg.mineTrackerX = 10; cfg.mineTrackerY = 120; cfg.mineTrackerScale = 1.0f; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 120; }

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();
                float alpha = cfg.themeBgAlpha;
                int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

                Style.roundedRect(x, y, x + 140, y + 48, 0xFF14151E);
                Style.roundedRect(x + 1, y + 1, x + 139, y + 47, (0x66 << 24) | (accent & 0xFFFFFF));
                Style.roundedRect(x + 2, y + 2, x + 138, y + 46, (alphaInt << 24) | 0x0A0A0F);

                gui.fontRendererObj.drawStringWithShadow("§lMine Tracker", x + 6, y + 6, accent);
                gui.fontRendererObj.drawStringWithShadow("Iron Ore: 128 (64/m)", x + 6, y + 18, 0xFFFFFF);
                gui.fontRendererObj.drawStringWithShadow("DB Estimat: 2 DBs", x + 6, y + 30, 0x55FFFF);
            }
        });

        // 3. Rustnings-HUD
        huds.add(new Hud("Rustnings-HUD") {
            int baseW() { return 100; }
            int baseH() { return 68; }
            float getScale() { return cfg.armorHudScale; }
            void setScale(float s) { cfg.armorHudScale = s; }
            int cfgX() { return cfg.armorHudX; }
            int cfgY() { return cfg.armorHudY; }
            void setPos(int x, int y) { cfg.armorHudX = x; cfg.armorHudY = y; }
            void resetPosAndScale() { cfg.armorHudX = 5; cfg.armorHudY = 180; cfg.armorHudScale = 1.0f; }
            int defX(int sw) { return 5; }
            int defY(int sh) { return 180; }

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();
                float alpha = cfg.themeBgAlpha;
                int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

                Style.roundedRect(x, y, x + 100, y + 68, 0xFF14151E);
                Style.roundedRect(x + 1, y + 1, x + 99, y + 67, (0x66 << 24) | (accent & 0xFFFFFF));
                Style.roundedRect(x + 2, y + 2, x + 98, y + 66, (alphaInt << 24) | 0x0A0A0F);

                // Render real item previews: Helmet, Chest, Legs, Boots
                ItemStack[] sampleArmor = new ItemStack[]{
                        new ItemStack(Items.diamond_helmet),
                        new ItemStack(Items.diamond_chestplate),
                        new ItemStack(Items.diamond_leggings),
                        new ItemStack(Items.diamond_boots)
                };

                for (int i = 0; i < 4; i++) {
                    int py = y + 4 + i * 16;
                    gui.itemRender.renderItemAndEffectIntoGUI(sampleArmor[i], x + 4, py);
                    gui.fontRendererObj.drawStringWithShadow((100 - i * 8) + "%", x + 24, py + 4, i == 3 ? 0xFF5555 : 0x55FF55);
                }
            }
        });

        // 4. Item-log
        huds.add(new Hud("Item-log") {
            int baseW() { return 120; }
            int baseH() { return 36; }
            float getScale() { return cfg.itemPickupScale; }
            void setScale(float s) { cfg.itemPickupScale = s; }
            int cfgX() { return cfg.itemPickupX != null ? cfg.itemPickupX : -1; }
            int cfgY() { return cfg.itemPickupY != null ? cfg.itemPickupY : -1; }
            void setPos(int x, int y) { cfg.itemPickupX = x; cfg.itemPickupY = y; }
            void resetPosAndScale() { cfg.itemPickupX = null; cfg.itemPickupY = null; cfg.itemPickupScale = 1.0f; }
            int defX(int sw) { return sw - w() - 4; }
            int defY(int sh) { return sh - h() - 4; }

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();
                float alpha = cfg.themeBgAlpha;
                int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

                Style.roundedRect(x, y, x + 120, y + 36, 0xFF14151E);
                Style.roundedRect(x + 1, y + 1, x + 119, y + 35, (0x66 << 24) | (accent & 0xFFFFFF));
                Style.roundedRect(x + 2, y + 2, x + 118, y + 34, (alphaInt << 24) | 0x0A0A0F);

                gui.fontRendererObj.drawStringWithShadow("+64 Iron Ore", x + 6, y + 6, 0x55FF55);
                gui.fontRendererObj.drawStringWithShadow("+1 Diamond Block", x + 6, y + 20, 0x55FFFF);
            }
        });

        // 5. PvP Mine
        huds.add(new Hud("PvP Mine") {
            int baseW() { return 130; }
            int baseH() { return 46; }
            float getScale() { return cfg.pvpMineScale; }
            void setScale(float s) { cfg.pvpMineScale = s; }
            int cfgX() { return cfg.pvpMineX != null ? cfg.pvpMineX : -1; }
            int cfgY() { return cfg.pvpMineY != null ? cfg.pvpMineY : -1; }
            void setPos(int x, int y) { cfg.pvpMineX = x; cfg.pvpMineY = y; }
            void resetPosAndScale() { cfg.pvpMineX = null; cfg.pvpMineY = null; cfg.pvpMineScale = 1.0f; }
            int defX(int sw) { return 4; }
            int defY(int sh) { return sh - h() - 4; }

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();
                float alpha = cfg.themeBgAlpha;
                int alphaInt = Math.max(20, Math.min(255, (int) (alpha * 255)));

                Style.roundedRect(x, y, x + 130, y + 46, 0xFF14151E);
                Style.roundedRect(x + 1, y + 1, x + 129, y + 45, (0x66 << 24) | (accent & 0xFFFFFF));
                Style.roundedRect(x + 2, y + 2, x + 128, y + 44, (alphaInt << 24) | 0x0A0A0F);

                gui.fontRendererObj.drawStringWithShadow("§lPvP Mine", x + 6, y + 6, accent);
                gui.fontRendererObj.drawStringWithShadow("Spillere: 2 (Zone Rød)", x + 6, y + 18, 0xFF5555);
                gui.fontRendererObj.drawStringWithShadow("Drop: 04m 20s", x + 6, y + 30, 0xFFFF55);
            }
        });

        // 6. Debug Overlay (F12)
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

            void drawContent(GuiHudEditor gui, int x, int y, float scale) {
                int accent = Style.getAccentColor();

                Style.roundedRect(x, y, x + 175, y + 66, 0xEE0A0A0F);
                Style.roundedRect(x + 1, y + 1, x + 174, y + 65, (0x66 << 24) | (accent & 0xFFFFFF));

                gui.fontRendererObj.drawStringWithShadow("§lMassiveo Debug (F12)", x + 6, y + 4, accent);
                gui.fontRendererObj.drawStringWithShadow("FPS: 144  (6.9 ms)", x + 6, y + 16, 0xFFFFFF);
                gui.fontRendererObj.drawStringWithShadow("Hukommelse: 1024MB / 4096MB", x + 6, y + 28, 0xAAAAAA);
                gui.fontRendererObj.drawStringWithShadow("Entiteter: 42  |  Addons: 15", x + 6, y + 40, 0xAAAAAA);
                gui.fontRendererObj.drawStringWithShadow("Tema Farve: #" + Integer.toHexString(accent & 0xFFFFFF).toUpperCase(), x + 6, y + 52, accent);
            }
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
        int r = 12; // corner hit radius

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

        // Handle active corner resizing drag (Corrected scale direction for all 4 corners)
        if (resizingHud >= 0 && Mouse.isButtonDown(0)) {
            Hud hud = huds.get(resizingHud);
            int dx = mouseX - initialMouseX;
            int dy = mouseY - initialMouseY;

            float delta = 0f;
            switch (resizingCorner) {
                case 0: delta = (-dx - dy) * 0.006f; break; // TopLeft: dragging left/up increases scale
                case 1: delta = (dx - dy) * 0.006f; break;  // TopRight: dragging right/up increases scale
                case 2: delta = (-dx + dy) * 0.006f; break; // BottomLeft: dragging left/down increases scale
                case 3: delta = (dx + dy) * 0.006f; break;  // BottomRight: dragging right/down increases scale
            }

            float newScale = initialScale + delta;
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

        int accent = Style.getAccentColor();

        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            float scale = hud.getScale();

            boolean active = draggingHud == i || resizingHud == i
                    || (draggingHud < 0 && resizingHud < 0 && mouseX >= hx && mouseX <= hx + hud.w() && mouseY >= hy && mouseY <= hy + hud.h());

            int hoveredCorner = (active || resizingHud == i) ? getHoveredCorner(hud, mouseX, mouseY) : -1;

            // Render REAL SCALED HUD CONTENT PREVIEW
            GL11.glPushMatrix();
            GL11.glScalef(scale, scale, 1.0f);
            int sx = (int) (hx / scale);
            int sy = (int) (hy / scale);

            hud.drawContent(this, sx, sy, scale);
            GL11.glPopMatrix();

            // Draw glowing 4 Corner Handles & selection outline on top
            int hw = hud.w();
            int hh = hud.h();
            Style.roundedRect(hx, hy, hx + hw, hy + hh, active ? accent : 0x55FFFFFF);
            drawCornerBracket(hx, hy, hx + hw, hy + hh, active ? 0xFFFFFFFF : 0xDDFFFFFF, hoveredCorner);

            // Small scale badge at top-right
            String scaleStr = String.format("%.1fx", scale);
            this.fontRendererObj.drawStringWithShadow(scaleStr, hx + hw - this.fontRendererObj.getStringWidth(scaleStr) - 4, hy + 4, active ? accent : 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCornerBracket(int x1, int y1, int x2, int y2, int color, int hoveredCorner) {
        int l = 8; // Bracket leg length
        int accent = Style.getAccentColor();

        // Top-Left ⌜
        int c0 = hoveredCorner == 0 ? accent : color;
        drawRect(x1 - 1, y1 - 1, x1 + l, y1 + 1, c0);
        drawRect(x1 - 1, y1 - 1, x1 + 1, y1 + l, c0);
        if (hoveredCorner == 0) drawRect(x1 - 2, y1 - 2, x1 + 4, y1 + 4, accent);

        // Top-Right ⌝
        int c1 = hoveredCorner == 1 ? accent : color;
        drawRect(x2 - l, y1 - 1, x2 + 1, y1 + 1, c1);
        drawRect(x2 - 1, y1 - 1, x2 + 1, y1 + l, c1);
        if (hoveredCorner == 1) drawRect(x2 - 4, y1 - 2, x2 + 2, y1 + 4, accent);

        // Bottom-Left ⌞
        int c2 = hoveredCorner == 2 ? accent : color;
        drawRect(x1 - 1, y2 - 1, x1 + l, y2 + 1, c2);
        drawRect(x1 - 1, y2 - l, x1 + 1, y2 + 1, c2);
        if (hoveredCorner == 2) drawRect(x1 - 2, y2 - 4, x1 + 4, y2 + 2, accent);

        // Bottom-Right ⌟
        int c3 = hoveredCorner == 3 ? accent : color;
        drawRect(x2 - l, y2 - 1, x2 + 1, y2 + 1, c3);
        drawRect(x2 - 1, y2 - l, x2 + 1, y2 + 1, c3);
        if (hoveredCorner == 3) drawRect(x2 - 4, y2 - 4, x2 + 2, y2 + 2, accent);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
