package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The main menu for Massiveo's Freaky Addons - the hub you land on from the
 * keybind (default: B). Addons are grouped under their category, each tile
 * shows its live [Til]/[Fra] state, and hovering a tile shows its blurb.
 * Everything is driven off MassiveoAddons, so new addons slot in automatically.
 */
public class GuiAddonsHub extends GuiScreen {

    private static final int ID_CLOSE = 1000;

    private static final int HEADER_H = 14;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;
    private static final int CAT_GAP = 6;
    private static final int PANEL_W = 200;

    private static final class Header {
        final String text;
        final int y;
        final int color;

        Header(String text, int y, int color) {
            this.text = text;
            this.y = y;
            this.color = color;
        }
    }

    private final List<Header> headers = new ArrayList<Header>();
    private final List<MassiveoAddons.Addon> flatAddons = new ArrayList<MassiveoAddons.Addon>();
    private final List<GuiButton> addonButtons = new ArrayList<GuiButton>();

    private int panelX1, panelY1, panelX2, panelY2;

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.headers.clear();
        this.flatAddons.clear();
        this.addonButtons.clear();

        List<String> categories = MassiveoAddons.categories();

        // Measure total height so the block can be vertically centered.
        int totalH = 0;
        for (String cat : categories) {
            totalH += HEADER_H + MassiveoAddons.addonsIn(cat).size() * ROW_H + CAT_GAP;
        }
        totalH += BTN_H; // close button

        int centerX = this.width / 2;
        int left = centerX - PANEL_W / 2;
        int y = Math.max(28, this.height / 2 - totalH / 2);

        int id = 0;
        for (String cat : categories) {
            headers.add(new Header(cat, y, colorForCategory(cat)));
            y += HEADER_H;
            for (MassiveoAddons.Addon addon : MassiveoAddons.addonsIn(cat)) {
                GuiButton button = new StyledButton(id++, left, y, PANEL_W, BTN_H, labelFor(addon));
                this.buttonList.add(button);
                this.addonButtons.add(button);
                this.flatAddons.add(addon);
                y += ROW_H;
            }
            y += CAT_GAP;
        }

        this.buttonList.add(new StyledButton(ID_CLOSE, left, y, PANEL_W, BTN_H, "Luk"));

        int contentTop = headers.isEmpty() ? y : headers.get(0).y;
        panelX1 = left - 12;
        panelX2 = left + PANEL_W + 12;
        panelY1 = contentTop - 34;
        panelY2 = y + BTN_H + 10;
    }

    private static String labelFor(MassiveoAddons.Addon addon) {
        String status = addon.isActive()
                ? EnumChatFormatting.GREEN + "[Til]"
                : EnumChatFormatting.GRAY + "[Fra]";
        return addon.name() + "  " + status;
    }

    private static int colorForCategory(String category) {
        if ("Celler".equals(category)) {
            return 0x55FF55;
        }
        if ("PvP".equals(category)) {
            return 0xFF5555;
        }
        if ("World".equals(category)) {
            return 0x55FFFF;
        }
        return 0xFFFFFF;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_CLOSE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id >= 0 && button.id < flatAddons.size()) {
            flatAddons.get(button.id).open();
        }
    }

    private int hoveredAddon(int mouseX, int mouseY) {
        for (int i = 0; i < addonButtons.size(); i++) {
            GuiButton b = addonButtons.get(i);
            if (mouseX >= b.xPosition && mouseX <= b.xPosition + b.width
                    && mouseY >= b.yPosition && mouseY <= b.yPosition + b.height) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.panel(panelX1, panelY1, panelX2, panelY2);
        // Accent underline beneath the title.
        drawRect(panelX1 + 10, panelY1 + 22, panelX2 - 10, panelY1 + 23, Style.ACCENT);

        int titleY = (headers.isEmpty() ? this.height / 2 - 40 : headers.get(0).y) - 26;
        drawCenteredString(this.fontRendererObj, MassiveoAddons.BRAND, this.width / 2, titleY, 0xFF55FF);
        drawCenteredString(this.fontRendererObj, flatAddons.size() + " addons", this.width / 2, titleY + 11, 0xAAAAAA);

        for (Header h : headers) {
            drawString(this.fontRendererObj, EnumChatFormatting.BOLD + h.text, this.width / 2 - PANEL_W / 2, h.y, h.color);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        int hovered = hoveredAddon(mouseX, mouseY);
        if (hovered >= 0) {
            drawCenteredString(this.fontRendererObj, flatAddons.get(hovered).description(),
                    this.width / 2, this.height - 14, 0x888888);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
