package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A unified HUD editor: drag every on-screen HUD (Celle HUD, Rustnings-HUD,
 * Item-log, PvP Mine) to reposition it. Each HUD is shown as a labelled box at
 * its current spot; drag it and it saves on release. "Nulstil" puts them back to
 * their defaults.
 */
public class GuiHudEditor extends GuiScreen {

    private static final int ID_RESET = 0;
    private static final int ID_BACK = 1;

    private final List<Hud> huds = new ArrayList<Hud>();
    private int dragging = -1;
    private int dragOffX, dragOffY;

    /** A movable HUD: its config position, a default spot, and a nominal box size. */
    private abstract class Hud {
        final String name;

        Hud(String name) {
            this.name = name;
        }

        abstract int w();
        abstract int h();

        abstract int cfgX();       // -1 when unset

        abstract int cfgY();

        abstract void setPos(int x, int y);

        abstract void reset();

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
        final CelleConfig cfg = CelleScannerMod.config;

        huds.add(new Hud("Celle HUD") {
            int w() { return CelleHud.lastBoxRight - CelleHud.lastBoxLeft; }
            int h() { return CelleHud.lastBoxBottom - CelleHud.lastBoxTop; }
            int cfgX() { return cfg.hudX; }
            int cfgY() { return cfg.hudY; }
            void setPos(int x, int y) { cfg.hudX = x; cfg.hudY = y; }
            void reset() { cfg.hudX = 10; cfg.hudY = 10; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 10; }
        });
        huds.add(new Hud("Rustnings-HUD") {
            int w() { return ArmorHud.lastWidth; }
            int h() { return ArmorHud.lastHeight; }
            int cfgX() { return cfg.armorHudX; }
            int cfgY() { return cfg.armorHudY; }
            void setPos(int x, int y) { cfg.armorHudX = x; cfg.armorHudY = y; }
            void reset() { cfg.armorHudX = 5; cfg.armorHudY = 140; }
            int defX(int sw) { return 5; }
            int defY(int sh) { return 140; }
        });
        huds.add(new Hud("Item-log") {
            int w() { return ItemPickupNotify.lastWidth; }
            int h() { return ItemPickupNotify.lastHeight; }
            int cfgX() { return cfg.itemPickupX != null ? cfg.itemPickupX : -1; }
            int cfgY() { return cfg.itemPickupY != null ? cfg.itemPickupY : -1; }
            void setPos(int x, int y) { cfg.itemPickupX = x; cfg.itemPickupY = y; }
            void reset() { cfg.itemPickupX = null; cfg.itemPickupY = null; }
            int defX(int sw) { return sw - w() - 4; }
            int defY(int sh) { return sh - h() - 4; }
        });
        huds.add(new Hud("PvP Mine") {
            int w() { return PvpMine.lastWidth; }
            int h() { return PvpMine.lastHeight; }
            int cfgX() { return cfg.pvpMineX != null ? cfg.pvpMineX : -1; }
            int cfgY() { return cfg.pvpMineY != null ? cfg.pvpMineY : -1; }
            void setPos(int x, int y) { cfg.pvpMineX = x; cfg.pvpMineY = y; }
            void reset() { cfg.pvpMineX = null; cfg.pvpMineY = null; }
            int defX(int sw) { return 4; }
            int defY(int sh) { return sh - h() - 4; }
        });

        int bw = 90;
        this.buttonList.add(new StyledButton(ID_RESET, this.width / 2 - bw - 4, this.height - 24, bw, 20, "Nulstil"));
        this.buttonList.add(new StyledButton(ID_BACK, this.width / 2 + 4, this.height - 24, bw, 20, "Tilbage"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_RESET) {
            for (Hud hud : huds) {
                hud.reset();
            }
            CelleScannerMod.config.save();
        } else if (button.id == ID_BACK) {
            CelleActions.openGuiSettings();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            if (mouseX >= hx && mouseX <= hx + hud.w() && mouseY >= hy && mouseY <= hy + hud.h()) {
                dragging = i;
                dragOffX = mouseX - hx;
                dragOffY = mouseY - hy;
                return;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (dragging >= 0) {
            CelleScannerMod.config.save();
            dragging = -1;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int guideX = -1;
        int guideY = -1;

        if (dragging >= 0) {
            Hud hud = huds.get(dragging);
            int nx = mouseX - dragOffX;
            int ny = mouseY - dragOffY;
            int w = hud.w();
            int h = hud.h();

            boolean snappedX = false;
            boolean snappedY = false;

            // Snap to screen edges (4px padding)
            if (Math.abs(nx - 4) <= 6) {
                nx = 4;
                snappedX = true;
            } else if (Math.abs(nx - (this.width - w - 4)) <= 6) {
                nx = this.width - w - 4;
                snappedX = true;
            }

            if (Math.abs(ny - 4) <= 6) {
                ny = 4;
                snappedY = true;
            } else if (Math.abs(ny - (this.height - h - 4)) <= 6) {
                ny = this.height - h - 4;
                snappedY = true;
            }

            // Snap to screen centers
            int centerX = this.width / 2 - w / 2;
            if (!snappedX && Math.abs(nx - centerX) <= 6) {
                nx = centerX;
                snappedX = true;
                guideX = this.width / 2;
            }

            int centerY = this.height / 2 - h / 2;
            if (!snappedY && Math.abs(ny - centerY) <= 6) {
                ny = centerY;
                snappedY = true;
                guideY = this.height / 2;
            }

            // Snap to other HUDs
            for (int j = 0; j < huds.size(); j++) {
                if (j == dragging) continue;
                Hud other = huds.get(j);
                int ox = other.x();
                int oy = other.y();
                int ow = other.w();
                int oh = other.h();

                if (!snappedX) {
                    if (Math.abs(nx - ox) <= 6) {
                        nx = ox;
                        snappedX = true;
                        guideX = ox;
                    } else if (Math.abs((nx + w) - (ox + ow)) <= 6) {
                        nx = ox + ow - w;
                        snappedX = true;
                        guideX = ox + ow;
                    } else if (Math.abs(nx - (ox + ow + 4)) <= 6) {
                        nx = ox + ow + 4;
                        snappedX = true;
                    } else if (Math.abs((nx + w) - (ox - 4)) <= 6) {
                        nx = ox - 4 - w;
                        snappedX = true;
                    }
                }

                if (!snappedY) {
                    if (Math.abs(ny - oy) <= 6) {
                        ny = oy;
                        snappedY = true;
                        guideY = oy;
                    } else if (Math.abs((ny + h) - (oy + oh)) <= 6) {
                        ny = oy + oh - h;
                        snappedY = true;
                        guideY = oy + oh;
                    } else if (Math.abs(ny - (oy + oh + 4)) <= 6) {
                        ny = oy + oh + 4;
                        snappedY = true;
                    } else if (Math.abs((ny + h) - (oy - 4)) <= 6) {
                        ny = oy - 4 - h;
                        snappedY = true;
                    }
                }
            }

            nx = clamp(nx, 0, this.width - w);
            ny = clamp(ny, 0, this.height - h);
            hud.setPos(nx, ny);
        }

        // Draw guide lines
        if (guideX >= 0) {
            drawRect(guideX, 0, guideX + 1, this.height, 0x554BE08C);
        }
        if (guideY >= 0) {
            drawRect(0, guideY, this.width, guideY + 1, 0x554BE08C);
        }

        drawCenteredString(this.fontRendererObj, "Flyt HUD'er", this.width / 2, 8, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Træk hver kasse hen hvor du vil have den. Shift for finjustering.", this.width / 2, 20, 0xAAAAAA);

        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            int hw = hud.w();
            int hh = hud.h();
            boolean active = dragging == i
                    || (dragging < 0 && mouseX >= hx && mouseX <= hx + hw && mouseY >= hy && mouseY <= hy + hh);
            // Draw transparent background so the HUD underneath is visible
            drawRect(hx, hy, hx + hw, hy + hh, active ? 0x204BE08C : 0x10FFFFFF);
            Style.roundedRect(hx, hy, hx + hw, hy + hh, active ? Style.ACCENT : 0x55FFFFFF);
            drawCenteredString(this.fontRendererObj, hud.name, hx + hw / 2, hy + hh / 2 - 4, active ? Style.ACCENT : 0xDDFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
