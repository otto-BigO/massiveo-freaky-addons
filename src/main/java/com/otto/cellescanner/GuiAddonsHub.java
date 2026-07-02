package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The main menu for Massiveo's Freaky Addons - the hub from the keybind (B).
 * Two levels: the genre list (Celler / PvP / World), and, when a genre is
 * chosen, the addons inside it. Each level is its own instance of this screen
 * (navigation uses displayGuiScreen, never an in-place buttonList rebuild during
 * a click - that caused clicks to "fall through" onto the newly-placed button).
 */
public class GuiAddonsHub extends GuiScreen {

    private static final int ID_CLOSE = 1000;
    private static final int ID_BACK = 1001;

    private static final int ROW_H = 24;
    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    // null = genre level; otherwise the genre whose addons are shown.
    private final String category;

    private final List<String> levelCategories = new ArrayList<String>();
    private final List<MassiveoAddons.Addon> levelAddons = new ArrayList<MassiveoAddons.Addon>();
    private final List<GuiButton> itemButtons = new ArrayList<GuiButton>();

    private int firstRowY;

    public GuiAddonsHub() {
        this(null);
    }

    public GuiAddonsHub(String category) {
        this.category = category;
    }

    @Override
    public void initGui() {
        AddonList.ensureRegistered();
        this.buttonList.clear();
        this.levelCategories.clear();
        this.levelAddons.clear();
        this.itemButtons.clear();

        int rowCount;
        if (category == null) {
            levelCategories.addAll(MassiveoAddons.categories());
            rowCount = levelCategories.size();
        } else {
            levelAddons.addAll(MassiveoAddons.addonsIn(category));
            rowCount = levelAddons.size();
        }

        int left = this.width / 2 - PANEL_W / 2;
        int contentH = (rowCount + 1) * ROW_H; // items + close/back
        int y = Math.max(this.height / 2 - 130, this.height / 2 - contentH / 2);
        firstRowY = y;

        int id = 0;
        if (category == null) {
            for (String cat : levelCategories) {
                int count = MassiveoAddons.addonsIn(cat).size();
                String label = catColor(cat) + cat + "  " + EnumChatFormatting.GRAY + "(" + count + ")";
                GuiButton b = new StyledButton(id++, left, y, PANEL_W, BTN_H, label);
                this.buttonList.add(b);
                this.itemButtons.add(b);
                y += ROW_H;
            }
            this.buttonList.add(new StyledButton(ID_CLOSE, left, y, PANEL_W, BTN_H, "Luk"));
        } else {
            for (MassiveoAddons.Addon addon : levelAddons) {
                GuiButton b = new StyledButton(id++, left, y, PANEL_W, BTN_H, addonLabel(addon));
                this.buttonList.add(b);
                this.itemButtons.add(b);
                y += ROW_H;
            }
            this.buttonList.add(new StyledButton(ID_BACK, left, y, PANEL_W, BTN_H, "< Tilbage"));
        }
    }

    private static String addonLabel(MassiveoAddons.Addon addon) {
        String status = addon.isActive()
                ? EnumChatFormatting.GREEN + "[Til]"
                : EnumChatFormatting.GRAY + "[Fra]";
        return addon.name() + "  " + status;
    }

    private static String catColor(String category) {
        if ("Celler".equals(category)) {
            return EnumChatFormatting.GREEN.toString();
        }
        if ("PvP".equals(category)) {
            return EnumChatFormatting.RED.toString();
        }
        if ("World".equals(category)) {
            return EnumChatFormatting.AQUA.toString();
        }
        return EnumChatFormatting.WHITE.toString();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        // All navigation swaps to a fresh screen rather than rebuilding this
        // one's buttons mid-click, so a click can never fall through.
        if (button.id == ID_CLOSE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == ID_BACK) {
            this.mc.displayGuiScreen(new GuiAddonsHub());
            return;
        }
        if (button.id >= 0) {
            if (category == null && button.id < levelCategories.size()) {
                this.mc.displayGuiScreen(new GuiAddonsHub(levelCategories.get(button.id)));
            } else if (category != null && button.id < levelAddons.size()) {
                levelAddons.get(button.id).open();
            }
        }
    }

    private int hoveredItem(int mouseX, int mouseY) {
        for (int i = 0; i < itemButtons.size(); i++) {
            GuiButton b = itemButtons.get(i);
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
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int titleY = firstRowY - 34;
        drawCenteredString(this.fontRendererObj, MassiveoAddons.BRAND, cx, titleY, 0xFF55FF);
        String subtitle = category == null ? "Vælg en kategori" : catColor(category) + category;
        drawCenteredString(this.fontRendererObj, subtitle, cx, titleY + 11, 0xAAAAAA);
        drawRect(cx - PANEL_W / 2, titleY + 22, cx + PANEL_W / 2, titleY + 23, Style.ACCENT);

        super.drawScreen(mouseX, mouseY, partialTicks);

        int hovered = hoveredItem(mouseX, mouseY);
        if (hovered >= 0) {
            String hint;
            if (category == null) {
                String cat = levelCategories.get(hovered);
                hint = MassiveoAddons.addonsIn(cat).size() + " addons i " + cat;
            } else {
                hint = levelAddons.get(hovered).description();
            }
            drawCenteredString(this.fontRendererObj, hint, cx, this.height / 2 + 138, 0x888888);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
