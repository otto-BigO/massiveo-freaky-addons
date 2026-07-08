package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

/**
 * Control screen for the Item Value addon - toggle the tooltip line and reload
 * the price file after editing it. Opened from the hub.
 */
public class GuiItemValues extends GuiScreen {

    private static final int ID_TOGGLE = 0;
    private static final int ID_RELOAD = 1;
    private static final int ID_BACK = 2;

    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private GuiButton toggleButton;

    @Override
    public void initGui() {
        this.buttonList.clear();
        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 24;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, y, PANEL_W, BTN_H, toggleLabel()));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_RELOAD, left, y, PANEL_W, BTN_H, "Genindlæs priser"));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "Tilbage"));
    }

    private String toggleLabel() {
        return "Item værdi: " + (CelleScannerMod.config.itemValueEnabled ? "Til" : "Fra");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_TOGGLE:
                CelleActions.toggleItemValues();
                toggleButton.displayString = toggleLabel();
                break;
            case ID_RELOAD:
                CelleActions.reloadItemValues();
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
        int titleY = this.height / 2 - 70;
        drawCenteredString(this.fontRendererObj, "Item Værdi", cx, titleY, 0x55FFFF);
        drawCenteredString(this.fontRendererObj, "Viser en vares værdi under navnet i tooltippet.", cx, titleY + 14, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, ItemValues.count() + " varer i prislisten.", cx, titleY + 24, 0xAAAAAA);

        int infoY = this.height / 2 + 68;
        drawCenteredString(this.fontRendererObj, "Rediger priser i: config/massiveo_prices.json", cx, infoY, 0x888888);
        drawCenteredString(this.fontRendererObj, "og tryk Genindlæs priser (ingen genstart nødvendig).", cx, infoY + 11, 0x888888);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
