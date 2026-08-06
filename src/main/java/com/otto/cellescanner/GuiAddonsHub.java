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

    /**
     * The "aktive" panel down the right-hand side: every addon currently
     * switched on, wherever it lives in the categories, so the ones actually
     * doing something are reachable without hunting for them.
     */
    private static final int ACTIVE_BASE = 2000;
    /** Same width, row height and button height as the main list, so it reads as the same thing. */
    private static final int ACTIVE_W_FULL = PANEL_W;
    private static final int ACTIVE_W_NARROW = 140;
    private int activeW = PANEL_W;
    private static final int ACTIVE_ROW = ROW_H;
    private static final int ACTIVE_MAX_ROWS = 9;
    private final List<MassiveoAddons.Addon> activeAddons = new ArrayList<MassiveoAddons.Addon>();
    private int activeHidden = 0;
    private int activeScroll = 0;
    /**
     * The active count the panel was last built against, so "needs rebuilding"
     * never depends on how many rows ended up on screen.
     *
     * Comparing the live count to the number of rows meant a panel that could
     * not fit, and therefore built no rows at all, looked permanently out of
     * date. It asked for a rebuild every tick, which restarted the menu's open
     * animation every tick.
     */
    private int activeBuiltFor = -1;
    private int activeX = 0;
    private int activeTopY = 0;
    private boolean showActive = false;
    /**
     * Rebuild the screen on the next tick rather than mid-click.
     *
     * GuiScreen.mouseClicked keeps walking the button list after calling
     * actionPerformed. Rebuilding from inside that means the row below the one
     * just switched off slides up under the cursor and gets pressed as well, so
     * one click would switch off two addons.
     */
    private boolean pendingRebuild = false;
    /**
     * Opacity of the active panel, so it eases in when something is switched on
     * and eases out when the last one goes off, rather than appearing and
     * vanishing between two frames.
     */
    private final AnimationValue activeAlpha = new AnimationValue(0f);

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
        if (pendingRebuild) {
            pendingRebuild = false;
            initGui();
        }

        // The active set can change from outside this screen, so it is checked
        // each tick instead of only when something here is clicked.
        int live = countActive();
        if (live > 0) {
            activeAlpha.animateTo(1.0f, 220);
            if (live != activeBuiltFor) {
                pendingRebuild = true;
            }
        } else {
            activeAlpha.animateTo(0.0f, 220);
            // The rows are kept until the fade finishes, so they go out with the
            // panel instead of blinking off while it is still visible.
            if (!activeAddons.isEmpty() && activeAlpha.getValue() <= 0.02f) {
                pendingRebuild = true;
            }
            activeBuiltFor = 0;
        }
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
        if (d == 0) {
            return;
        }

        // Whichever list the pointer is over is the one that scrolls, so the
        // main list does not move while you are reading the panel.
        if (showActive && pointerOverActive()) {
            int maxOffset = Math.max(0, activeAddons.size() - ACTIVE_MAX_ROWS);
            int next = Math.max(0, Math.min(maxOffset, activeScroll + (d > 0 ? -1 : 1)));
            if (next != activeScroll) {
                activeScroll = next;
                pendingRebuild = true;
            }
            return;
        }

        if (d > 0) {
            targetScroll -= ROW_H * 2; // Smooth 2-row glide up
        } else {
            targetScroll += ROW_H * 2; // Smooth 2-row glide down
        }
        clampScroll();
    }

    /** Whether the mouse is inside the active panel right now. */
    private boolean pointerOverActive() {
        int mx = Mouse.getX() * this.width / this.mc.displayWidth;
        int my = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
        int rows = Math.min(activeAddons.size(), ACTIVE_MAX_ROWS);
        int bottom = activeTopY + rows * ACTIVE_ROW + 12;
        return mx >= activeX - 6 && mx <= activeX + activeW + 6
                && my >= activeTopY - 20 && my <= bottom;
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

        buildActivePanel();
    }

    /**
     * Lays out the active-addon panel beside the card.
     *
     * Only when there is genuinely room for it: at a large GUI scale the screen
     * is narrow enough that this would sit on top of the card, and a panel
     * overlapping the thing it is meant to complement is worse than no panel.
     */
    private void buildActivePanel() {
        activeAddons.clear();
        activeHidden = 0;
        showActive = false;
        // Recorded before any early return, so every path leaves the panel
        // agreeing with what it was built for.
        activeBuiltFor = countActive();

        int cx = this.width / 2;
        int cy = this.height / 2;
        activeX = cx + Style.cardHalfWidth(this.width) + 10;

        // As wide as the main list when there is room, narrower when there is
        // not, and only dropped when even that will not fit. It used to be one
        // fixed width, so widening it to match the list pushed it off narrower
        // screens entirely.
        activeW = ACTIVE_W_FULL;
        if (activeX + activeW + 6 > this.width) {
            activeW = ACTIVE_W_NARROW;
        }
        if (activeX + activeW + 6 > this.width) {
            return;
        }

        for (String cat : MassiveoAddons.categories()) {
            for (MassiveoAddons.Addon a : MassiveoAddons.addonsIn(cat)) {
                try {
                    if (a.isActive() && a.showInActive()) {
                        activeAddons.add(a);
                    }
                } catch (Throwable ignored) {
                    // An addon that cannot report its own state is not worth
                    // taking the whole menu down for.
                }
            }
        }
        if (activeAddons.isEmpty()) {
            return;
        }

        showActive = true;
        activeTopY = cy - 104;

        int shown = Math.min(activeAddons.size(), ACTIVE_MAX_ROWS);
        int maxOffset = Math.max(0, activeAddons.size() - ACTIVE_MAX_ROWS);
        if (activeScroll > maxOffset) activeScroll = maxOffset;
        if (activeScroll < 0) activeScroll = 0;
        activeHidden = maxOffset - activeScroll;

        // Scrolling moves which addons the rows show rather than moving the
        // rows, so nothing can ever slide out past the edge of the panel.
        int y = activeTopY;
        for (int i = 0; i < shown; i++) {
            MassiveoAddons.Addon a = activeAddons.get(activeScroll + i);
            this.buttonList.add(new StyledButton(ACTIVE_BASE + i, activeX, y, activeW, BTN_H,
                    addonLabel(a)));
            y += ACTIVE_ROW;
        }
    }

    /** How many addons report themselves as on right now. */
    private static int countActive() {
        int n = 0;
        for (String cat : MassiveoAddons.categories()) {
            for (MassiveoAddons.Addon a : MassiveoAddons.addonsIn(cat)) {
                try {
                    if (a.isActive() && a.showInActive()) {
                        n++;
                    }
                } catch (Throwable ignored) {
                    // Same reasoning as buildActivePanel: one broken addon does
                    // not get to break the menu.
                }
            }
        }
        return n;
    }

    /** Keeps a name inside the narrow panel rather than letting it run over the edge. */
    private static String trimTo(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /**
     * A click in the active panel. Left opens the addon's settings when it has
     * any, right always toggles, which matches how the main list behaves.
     */
    private boolean handleActiveClick(int id, boolean toggleInstead) {
        int index = activeScroll + (id - ACTIVE_BASE);
        if (index < 0 || index >= activeAddons.size()) {
            return false;
        }
        // A row too faint to read should not be clickable either.
        if (activeAlpha.getValue() < 0.5f) {
            return false;
        }
        MassiveoAddons.Addon addon = activeAddons.get(index);
        if (toggleInstead || !addon.hasSettings()) {
            addon.toggle();
            // Switching one off takes it out of the panel, so the rows have to
            // be rebuilt. Deferred, for the reason on pendingRebuild.
            pendingRebuild = true;
        } else {
            addon.open();
        }
        return true;
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
        if (button.id >= ACTIVE_BASE) {
            handleActiveClick(button.id, false);
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

    /**
     * This used to be a hardcoded list of addon names. Every addon added since
     * then whose tile should open a settings screen just toggled instead, which
     * is why eleven screens were unreachable from the menu, and two names on the
     * list had no screen behind them at all. The addon declares it now.
     */
    private boolean hasSubGui(MassiveoAddons.Addon addon) {
        return addon != null && addon.hasSettings();
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

        // Backdrop for the active-addon panel, drawn before super.drawScreen so
        // the buttons in it land on top.
        float aVal = activeAlpha.getValue();
        if (showActive && aVal > 0.01f) {
            int aBits = ((int) (aVal * 255.0f) & 0xFF) << 24;
            int rows = Math.min(activeAddons.size(), ACTIVE_MAX_ROWS);
            int bottom = activeTopY + rows * ACTIVE_ROW + (activeHidden > 0 || activeScroll > 0 ? 12 : 2);

            // The backdrop fades with everything else, so the whole panel eases
            // in and out as one thing.
            // Same card treatment as the rest of the menu, so it sits in the
            // interface rather than on top of it. A solid slab was tried to stop
            // the scoreboard reading through, and it just looked like a
            // different program. The rows fill the panel now, which does the
            // same job without breaking the style.
            Style.panel(activeX - 6, activeTopY - 20, activeX + activeW + 6, bottom, aVal);

            drawString(this.fontRendererObj,
                    EnumChatFormatting.BOLD + "Aktive " + EnumChatFormatting.GRAY + "(" + activeAddons.size() + ")",
                    activeX, activeTopY - 15, aBits | (Style.getAccentColor() & 0x00FFFFFF));
            if (activeHidden > 0 || activeScroll > 0) {
                String more = (activeScroll > 0 ? "▲ " + activeScroll + "  " : "")
                        + (activeHidden > 0 ? "▼ " + activeHidden : "");
                drawString(this.fontRendererObj,
                        EnumChatFormatting.DARK_GRAY + more.trim(),
                        activeX, activeTopY + rows * ACTIVE_ROW + 1, aBits | 0x888888);
            }
        }

        // The rows fade with the panel, and stop taking clicks once they are
        // faint enough not to look present.
        for (Object o : this.buttonList) {
            GuiButton gb = (GuiButton) o;
            if (gb.id >= ACTIVE_BASE && gb instanceof StyledButton) {
                // Left enabled, so it keeps its normal colours while fading.
                // Whether a click counts is decided in handleActiveClick.
                ((StyledButton) gb).setAlpha(aVal);
            }
        }

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

        // Render other buttons (back/close/theme) outside scissor.
        // The active-panel rows go here too. This screen never calls
        // super.drawScreen for its buttons, it draws them by hand, so a button
        // that is in neither list is simply never drawn.
        for (GuiButton b : this.buttonList) {
            if (b.id == ID_CLOSE || b.id == ID_BACK || b.id == ID_THEME || b.id >= ACTIVE_BASE) {
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
            // The panel sits outside the scrolling viewport, so it is checked
            // first and on its own.
            int hitActive = -1;
            for (GuiButton b : this.buttonList) {
                if (b.id >= ACTIVE_BASE && b.mousePressed(this.mc, mouseX, mouseY) && b.enabled) {
                    b.playPressSound(this.mc.getSoundHandler());
                    hitActive = b.id;
                    break;
                }
            }
            if (hitActive >= 0) {
                handleActiveClick(hitActive, true);
                return;
            }
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
