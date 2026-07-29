package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;

/**
 * On-join interactive update notification screen.
 * Displays when a newer release of Massiveo's Freaky Addons is available.
 */
public class GuiUpdateNotifier extends GuiScreen {

    private static final int ID_UPDATE_NOW = 0;
    private static final int ID_DISMISS = 1;

    private static final int PANEL_W = 240;
    private static final int BTN_H = 20;

    @Override
    public void initGui() {
        this.buttonList.clear();

        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 + 10;

        this.buttonList.add(new StyledButton(ID_UPDATE_NOW, left, y, PANEL_W, BTN_H, "Opdater Nu"));
        y += BTN_H + 6;
        this.buttonList.add(new StyledButton(ID_DISMISS, left, y, PANEL_W, BTN_H, "Senere / Luk"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_UPDATE_NOW) {
            CelleActions.openUpdate();
        } else if (button.id == ID_DISMISS) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int titleY = cy - 70;

        int accent = Style.getAccentColor();        drawCenteredString(this.fontRendererObj, EnumChatFormatting.BOLD + "🚀 Ny Opdatering Tilgængelig!", cx, titleY, accent);

        String latest = AutoUpdater.getLatestVersion();
        if (latest == null) latest = "2.0.0";

        drawCenteredString(this.fontRendererObj, "Nuværende: " + MassiveOsFreakyAddons.VERSION + "  ➜  Nyeste: " + latest, cx, titleY + 18, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, "En ny opdatering til Massiveo's Freaky Addons 2.0", cx, titleY + 34, 0xAAAAAA);
        drawCenteredString(this.fontRendererObj, "er klar til download!", cx, titleY + 44, 0xAAAAAA);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
