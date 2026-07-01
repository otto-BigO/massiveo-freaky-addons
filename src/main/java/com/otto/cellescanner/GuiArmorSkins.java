package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the Armor Skins addon - just an on/off toggle plus a short
 * explanation of what the colors/levels mean. Opened from the hub.
 */
public class GuiArmorSkins extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_BACK = 1;

    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 20;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Rustnings-skins: " + (CelleScannerMod.config.armorSkinsEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleArmorSkins();
                toggleButton.displayString = toggleLabel();
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

        int cx = this.width / 2;
        int titleY = this.height / 2 - 64;
        drawCenteredString(this.fontRendererObj, "Rustnings-skins", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Viser rustning efter beskyttelses-niveau (Protection 1-4).", cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "Jern P2-P4 og diamant P1-P4 får MesterHolm-teksturer.", cx, titleY + 24, 0xAAAAAA);

        int infoY = this.height / 2 + 34;
        drawCenteredString(this.fontRendererObj, "Kun rustningen ændres - resten af spillet er urørt.", cx, infoY, 0x888888);
        drawCenteredString(this.fontRendererObj, "Virker uden OptiFine. Kræver ingen texture pack.", cx, infoY + 12, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
