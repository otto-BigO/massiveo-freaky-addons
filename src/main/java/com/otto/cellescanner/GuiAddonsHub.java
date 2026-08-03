package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The main menu for Massiveo's Freaky Addons - Apple-Style Motion Physics.
 * Replicates Apple UI guidelines: Staggered entry animations, smooth spring scroll physics,
 * and micro scale/fade transitions.
 */
public class GuiAddonsHub extends GuiScreen {

    private static final int ID_CLOSE = 1000;
    private static final int ID_BACK = 1001;
    private static final int ID_THEME = 1002;

    private static final int ROW_H = 24;
    private static final int BTN_H = 20;
    private static final int PANEL_W = 200;

    private final String category;

    private final List<String> levelCategories = new ArrayList<String>();
    private final List<MassiveoAddons.Addon> levelAddons = new ArrayList<MassiveoAddons.Addon>();
    private final List<MassiveoAddons.Addon> searchResults = new ArrayList<MassiveoAddons.Addon>();
    private final List<GuiButton> itemButtons = new ArrayList<GuiButton>();

    private GuiTextField searchField;

    // Scrolling & Motion state
    private float scroll = 0f;
    private int targetScroll = 0;
    private int maxScroll = 0;
    private String lastQuery = "";
    private String lastCategory = "";

    private final AnimationValue panelAnim = new AnimationValue(0f);
    private long openTimeMs = 0;
    private long lastFrameTimeMs = 0;

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
        Keyboard.enableRepeatEvents(true);
        AddonList.ensureRegistered();

        openTimeMs = System.currentTimeMillis();
        lastFrameTimeMs = System.currentTimeMillis();
        panelAnim.setValueInstant(0.0f);
        panelAnim.animateTo(1.0f, 260); // 260ms smooth panel open animation

        String carryText = (searchField != null) ? searchField.getText() : "";
        int cx = this.width / 2;
        int cy = this.height / 2;

        searchField = new GuiTextField(999, this.fontRendererObj, cx - PANEL_W / 2, cy - 116, PANEL_W, 16);
        searchField.setMaxStringLength(32);
        searchField.setText(carryText);
        searchField.setFocused(true);

        rebuild();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void clampScroll() {
        if (targetScroll < 0) targetScroll = 0;
        if (targetScroll > maxScroll) targetScroll = maxScroll;
    }

    private void updateButtonPositions() {
        int cy = this.height / 2;
        int startY = cy - 70 - (int) scroll;
        for (int i = 0; i < itemButtons.size(); i++) {
            GuiButton b = itemButtons.get(i);
            b.yPosition = startY + i * ROW_H;
        }
    }

    @Override
    public void updateScreen() {
        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, (now - lastFrameTimeMs) / 1000.0f);
        lastFrameTimeMs = now;

        // Smooth frame-rate independent spring scroll interpolation
        float diff = targetScroll - scroll;
        if (Math.abs(diff) > 0.05f) {
            scroll = EaseUtils.damp(scroll, targetScroll, 18.0f, dt);
            updateButtonPositions();
        } else {
            scroll = targetScroll;
            updateButtonPositions();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getDWheel();
        if (d > 0) {
            targetScroll -= ROW_H * 2; // Smooth 2-row glide up
        } else if (d < 0) {
            targetScroll += ROW_H * 2; // Smooth 2-row glide down
        }
        if (d != 0) {
            clampScroll();
        }
    }

    private void rebuild() {
        this.buttonList.clear();
        this.levelCategories.clear();
        this.levelAddons.clear();
        this.searchResults.clear();
        this.itemButtons.clear();

        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        boolean isSearching = !query.isEmpty();

        String catKey = category == null ? "" : category;
        if (!query.equals(lastQuery) || !catKey.equals(lastCategory)) {
            scroll = 0f;
            targetScroll = 0;
            lastQuery = query;
            lastCategory = catKey;
        }

        int left = this.width / 2 - PANEL_W / 2;
        int y = this.height / 2 - 70;

        if (isSearching) {
            for (String cat : MassiveoAddons.categories()) {
                for (MassiveoAddons.Addon a : MassiveoAddons.addonsIn(cat)) {
                    if (a.name().toLowerCase().contains(query) || a.description().toLowerCase().contains(query)) {
                        searchResults.add(a);
                    }
                }
            }

            int id = 0;
            for (MassiveoAddons.Addon addon : searchResults) {
                GuiButton b = new StyledButton(id++, left, y, PANEL_W, BTN_H, addonLabel(addon));
                this.buttonList.add(b);
                this.itemButtons.add(b);
                y += ROW_H;
            }
            this.buttonList.add(new StyledButton(ID_BACK, left, this.height / 2 + 110, PANEL_W, BTN_H, "Ryd søgning"));
        } else {
            if (category == null) {
                levelCategories.addAll(MassiveoAddons.categories());
                int id = 0;
                for (String cat : levelCategories) {
                    int count = MassiveoAddons.addonsIn(cat).size();
                    String label = catColor(cat) + cat + "  " + EnumChatFormatting.GRAY + "(" + count + ")";
                    GuiButton b = new StyledButton(id++, left, y, PANEL_W, BTN_H, label);
                    this.buttonList.add(b);
                    this.itemButtons.add(b);
                    y += ROW_H;
                }
                int halfW = (PANEL_W - 4) / 2;
                this.buttonList.add(new StyledButton(ID_THEME, left, this.height / 2 + 110, halfW, BTN_H, "🎨 Temaer"));
                this.buttonList.add(new StyledButton(ID_CLOSE, left + halfW + 4, this.height / 2 + 110, halfW, BTN_H, "Luk"));
            } else {
                levelAddons.addAll(MassiveoAddons.addonsIn(category));
                int id = 0;
                for (MassiveoAddons.Addon addon : levelAddons) {
                    GuiButton b = new StyledButton(id++, left, y, PANEL_W, BTN_H, addonLabel(addon));
                    this.buttonList.add(b);
                    this.itemButtons.add(b);
                    y += ROW_H;
                }
                this.buttonList.add(new StyledButton(ID_BACK, left, this.height / 2 + 110, PANEL_W, BTN_H, "< Tilbage"));
            }
        }

        int count = isSearching ? searchResults.size() : (category == null ? levelCategories.size() : levelAddons.size());
        int totalHeight = count * ROW_H;
        maxScroll = Math.max(0, totalHeight - 170);
        clampScroll();
        updateButtonPositions();
    }

    private static String addonLabel(MassiveoAddons.Addon addon) {
        String status = addon.isActive()
                ? Style.getAccentFormatting() + "[ TIL ]"
                : EnumChatFormatting.DARK_GRAY + "[ FRA ]";
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
        if ("Automation".equals(category)) {
            return EnumChatFormatting.LIGHT_PURPLE.toString();
        }
        return EnumChatFormatting.WHITE.toString();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_CLOSE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == ID_THEME) {
            CelleActions.openThemeEditor();
            return;
        }
        if (button.id == ID_BACK) {
            if (searchField != null && !searchField.getText().isEmpty()) {
                searchField.setText("");
                rebuild();
            } else {
                this.mc.displayGuiScreen(new GuiAddonsHub());
            }
            return;
        }
        if (button.id >= 0) {
            String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
            boolean isSearching = !query.isEmpty();

            if (isSearching) {
                if (button.id < searchResults.size()) {
                    MassiveoAddons.Addon addon = searchResults.get(button.id);
                    if (hasSubGui(addon)) {
                        addon.open();
                    } else {
                        addon.toggle();
                        button.displayString = addonLabel(addon);
                    }
                }
            } else {
                if (category == null && button.id < levelCategories.size()) {
                    this.mc.displayGuiScreen(new GuiAddonsHub(levelCategories.get(button.id)));
                } else if (category != null && button.id < levelAddons.size()) {
                    MassiveoAddons.Addon addon = levelAddons.get(button.id);
                    if (hasSubGui(addon)) {
                        addon.open();
                    } else {
                        addon.toggle();
                        button.displayString = addonLabel(addon);
                    }
                }
            }
        }
    }

    private boolean hasSubGui(MassiveoAddons.Addon addon) {
        if (addon == null) return false;
        String name = addon.name();
        return "Celle Scanner".equals(name) || "Celle Finder".equals(name) || "Mine Celler".equals(name)
                || "Bande ESP".equals(name) || "PvP Mine".equals(name) || "Auto Mine".equals(name)
                || "Kiste Organisering".equals(name) || "Rustnings-HUD".equals(name) || "Spiller Info".equals(name)
                || "Item Værdi".equals(name) || "Prisguide".equals(name) || "Armour Skins".equals(name)
                || "Skralde-Filter".equals(name);
    }

    private int hoveredItem(int mouseX, int mouseY) {
        int cy = this.height / 2;
        if (mouseY < cy - 80 || mouseY > cy + 100) {
            return -1;
        }
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

        // Calculate Apple-style panel slide-up & fade transition
        float pVal = panelAnim.getValue();
        float offsetY = (1.0f - pVal) * 12.0f; // Slide up 12px

        // Ambient field behind the card. Drawn before the panel transform so the
        // motes stay put while the card slides in, which sells the depth.
        int halfW = Style.cardHalfWidth(this.width);
        int halfH = Style.cardHalfHeight(this.height);
        int ccx = this.width / 2;
        int ccy = this.height / 2;
        AmbientParticleEngine.INSTANCE.renderBehind(
                this.width, this.height,
                ccx - halfW, ccy - halfH, ccx + halfW, ccy + halfH,
                mouseX, mouseY);

        // Slow breathing halo around the card edge, fading in with the panel.
        float breathe = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1400.0);
        Style.cardGlow(this.width, this.height, pVal * (0.45f + 0.55f * breathe));

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -offsetY, 0.0f);

        Style.card(this.width, this.height);

        int cx = this.width / 2;
        int cy = this.height / 2;

        int titleY = cy - 138;
        int accent = Style.getAccentColor();

        // Render larger scaled title (1.4x scale)
        GL11.glPushMatrix();
        float titleScale = 1.4f;
        GL11.glScalef(titleScale, titleScale, 1.0f);
        int scaledTitleY = (int) (titleY / titleScale);
        int scaledCx = (int) (cx / titleScale);

        int titleW = this.fontRendererObj.getStringWidth(MassiveoAddons.BRAND);
        this.fontRendererObj.drawString(MassiveoAddons.BRAND, scaledCx - titleW / 2, scaledTitleY, accent, true);
        GL11.glPopMatrix();

        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        boolean isSearching = !query.isEmpty();
        String subtitle = isSearching ? "Søgeresultater" : (category == null ? "Vælg en kategori" : catColor(category) + category);
        drawCenteredString(this.fontRendererObj, subtitle, cx, cy - 92, 0xAAAAAA);

        searchField.drawTextBox();
        if (searchField.getText().isEmpty() && !searchField.isFocused()) {
            drawString(this.fontRendererObj, EnumChatFormatting.ITALIC + "Skriv for at s\u00f8ge...", cx - PANEL_W / 2 + 4, cy - 112, 0x888888);
        }

        // Render viewport with scissoring & Apple staggered item card entrance
        ScaledResolution sr = new ScaledResolution(this.mc);
        int scale = sr.getScaleFactor();
        int scissorX = (cx - PANEL_W / 2) * scale;
        int scissorY = (this.mc.displayHeight - (cy + 100) * scale);
        int scissorW = PANEL_W * scale;
        int scissorH = 180 * scale;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorW, scissorH);

        long now = System.currentTimeMillis();

        // Render item buttons with staggered delay (40ms per row)
        for (int i = 0; i < itemButtons.size(); i++) {
            GuiButton b = itemButtons.get(i);
            long itemStaggerDelay = i * 40L;
            long elapsedSinceOpen = now - openTimeMs;

            if (elapsedSinceOpen >= itemStaggerDelay) {
                float itemProgress = Math.min(1.0f, (float) (elapsedSinceOpen - itemStaggerDelay) / 180.0f);
                float itemEase = EaseUtils.easeOutCubic(itemProgress);
                float itemOffsetY = (1.0f - itemEase) * 8.0f; // Slide 8px up

                GL11.glPushMatrix();
                GL11.glTranslatef(0.0f, itemOffsetY, 0.0f);
                b.drawButton(this.mc, mouseX, (int) (mouseY - itemOffsetY));
                GL11.glPopMatrix();
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Render other buttons (back/close/theme) outside scissor
        for (GuiButton b : this.buttonList) {
            if (b.id == ID_CLOSE || b.id == ID_BACK || b.id == ID_THEME) {
                b.drawButton(this.mc, mouseX, mouseY);
            }
        }

        // Draw scrollbar if content overflows
        if (maxScroll > 0) {
            int totalHeight = (isSearching ? searchResults.size() : (category == null ? levelCategories.size() : levelAddons.size())) * ROW_H;
            double visibleRatio = 170.0 / totalHeight;
            int trackH = 172;
            int thumbH = (int) Math.max(15, visibleRatio * trackH);
            double scrollRatio = (double) scroll / maxScroll;
            int thumbY = (cy - 76) + (int) (scrollRatio * (trackH - thumbH));

            drawRect(cx + PANEL_W / 2 + 4, cy - 76, cx + PANEL_W / 2 + 6, cy + 96, 0x33FFFFFF);
            Style.roundedRect(cx + PANEL_W / 2 + 3, thumbY, cx + PANEL_W / 2 + 7, thumbY + thumbH, Style.ACCENT);
        }

        int hovered = hoveredItem(mouseX, mouseY);
        if (hovered >= 0) {
            String hint;
            if (isSearching) {
                hint = searchResults.get(hovered).description();
            } else if (category == null) {
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
        drawGear(gearX, gearY, GEAR_SIZE, gearHover ? 0xFFFFFFFF : 0xFFB0B0B8, 0xFF15151A, gearHover);
        if (gearHover) {
            String t = "Indstillinger";
            drawString(this.fontRendererObj, EnumChatFormatting.GRAY + t,
                    gearX - this.fontRendererObj.getStringWidth(t) - 6, gearY + 1, 0xAAAAAA);
        }

        GL11.glPopMatrix();

        ClickParticleEngine.INSTANCE.renderAndUpdate();
    }

    private final AnimationValue gearRotateAnim = new AnimationValue(0.0f);

    private void drawGear(int left, int top, int s, int body, int hole, boolean hovered) {
        if (hovered) {
            gearRotateAnim.animateTo(90.0f, 200);
        } else {
            gearRotateAnim.animateTo(0.0f, 240);
        }
        float angle = gearRotateAnim.getValue();

        float cx = left + s / 2.0f;
        float cy = top + s / 2.0f;

        GL11.glPushMatrix();
        GL11.glTranslatef(cx, cy, 0.0f);
        GL11.glRotatef(angle, 0.0f, 0.0f, 1.0f);
        GL11.glTranslatef(-cx, -cy, 0.0f);

        int icx = left + s / 2;
        int icy = top + s / 2;
        drawRect(icx - 1, top - 2, icx + 2, top + 1, body);
        drawRect(icx - 1, top + s - 1, icx + 2, top + s + 2, body);
        drawRect(left - 2, icy - 1, left + 1, icy + 2, body);
        drawRect(left + s - 1, icy - 1, left + s + 2, icy + 2, body);
        Style.roundedRect(left, top, left + s, top + s, body);
        drawRect(icx - 1, icy - 1, icx + 2, icy + 2, hole);

        GL11.glPopMatrix();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int cy = this.height / 2;
        boolean clickInViewport = (mouseY >= cy - 80 && mouseY <= cy + 100);

        if (mouseButton == 1) {
            if (clickInViewport) {
                for (GuiButton b : this.buttonList) {
                    if (b.id >= 0 && b.id < 1000 && b.mousePressed(this.mc, mouseX, mouseY) && b.enabled) {
                        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
                        boolean isSearching = !query.isEmpty();

                        b.playPressSound(this.mc.getSoundHandler());
                        if (isSearching && b.id < searchResults.size()) {
                            MassiveoAddons.Addon addon = searchResults.get(b.id);
                            addon.toggle();
                            b.displayString = addonLabel(addon);
                        } else if (!isSearching && category != null && b.id < levelAddons.size()) {
                            MassiveoAddons.Addon addon = levelAddons.get(b.id);
                            addon.toggle();
                            b.displayString = addonLabel(addon);
                        }
                        return;
                    }
                }
            }
        }

        if (!clickInViewport) {
            for (GuiButton b : this.buttonList) {
                if (b.id >= 0 && b.id < 1000) {
                    b.enabled = false;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (!clickInViewport) {
            for (GuiButton b : this.buttonList) {
                if (b.id >= 0 && b.id < 1000) {
                    b.enabled = true;
                }
            }
        }

        if (searchField != null) {
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0 && mouseX >= gearX - 3 && mouseX <= gearX + GEAR_SIZE + 3
                && mouseY >= gearY - 3 && mouseY <= gearY + GEAR_SIZE + 3) {
            this.mc.displayGuiScreen(new GuiGuiSettings());
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (searchField != null && searchField.textboxKeyTyped(typedChar, keyCode)) {
            rebuild();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
