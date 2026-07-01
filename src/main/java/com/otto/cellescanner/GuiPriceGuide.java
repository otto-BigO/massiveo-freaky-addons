package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game browser for the FreakyVille price guide (fetched live by PriceGuide).
 * Drills down block -> category -> price groups, paginated, with loading/retry
 * states. Opened from the hub.
 */
public class GuiPriceGuide extends GuiScreen {

    private static final int ID_PREV = 90;
    private static final int ID_NEXT = 91;
    private static final int ID_BACK = 92;
    private static final int ID_REFRESH = 93;

    private static final int PAGE_SIZE = 6;
    private static final int PANEL_W = 240;
    private static final int ROW_H = 22;
    private static final int BTN_H = 20;
    private static final int HALF = (PANEL_W - 4) / 2;

    private Integer blockId = null; // null = blocks level
    private Integer catId = null;   // set = groups level
    private int page = 0;
    private boolean built = false;
    private boolean triedFetch = false;

    private final List<PriceGuide.Cat> pageCats = new ArrayList<PriceGuide.Cat>();
    private List<PriceGuide.Group> curGroups = null;

    // Layout anchors computed once per initGui so drawScreen lines up exactly.
    private int listStartY;
    private int listBottom;
    private int pageInfoY;
    private int curPages = 1;

    private boolean groupsLevel() {
        return catId != null;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.pageCats.clear();
        this.curGroups = null;

        if (!triedFetch) {
            triedFetch = true;
            PriceGuide.fetch(false);
        }

        int cy = this.height / 2;
        int left = this.width / 2 - PANEL_W / 2;
        listStartY = cy - 64;
        listBottom = listStartY + PAGE_SIZE * ROW_H;
        pageInfoY = listBottom + 3;

        if (!PriceGuide.isLoaded()) {
            built = false;
            this.buttonList.add(new StyledButton(ID_BACK, left, cy + 30, HALF, BTN_H, "Tilbage"));
            this.buttonList.add(new StyledButton(ID_REFRESH, left + HALF + 4, cy + 30, HALF, BTN_H, "Prøv igen"));
            return;
        }
        built = true;

        int count;
        if (groupsLevel()) {
            curGroups = PriceGuide.groupsIn(catId);
            count = curGroups.size();
        } else {
            List<PriceGuide.Cat> list = (blockId == null) ? PriceGuide.topBlocks() : PriceGuide.categoriesIn(blockId);
            count = list.size();
            curPages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
            page = Math.max(0, Math.min(page, curPages - 1));
            int start = page * PAGE_SIZE;
            int end = Math.min(count, start + PAGE_SIZE);
            int y = listStartY;
            for (int i = start; i < end; i++) {
                PriceGuide.Cat c = list.get(i);
                pageCats.add(c);
                this.buttonList.add(new StyledButton(pageCats.size() - 1, left, y, PANEL_W, BTN_H, c.name == null ? "?" : c.name));
                y += ROW_H;
            }
        }

        curPages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, curPages - 1));

        int navY = listBottom + 14;
        if (curPages > 1) {
            this.buttonList.add(new StyledButton(ID_PREV, left, navY, HALF, BTN_H, "< Forrige"));
            this.buttonList.add(new StyledButton(ID_NEXT, left + HALF + 4, navY, HALF, BTN_H, "Næste >"));
            navY += BTN_H + 4;
        }
        this.buttonList.add(new StyledButton(ID_BACK, left, navY, HALF, BTN_H, "Tilbage"));
        this.buttonList.add(new StyledButton(ID_REFRESH, left + HALF + 4, navY, HALF, BTN_H, "Genindlæs"));
    }

    @Override
    public void updateScreen() {
        if (PriceGuide.isLoaded() && !built) {
            initGui();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 0 && button.id < pageCats.size()) {
            PriceGuide.Cat c = pageCats.get(button.id);
            if (blockId == null) {
                blockId = c.id;
            } else {
                catId = c.id;
            }
            page = 0;
            initGui();
            return;
        }
        switch (button.id) {
            case ID_PREV:
                page--;
                initGui();
                break;
            case ID_NEXT:
                page++;
                initGui();
                break;
            case ID_REFRESH:
                PriceGuide.fetch(true);
                built = false;
                initGui();
                break;
            case ID_BACK:
                if (catId != null) {
                    catId = null;
                    page = 0;
                    initGui();
                } else if (blockId != null) {
                    blockId = null;
                    page = 0;
                    initGui();
                } else {
                    CelleActions.openHub();
                }
                break;
            default:
                break;
        }
    }

    private String breadcrumb() {
        if (blockId == null) {
            return "Vælg blok/server";
        }
        String block = PriceGuide.nameOf(blockId);
        if (catId == null) {
            return block + "  >  vælg kategori";
        }
        return block + "  >  " + PriceGuide.nameOf(catId);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int cx = this.width / 2;
        int cy = this.height / 2;
        drawCenteredString(this.fontRendererObj, "Prisguide", cx, cy - 92, 0x55FFFF);

        if (PriceGuide.isLoading() && !PriceGuide.isLoaded()) {
            drawCenteredString(this.fontRendererObj, "Henter prisguide fra freakyville.dk ...", cx, cy - 10, 0xAAAAAA);
        } else if (PriceGuide.isFailed() && !PriceGuide.isLoaded()) {
            drawCenteredString(this.fontRendererObj, "Kunne ikke hente prisguiden.", cx, cy - 16, 0xFF5555);
            String err = PriceGuide.getError();
            if (err != null) {
                drawCenteredString(this.fontRendererObj, err, cx, cy - 4, 0x888888);
            }
        } else if (PriceGuide.isLoaded()) {
            drawCenteredString(this.fontRendererObj, breadcrumb(), cx, cy - 80, 0xAAAAAA);

            if (groupsLevel() && curGroups != null) {
                int left = cx - PANEL_W / 2;
                int start = page * PAGE_SIZE;
                int end = Math.min(curGroups.size(), start + PAGE_SIZE);
                int y = listStartY + 4;
                for (int i = start; i < end; i++) {
                    PriceGuide.Group g = curGroups.get(i);
                    String name = g.name == null ? "?" : g.name;
                    String val = PriceGuide.valueText(g);
                    int vw = this.fontRendererObj.getStringWidth(val);
                    // Trim the name if it would collide with the right-aligned value.
                    int maxNameW = PANEL_W - vw - 10;
                    name = trimToWidth(name, maxNameW);
                    drawString(this.fontRendererObj, name, left, y, 0xFFFFFF);
                    drawString(this.fontRendererObj, val, left + PANEL_W - vw, y, 0x55FF55);
                    y += ROW_H;
                }
            }

            if (curPages > 1) {
                drawCenteredString(this.fontRendererObj, "Side " + (page + 1) + "/" + curPages, cx, pageInfoY, 0x888888);
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String trimToWidth(String s, int maxW) {
        if (this.fontRendererObj.getStringWidth(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && this.fontRendererObj.getStringWidth(s + "..") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "..";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
