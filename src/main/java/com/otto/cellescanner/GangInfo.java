package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learns which "gang" (corridor) each remembered celle sits on. The sign only
 * shows status/owner/id/timer, never the gang - that appears solely in the
 * server's "/ce info &lt;id&gt;" reply, on its "Lokation:" line. So this quietly
 * runs "/ce info" for scanned celler whose gang we don't know yet, throttled to
 * one every few seconds so it never looks like spam, and parses the reply:
 *
 *   ----={ B363 }=----      <- which celle this reply is about
 *   Lokation: Hvid cellegang <- the gang
 *   Tid: 13 dage, 9 timer... <- a fresh timer, even for a far-away celle
 *
 * Our own auto-queries are hidden from chat; a "/ce info" you run yourself is
 * left visible but still parsed (so it fills a gang in for free). Powers the
 * Gange screen (GuiGange).
 */
public class GangInfo {

    // The celle id inside a reply header, e.g. "----={ B363 }=----".
    private static final Pattern HEADER = Pattern.compile("\\{\\s*([A-Za-z]{1,2}[0-9]{2,5})\\s*\\}");
    // A number + Danish unit on the "Tid:" line: "13 dage, 9 timer, 20 minutter".
    private static final Pattern TID_TOKEN = Pattern.compile("(\\d+)\\s*(dage?|timer?|minutter?|sekunder?)", Pattern.CASE_INSENSITIVE);

    private static final long SWEEP_INTERVAL_MS = 7000L;  // gap between auto-queries
    private static final long INFO_WINDOW_MS = 3500L;     // how long a header's follow-up lines are accepted
    private static final long IDLE_RESCAN_MS = 20000L;    // when nothing needs a gang, recheck this often

    // Correlates the multi-line reply: a header line sets the id that the
    // Lokation:/Tid: lines right after it belong to.
    private static String pendingInfoId = null;
    private static long pendingInfoUntil = 0L;

    // While in the future, reply lines are our own silent query and get hidden.
    private static long silentUntil = 0L;

    private static long nextSweepAt = 0L;

    // Ids tried this session, so a celle whose /ce info yielded no gang isn't
    // re-queried on a loop. Cleared by the screen's "Opdater gange" button.
    private static final Set<String> attempted = new HashSet<String>();

    /** Clears the tried-list and schedules an immediate sweep (the GUI's refresh button). */
    public static void requestResweep() {
        attempted.clear();
        nextSweepAt = 0L;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CelleScannerMod.config.gangAutoQuery) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextSweepAt || now < pendingInfoUntil) {
            return; // waiting on the interval, or on a reply still inside its window
        }
        String id = nextIdToQuery();
        if (id == null) {
            nextSweepAt = now + IDLE_RESCAN_MS;
            return;
        }
        attempted.add(id.toLowerCase(Locale.ROOT));
        silentUntil = now + INFO_WINDOW_MS;
        pendingInfoUntil = now + INFO_WINDOW_MS;
        nextSweepAt = now + SWEEP_INTERVAL_MS;
        mc.thePlayer.sendChatMessage("/ce info " + id);
    }

    /** First remembered celle with no gang yet that we haven't already tried this session. */
    private static String nextIdToQuery() {
        Map<String, CellePositions.Entry> all = CellePositions.snapshot();
        for (Map.Entry<String, CellePositions.Entry> e : all.entrySet()) {
            CellePositions.Entry entry = e.getValue();
            if (entry.gang != null && !entry.gang.isEmpty()) {
                continue;
            }
            String id = entry.displayId != null && !entry.displayId.isEmpty() ? entry.displayId : e.getKey();
            if (id == null || id.isEmpty() || attempted.contains(id.toLowerCase(Locale.ROOT))) {
                continue;
            }
            return id;
        }
        return null;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.message == null) {
            return;
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (text == null || text.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();

        Matcher h = HEADER.matcher(text);
        if (h.find()) {
            pendingInfoId = h.group(1);
            pendingInfoUntil = now + INFO_WINDOW_MS;
        } else if (pendingInfoId != null && now <= pendingInfoUntil) {
            String trimmed = text.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("lokation:")) {
                String gang = trimmed.substring(trimmed.indexOf(':') + 1).trim();
                if (!gang.isEmpty()) {
                    CellePositions.recordInfo(pendingInfoId, gang, -1L);
                }
            } else if (lower.startsWith("tid:")) {
                long secs = parseTid(trimmed.substring(trimmed.indexOf(':') + 1));
                if (secs >= 0) {
                    CellePositions.recordInfo(pendingInfoId, null, secs);
                }
            }
        }

        // Hide the reply from chat only when it was our own silent query.
        if (now <= silentUntil && looksLikeInfoLine(text)) {
            event.setCanceled(true);
        }
    }

    private static boolean looksLikeInfoLine(String text) {
        if (HEADER.matcher(text).find()) {
            return true;
        }
        String lower = text.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("lokation:") || lower.startsWith("ejer:")
                || lower.startsWith("blok:") || lower.startsWith("tid:")
                || lower.startsWith("medlemmer:");
    }

    private static long parseTid(String value) {
        Matcher m = TID_TOKEN.matcher(value);
        long secs = 0L;
        boolean any = false;
        while (m.find()) {
            any = true;
            long v = Long.parseLong(m.group(1));
            String u = m.group(2).toLowerCase(Locale.ROOT);
            if (u.startsWith("dag")) {
                secs += v * 86400L;
            } else if (u.startsWith("tim")) {
                secs += v * 3600L;
            } else if (u.startsWith("min")) {
                secs += v * 60L;
            } else if (u.startsWith("sek")) {
                secs += v;
            }
        }
        return any ? secs : -1L;
    }
}
