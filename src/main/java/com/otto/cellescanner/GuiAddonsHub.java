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

    // Settings gear (bottom-right of the card).
    private static final int GEAR_SIZE = 9;
    private int gearX, gearY;

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
        if ("Tracking".equals(category)) {
            return EnumChatFormatting.RED.toString();
        }
        if ("Quality of life".equals(category)) {
            return EnumChatFormatting.AQUA.toString();
        }
        if ("World".equals(category)) {
            return EnumChatFormatting.GOLD.toString();
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

        // Settings gear in the bottom-right corner of the card.
        int cardRight = cx + Math.min(cx - 8, 170);
        int cardBottom = this.height / 2 + Math.min(this.height / 2 - 8, 150);
        gearX = cardRight - GEAR_SIZE - 6;
        gearY = cardBottom - GEAR_SIZE - 6;
        boolean gearHover = mouseX >= gearX - 3 && mouseX <= gearX + GEAR_SIZE + 3
                && mouseY >= gearY - 3 && mouseY <= gearY + GEAR_SIZE + 3;
        drawGear(gearX, gearY, GEAR_SIZE, gearHover ? 0xFFFFFFFF : 0xFFB0B0B8, 0xFF15151A);
        if (gearHover) {
            String t = "Indstillinger";
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + t,
                    gearX - this.fontRendererObj.getStringWidth(t) - 6, gearY + 1, 0xAAAAAA);
        }
    }

    private void drawGear(int left, int top, int s, int body, int hole) {
        int cx = left + s / 2;
        int cy = top + s / 2;
        drawRect(cx - 1, top - 2, cx + 2, top + 1, body);            // top tooth
        drawRect(cx - 1, top + s - 1, cx + 2, top + s + 2, body);    // bottom tooth
        drawRect(left - 2, cy - 1, left + 1, cy + 2, body);          // left tooth
        drawRect(left + s - 1, cy - 1, left + s + 2, cy + 2, body);  // right tooth
        Style.roundedRect(left, top, left + s, top + s, body);       // body
        drawRect(cx - 1, cy - 1, cx + 2, cy + 2, hole);              // centre hole
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && mouseX >= gearX - 3 && mouseX <= gearX + GEAR_SIZE + 3
                && mouseY >= gearY - 3 && mouseY <= gearY + GEAR_SIZE + 3) {
            this.mc.displayGuiScreen(new GuiGuiSettings());
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
