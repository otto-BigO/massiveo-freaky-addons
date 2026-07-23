package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean, modern GUI selector for the 120 3D fantasy weapons from Blades of Majestica.
 * Features a real-time search bar, category filter tabs, a card grid, and a live 3D spinning model preview!
 */
public class GuiWeaponSelector extends GuiScreen {

    private static final int ID_RESET = 100;
    private static final int ID_BACK = 101;
    private static final int ID_TAB_ALL = 200;
    private static final int ID_TAB_SWORDS = 201;
    private static final int ID_TAB_AXES = 202;
    private static final int ID_TAB_SELECTED = 203;

    private static final int ID_PREV_PAGE = 300;
    private static final int ID_NEXT_PAGE = 301;
    private static final int ID_EQUIP_SEL = 400;

    private GuiTextField searchField;
    private final List<MajesticaWeapons.Weapon> filteredWeapons = new ArrayList<MajesticaWeapons.Weapon>();
    private MajesticaWeapons.Weapon selectedWeapon = null;

    private int currentCategory = 0; // 0=All, 1=Swords, 2=Axes, 3=Selected
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 12;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();

        int cx = this.width / 2;
        int cy = this.height / 2;

        int panelW = 420;
        int panelH = 245;
        int panelLeft = cx - panelW / 2;
        int panelTop = cy - panelH / 2;

        // Search field at top left
        this.searchField = new GuiTextField(1, this.fontRendererObj, panelLeft + 15, panelTop + 35, 160, 18);
        this.searchField.setMaxStringLength(40);

        // Category filter tabs
        int tabY = panelTop + 58;
        int tabW = 42;
        this.buttonList.add(new StyledButton(ID_TAB_ALL, panelLeft + 15, tabY, tabW, 16, "Alle"));
        this.buttonList.add(new StyledButton(ID_TAB_SWORDS, panelLeft + 60, tabY, tabW, 16, "Sværd"));
        this.buttonList.add(new StyledButton(ID_TAB_AXES, panelLeft + 105, tabY, tabW, 16, "Økser"));
        this.buttonList.add(new StyledButton(ID_TAB_SELECTED, panelLeft + 150, tabY, tabW, 16, "Valgt"));

        // Pagination buttons for card grid
        int pageY = panelTop + panelH - 28;
        this.buttonList.add(new StyledButton(ID_PREV_PAGE, panelLeft + 15, pageY, 40, 18, "◄ Forr."));
        this.buttonList.add(new StyledButton(ID_NEXT_PAGE, panelLeft + 155, pageY, 40, 18, "Næste ►"));

        // Action buttons
        this.buttonList.add(new StyledButton(ID_EQUIP_SEL, panelLeft + 270, panelTop + panelH - 30, 70, 20, "Udstyr"));
        this.buttonList.add(new StyledButton(ID_RESET, panelLeft + 345, panelTop + panelH - 30, 60, 20, "Nulstil"));

        // Initial selection setup
        String curSel = CelleScannerMod.config.majesticaSelectedWeaponId;
        if (curSel != null && !curSel.isEmpty()) {
            selectedWeapon = MajesticaWeapons.INSTANCE.getWeaponById(curSel);
        }

        updateFilter();
    }

    private void updateFilter() {
        filteredWeapons.clear();
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        List<MajesticaWeapons.Weapon> all = MajesticaWeapons.INSTANCE.getWeapons();

        for (MajesticaWeapons.Weapon w : all) {
            boolean catMatch = true;
            if (currentCategory == 1) { // Swords
                catMatch = w.id.contains("sword") || w.id.contains("blade") || w.id.contains("saber") || w.id.contains("katana") || w.id.contains("rapier");
            } else if (currentCategory == 2) { // Axes
                catMatch = w.id.contains("axe") || w.id.contains("scythe") || w.id.contains("halberd") || w.id.contains("cleaver");
            } else if (currentCategory == 3) { // Selected
                catMatch = w.id.equals(CelleScannerMod.config.majesticaSelectedWeaponId);
            }

            boolean qMatch = query.isEmpty() || w.id.toLowerCase().contains(query) || w.namePattern.toLowerCase().contains(query);
            if (catMatch && qMatch) {
                filteredWeapons.add(w);
            }
        }
        page = 0;
        rebuildWeaponGridButtons();
    }

    private void rebuildWeaponGridButtons() {
        // Remove old grid buttons (ids 1000+)
        List<GuiButton> toRemove = new ArrayList<GuiButton>();
        for (GuiButton b : this.buttonList) {
            if (b.id >= 1000) {
                toRemove.add(b);
            }
        }
        this.buttonList.removeAll(toRemove);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelLeft = cx - 210;
        int panelTop = cy - 122;

        int gridX = panelLeft + 15;
        int gridY = panelTop + 78;
        int cardW = 86;
        int cardH = 22;
        int cols = 2;

        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, filteredWeapons.size());

        for (int i = startIdx; i < endIdx; i++) {
            int slot = i - startIdx;
            int col = slot % cols;
            int row = slot / cols;
            int bx = gridX + col * (cardW + 8);
            int by = gridY + row * (cardH + 4);

            MajesticaWeapons.Weapon w = filteredWeapons.get(i);
            String title = formatName(w.id);
            if (title.length() > 13) {
                title = title.substring(0, 12) + "…";
            }
            this.buttonList.add(new StyledButton(1000 + i, bx, by, cardW, cardH, title));
        }
    }

    private String formatName(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.length() > 0) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.searchField != null && this.searchField.textboxKeyTyped(typedChar, keyCode)) {
            updateFilter();
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        int id = button.id;
        if (id == ID_RESET) {
            CelleScannerMod.config.majesticaSelectedWeaponId = "";
            CelleScannerMod.config.save();
            selectedWeapon = null;
            if (mc.thePlayer != null) {
                mc.thePlayer.playSound("random.pop", 1f, 1f);
            }
        } else if (id == ID_EQUIP_SEL && selectedWeapon != null) {
            CelleScannerMod.config.majesticaSelectedWeaponId = selectedWeapon.id;
            CelleScannerMod.config.save();
            if (mc.thePlayer != null) {
                mc.thePlayer.playSound("random.orb", 1f, 1f);
            }
        } else if (id == ID_TAB_ALL) {
            currentCategory = 0;
            updateFilter();
        } else if (id == ID_TAB_SWORDS) {
            currentCategory = 1;
            updateFilter();
        } else if (id == ID_TAB_AXES) {
            currentCategory = 2;
            updateFilter();
        } else if (id == ID_TAB_SELECTED) {
            currentCategory = 3;
            updateFilter();
        } else if (id == ID_PREV_PAGE) {
            if (page > 0) {
                page--;
                rebuildWeaponGridButtons();
            }
        } else if (id == ID_NEXT_PAGE) {
            if ((page + 1) * ITEMS_PER_PAGE < filteredWeapons.size()) {
                page++;
                rebuildWeaponGridButtons();
            }
        } else if (id >= 1000) {
            int idx = id - 1000;
            if (idx >= 0 && idx < filteredWeapons.size()) {
                selectedWeapon = filteredWeapons.get(idx);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelW = 420;
        int panelH = 245;
        int panelLeft = cx - panelW / 2;
        int panelTop = cy - panelH / 2;

        // Draw main container card
        drawRect(panelLeft, panelTop, panelLeft + panelW, panelTop + panelH, 0xEE0F1015);
        drawRect(panelLeft, panelTop, panelLeft + panelW, panelTop + 26, 0xFF1A1C23);

        // Header Title
        drawCenteredString(this.fontRendererObj, "§6§lBlades of Majestica §7- 3D Våben Vælger", cx, panelTop + 8, 0xFFFFFF);

        // Search label
        if (searchField != null) {
            searchField.drawTextBox();
            if (searchField.getText().isEmpty()) {
                this.fontRendererObj.drawString("Søg våben...", panelLeft + 20, panelTop + 40, 0x777777);
            }
        }

        // Left Grid Divider Line
        drawRect(panelLeft + 200, panelTop + 32, panelLeft + 201, panelTop + panelH - 12, 0x33FFFFFF);

        // Page info text
        int maxPages = Math.max(1, (int) Math.ceil((double) filteredWeapons.size() / ITEMS_PER_PAGE));
        drawCenteredString(this.fontRendererObj, (page + 1) + " / " + maxPages, panelLeft + 105, panelTop + panelH - 23, 0xAAAAAA);

        // Right Panel: 3D Spinning Weapon Preview Box
        int previewCenterX = panelLeft + 310;
        int previewCenterY = panelTop + 110;
        int previewBoxW = 180;
        int previewBoxH = 140;
        int pBoxLeft = previewCenterX - previewBoxW / 2;
        int pBoxTop = previewCenterY - previewBoxH / 2;

        drawRect(pBoxLeft, pBoxTop, pBoxLeft + previewBoxW, pBoxTop + previewBoxH, 0xFF161820);

        String activeEq = CelleScannerMod.config.majesticaSelectedWeaponId;
        if (selectedWeapon != null) {
            // Live 3D Spinning Weapon Model Render
            WeaponModelRenderer.renderSpinningWeapon(this.mc, selectedWeapon, previewCenterX, previewCenterY + 10, 48.0F, 1.0F);

            String title = formatName(selectedWeapon.id);
            drawCenteredString(this.fontRendererObj, "§e" + title, previewCenterX, pBoxTop + 8, 0xFFFFFF);
            drawCenteredString(this.fontRendererObj, "§7Mønster: " + selectedWeapon.namePattern, previewCenterX, pBoxTop + previewBoxH - 18, 0x888888);

            boolean isEquipped = selectedWeapon.id.equals(activeEq);
            if (isEquipped) {
                drawCenteredString(this.fontRendererObj, "§a✔ AKTIVT UDSTYRET", previewCenterX, pBoxTop + previewBoxH - 32, 0x55FF55);
            }
        } else {
            drawCenteredString(this.fontRendererObj, "§7Vælg et våben fra listen", previewCenterX, previewCenterY - 10, 0x777777);
            drawCenteredString(this.fontRendererObj, "§7for 3D spinning preview", previewCenterX, previewCenterY + 5, 0x555555);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
