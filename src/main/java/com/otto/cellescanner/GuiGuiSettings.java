package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * GUI settings tab (opened from the gear in the hub). Meant to hold all the
 * move/customize options for the mod's on-screen GUIs. For now it's a shell with
 * the existing HUD-move screen and a note that more is coming.
 */
public class GuiGuiSettings extends GuiScreen {

    private static final int ID_MOVE_HUD = 0;
    private static final int ID_BACK = 1;
    private static final int PANEL_W = 220;
    private static final int BTN_H = 20;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 6;
        this.buttonList.add(new StyledButton(ID_MOVE_HUD, left, y, PANEL_W, BTN_H, "Flyt HUD'er"));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_MOVE_HUD:
                CelleActions.openHudEditor();
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
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = this.height / 2 - 46;
        drawCenteredString(this.fontRendererObj, "GUI Indstillinger", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Flyt og tilpas mod'ens GUI'er.", cx, titleY + 12, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Flere indstillinger kommer snart.", cx, this.height / 2 + 44, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
