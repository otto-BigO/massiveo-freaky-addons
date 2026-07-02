package com.otto.cellescanner;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Learns which "gang" (corridor) each remembered celle sits on, passively, by
 * reading the celle info block the server prints when you right-click a celle
 * sign (the same block "/ce info" produces):
 *
 *   ----={ B359 }=----        <- which celle this block is about
 *   Lokation: Hvid cellegang  <- the gang
 *   Ejer: Ugua
 *   Blok: B
 *   Tid: 14 dage, 3 timer...  <- a fresh timer, even for a far-away celle
 *   Medlemmer: Xutoo
 *
 * No commands are ever sent - it just watches chat - so there's no /ce cooldown
 * or delay. Right-click celle signs as you walk around and the Gange screen
 * (GuiGange) fills itself in. Gated by the config.gangAutoQuery toggle so it can
 * be turned off entirely from the Gange screen.
 */
public class GangInfo {

    // The celle id inside an info-block header, e.g. "----={ B359 }=----".
    private static final Pattern HEADER = Pattern.compile("\\{\\s*([A-Za-z]{1,2}[0-9]{2,5})\\s*\\}");
    // A number + Danish unit on the "Tid:" line: "14 dage, 3 timer, 0 minutter".
    private static final Pattern TID_TOKEN = Pattern.compile("(\\d+)\\s*(dage?|timer?|minutter?|sekunder?)", Pattern.CASE_INSENSITIVE);

    // How long after a header line its follow-up Lokation:/Tid: lines are accepted.
    private static final long INFO_WINDOW_MS = 3500L;

    // Correlates the multi-line block: a header line sets the id that the
    // Lokation:/Tid: lines right after it belong to.
    private static String pendingInfoId = null;
    private static long pendingInfoUntil = 0L;

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!CelleScannerMod.config.gangAutoQuery || event.message == null) {
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
            return;
        }
        if (pendingInfoId == null || now > pendingInfoUntil) {
            return;
        }

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
