package com.otto.cellescanner;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Gange" screen: every remembered celle grouped by its gang (corridor), each
 * with its live countdown. Gange are filled in automatically by GangInfo, which
 * learns them from "/ce info". Click a celle to point the Celle Finder at it.
 *
 * SHELVED: the Gange addon is intentionally parked. Its hub tile is commented
 * out in AddonList, so this screen is currently unreachable. Kept for when the
 * addon is picked up again - do not delete.
 */
public class GuiGange extends GuiScreen {

    private static final int ID_BACK = 0;
    private static final int ID_TOGGLE = 2;

    private static final int ROW_H = 11;
    private static final int PANEL_W = 240;
    private static final String UNKNOWN = "Ukendt gang";

    private GuiButton toggleButton;
    private final List<Row> rows = new ArrayList<Row>();
    private int scroll = 0;
    private int listTop;
    private int listBottom;
    private int gangCount = 0;
    private int celleCount = 0;
    private String flash = "";

    private static final class Row {
        final boolean header;
        final String text;
        final int color;
        final String celleId; // null for headers

        Row(boolean header, String text, int color, String celleId) {
            this.header = header;
            this.text = text;
            this.color = color;
            this.celleId = celleId;
        }
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        listTop = 40;
        // Room for two button rows: the detector toggle, then back/refresh.
        listBottom = this.height - 58;

        int left = this.width / 2 - PANEL_W / 2;
        this.buttonList.add(toggleButton = new StyledButton(ID_TOGGLE, left, this.height - 52, PANEL_W, 20, toggleLabel()));
        this.buttonList.add(new StyledButton(ID_BACK, left, this.height - 28, PANEL_W, 20, "Tilbage"));

        buildRows();
    }

    private String toggleLabel() {
        return "Gang-detektor: " + (CelleScannerMod.config.gangAutoQuery ? "Til" : "Fra");
    }

    @Override
    public void updateScreen() {
        buildRows(); // keeps the live timers moving
        clampScroll();
    }

    private void buildRows() {
        rows.clear();
        Map<String, CellePositions.Entry> all = CellePositions.snapshot();

        TreeMap<String, List<Map.Entry<String, CellePositions.Entry>>> byGang =
                new TreeMap<String, List<Map.Entry<String, CellePositions.Entry>>>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, CellePositions.Entry> e : all.entrySet()) {
            CellePositions.Entry entry = e.getValue();
            // A gang read straight off a right-clicked sign is exact; otherwise
            // resolve it from the id-range map (config/massiveo_gang_ranges.json).
            String gang = entry.gang;
            if (gang == null || gang.isEmpty()) {
                String id = entry.displayId != null && !entry.displayId.isEmpty() ? entry.displayId : e.getKey();
                gang = GangRanges.gangFor(id);
            }
            String key = (gang != null && !gang.isEmpty()) ? gang : UNKNOWN;
            List<Map.Entry<String, CellePositions.Entry>> list = byGang.get(key);
            if (list == null) {
                list = new ArrayList<Map.Entry<String, CellePositions.Entry>>();
                byGang.put(key, list);
            }
            list.add(e);
        }

        boolean hasUnknown = byGang.containsKey(UNKNOWN);
        List<String> gangs = new ArrayList<String>(byGang.keySet());
        gangs.remove(UNKNOWN);
        Collections.sort(gangs, String.CASE_INSENSITIVE_ORDER);
        if (hasUnknown) {
            gangs.add(UNKNOWN); // always last
        }

        gangCount = hasUnknown ? gangs.size() - 1 : gangs.size();
        celleCount = all.size();

        for (String gang : gangs) {
            List<Map.Entry<String, CellePositions.Entry>> list = byGang.get(gang);
            Collections.sort(list, new Comparator<Map.Entry<String, CellePositions.Entry>>() {
                @Override
                public int compare(Map.Entry<String, CellePositions.Entry> a, Map.Entry<String, CellePositions.Entry> b) {
                    return Long.compare(a.getValue().liveRemainingSeconds(), b.getValue().liveRemainingSeconds());
                }
            });

            int headColor = UNKNOWN.equals(gang) ? 0xFF8888 : 0x4BE08C;
            String head = gang + "  (" + list.size() + ")";
            String span = observedRange(list);
            if (!span.isEmpty()) {
                head += "  " + EnumChatFormatting.GRAY + span;
            }
            rows.add(new Row(true, head, headColor, null));
            for (Map.Entry<String, CellePositions.Entry> e : list) {
                CellePositions.Entry entry = e.getValue();
                String id = entry.displayId != null && !entry.displayId.isEmpty() ? entry.displayId : e.getKey();
                long live = entry.liveRemainingSeconds();
                String time = live <= 0 ? "udløbet" : fmt(live);
                rows.add(new Row(false, "  " + id + "   " + time, 0xD0D0D0, id));
            }
        }
    }

    // Letter prefix (level) + number of a celle id, for the observed-span readout.
    private static final Pattern ID = Pattern.compile("([A-Za-z]{1,5})([0-9]{1,7})");

    /**
     * The id span actually seen for a gang, per level letter, e.g. "B286-B494"
     * (or "B286-B494, A100-A150" if it straddles levels). This is exactly the
     * boundary info to hand off for a gang range - right-click the two ends of a
     * gang and this shows the min-max to record.
     */
    private static String observedRange(List<Map.Entry<String, CellePositions.Entry>> list) {
        TreeMap<String, long[]> spans = new TreeMap<String, long[]>();
        for (Map.Entry<String, CellePositions.Entry> e : list) {
            CellePositions.Entry entry = e.getValue();
            String id = entry.displayId != null && !entry.displayId.isEmpty() ? entry.displayId : e.getKey();
            if (id == null) {
                continue;
            }
            Matcher m = ID.matcher(id.trim());
            if (!m.matches()) {
                continue;
            }
            String pre = m.group(1).toUpperCase(Locale.ROOT);
            long n;
            try {
                n = Long.parseLong(m.group(2));
            } catch (NumberFormatException ex) {
                continue;
            }
            long[] mm = spans.get(pre);
            if (mm == null) {
                spans.put(pre, new long[]{n, n});
            } else {
                if (n < mm[0]) {
                    mm[0] = n;
                }
                if (n > mm[1]) {
                    mm[1] = n;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, long[]> s : spans.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            long lo = s.getValue()[0];
            long hi = s.getValue()[1];
            sb.append(s.getKey()).append(lo);
            if (hi != lo) {
                sb.append("-").append(s.getKey()).append(hi);
            }
        }
        return sb.toString();
    }

    private void clampScroll() {
        int total = rows.size() * ROW_H;
        int viewport = listBottom - listTop;
        int max = Math.max(0, total - viewport);
        if (scroll < 0) {
            scroll = 0;
        } else if (scroll > max) {
            scroll = max;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getDWheel();
        if (d > 0) {
            scroll -= ROW_H * 3;
        } else if (d < 0) {
            scroll += ROW_H * 3;
        }
        if (d != 0) {
            clampScroll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || mouseY < listTop || mouseY > listBottom) {
            return;
        }
        int idx = (mouseY - (listTop - scroll)) / ROW_H;
        if (idx >= 0 && idx < rows.size()) {
            Row r = rows.get(idx);
            if (!r.header && r.celleId != null) {
                CelleActions.setFinderTarget(r.celleId);
                this.mc.displayGuiScreen(null);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_BACK:
                this.mc.displayGuiScreen(new GuiCelleMenu());
                break;
            case ID_TOGGLE:
                CelleActions.toggleGangAutoQuery();
                toggleButton.displayString = toggleLabel();
                flash = CelleScannerMod.config.gangAutoQuery
                        ? "Gang-detektor til - højreklik celle-skilte for at hente gang."
                        : "Gang-detektor slået fra.";
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
        drawCenteredString(this.fontRendererObj, "Celle Scanner - Gange", cx, 16, 0xFFFFFF);
        drawCenteredString(this.fontRendererObj, gangCount + " gange, " + celleCount + " celler", cx, 27, 0xAAAAAA);

        int left = this.width / 2 - PANEL_W / 2;
        if (rows.isEmpty()) {
            drawCenteredString(this.fontRendererObj, "Ingen celler scannet endnu - gå rundt i fængslet.", cx, listTop + 20, 0xAAAAAA);
            drawCenteredString(this.fontRendererObj, "Højreklik et celle-skilt for at hente dets gang.", cx, listTop + 32, 0x888888);
        } else {
            int y = listTop - scroll;
            boolean first = true;
            for (Row r : rows) {
                if (y + ROW_H >= listTop && y <= listBottom) {
                    if (r.header && !first) {
                        drawRect(left, y - 1, left + PANEL_W, y, 0x33FFFFFF);
                    }
                    this.fontRendererObj.drawStringWithShadow(r.text, left, y, r.color);
                }
                y += ROW_H;
                first = false;
            }
        }

        if (!flash.isEmpty()) {
            drawCenteredString(this.fontRendererObj, flash, cx, listBottom + 2, 0x88FF88);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Compact days/hours/minutes, e.g. "13d 9t 20m" - CelleHud.formatDuration only goes to hours. */
    private static String fmt(long seconds) {
        long d = seconds / 86400L;
        long h = (seconds % 86400L) / 3600L;
        long m = (seconds % 3600L) / 60L;
        if (d > 0) {
            return d + "d " + h + "t " + m + "m";
        }
        if (h > 0) {
            return h + "t " + m + "m";
        }
        return m + "m";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
