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
        final int w, h;

        Hud(String name, int w, int h) {
            this.name = name;
            this.w = w;
            this.h = h;
        }

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

        huds.add(new Hud("Celle HUD", 108, 66) {
            int cfgX() { return cfg.hudX; }
            int cfgY() { return cfg.hudY; }
            void setPos(int x, int y) { cfg.hudX = x; cfg.hudY = y; }
            void reset() { cfg.hudX = 10; cfg.hudY = 10; }
            int defX(int sw) { return 10; }
            int defY(int sh) { return 10; }
        });
        huds.add(new Hud("Rustnings-HUD", 70, 82) {
            int cfgX() { return cfg.armorHudX; }
            int cfgY() { return cfg.armorHudY; }
            void setPos(int x, int y) { cfg.armorHudX = x; cfg.armorHudY = y; }
            void reset() { cfg.armorHudX = 5; cfg.armorHudY = 140; }
            int defX(int sw) { return 5; }
            int defY(int sh) { return 140; }
        });
        huds.add(new Hud("Item-log", 96, 44) {
            int cfgX() { return cfg.itemPickupX != null ? cfg.itemPickupX : -1; }
            int cfgY() { return cfg.itemPickupY != null ? cfg.itemPickupY : -1; }
            void setPos(int x, int y) { cfg.itemPickupX = x; cfg.itemPickupY = y; }
            void reset() { cfg.itemPickupX = null; cfg.itemPickupY = null; }
            int defX(int sw) { return sw - w - 4; }
            int defY(int sh) { return sh - h - 4; }
        });
        huds.add(new Hud("PvP Mine", 108, 60) {
            int cfgX() { return cfg.pvpMineX != null ? cfg.pvpMineX : -1; }
            int cfgY() { return cfg.pvpMineY != null ? cfg.pvpMineY : -1; }
            void setPos(int x, int y) { cfg.pvpMineX = x; cfg.pvpMineY = y; }
            void reset() { cfg.pvpMineX = null; cfg.pvpMineY = null; }
            int defX(int sw) { return 4; }
            int defY(int sh) { return sh - h - 4; }
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
            if (mouseX >= hx && mouseX <= hx + hud.w && mouseY >= hy && mouseY <= hy + hud.h) {
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

        if (dragging >= 0) {
            Hud hud = huds.get(dragging);
            int nx = clamp(mouseX - dragOffX, 0, this.width - hud.w);
            int ny = clamp(mouseY - dragOffY, 0, this.height - hud.h);
            hud.setPos(nx, ny);
        }

        drawCenteredString(this.fontRendererObj, "Flyt HUD'er", this.width / 2, 8, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Træk hver kasse hen hvor du vil have den.", this.width / 2, 20, 0xAAAAAA);

        for (int i = 0; i < huds.size(); i++) {
            Hud hud = huds.get(i);
            int hx = hud.x();
            int hy = hud.y();
            boolean active = dragging == i
                    || (dragging < 0 && mouseX >= hx && mouseX <= hx + hud.w && mouseY >= hy && mouseY <= hy + hud.h);
            drawRect(hx, hy, hx + hud.w, hy + hud.h, active ? 0xC0203028 : 0x90101014);
            Style.roundedRect(hx, hy, hx + hud.w, hy + hud.h, active ? Style.ACCENT : 0xFF3A3A44);
            drawRect(hx + 1, hy + 1, hx + hud.w - 1, hy + hud.h - 1, active ? 0xC0202830 : 0x90101014);
            drawCenteredString(this.fontRendererObj, hud.name, hx + hud.w / 2, hy + hud.h / 2 - 4, active ? 0xFFFFFF : 0xCCCCCC);
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
