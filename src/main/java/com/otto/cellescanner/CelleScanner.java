package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans every loaded TileEntitySign once a second and keeps a cache of
 * Celle objects built purely from sign text already loaded on the client.
 */
public class CelleScanner {

    private static final String LINE_SOLGT = "SOLGT!";
    private static final String LINE_TIL_SALG = "TIL SALG!";
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final long REPORT_COOLDOWN_MS = 15000L;
    // Same value/purpose as CelleScannerBot's state.js RENEWAL_SLACK_SECONDS -
    // ignore a tiny "increase" as reporting jitter rather than a genuine renewal.
    private static final long RENEWAL_SLACK_SECONDS = 30L;

    private final Map<BlockPos, Celle> cache = new HashMap<BlockPos, Celle>();
    private int tickCounter = 0;

    // Recomputed once per scan (every ~1s), not per render frame. HUD and
    // ESP both read this directly instead of re-filtering/re-sorting the
    // whole cache 60+ times a second.
    private List<Celle> upcomingCache = Collections.emptyList();

    private String lastReportSignature = "";
    private long lastReportPushMillis = 0L;

    private static final Comparator<Celle> UPCOMING_COMPARATOR = new Comparator<Celle>() {
        @Override
        public int compare(Celle a, Celle b) {
            return Long.compare(a.liveRemainingSeconds(), b.liveRemainingSeconds());
        }
    };

    private static final Comparator<Celle> REPORT_COMPARATOR = new Comparator<Celle>() {
        @Override
        public int compare(Celle a, Celle b) {
            if (a.celleId == null) {
                return b.celleId == null ? 0 : -1;
            }
            if (b.celleId == null) {
                return 1;
            }
            return a.celleId.compareTo(b.celleId);
        }
    };

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!CelleScannerMod.config.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        scan(mc.theWorld);
    }

    private void scan(World world) {
        Set<BlockPos> seen = new HashSet<BlockPos>();

        // Constant for the whole scan - no need to re-query it per sign.
        int dimensionId = world.provider.getDimensionId();

        for (Object obj : world.loadedTileEntityList) {
            if (!(obj instanceof TileEntitySign)) {
                continue;
            }

            TileEntitySign sign = (TileEntitySign) obj;
            if (sign.signText == null || sign.signText.length < 4) {
                continue;
            }

            String line1 = clean(sign.signText[0].getUnformattedText());
            String line2 = clean(sign.signText[1].getUnformattedText());
            String line3 = clean(sign.signText[2].getUnformattedText());
            String line4 = clean(sign.signText[3].getUnformattedText());

            CelleStatus status;
            String owner = null;
            String celleId;
            long remaining;

            if (LINE_SOLGT.equalsIgnoreCase(line1)) {
                status = CelleStatus.SOLGT;
                owner = line2;
                celleId = line3;
                remaining = TimerParser.parseSeconds(line4);
                if (remaining < 0) {
                    continue;
                }
            } else if (LINE_TIL_SALG.equalsIgnoreCase(line1)) {
                status = CelleStatus.TIL_SALG;
                celleId = line2;
                remaining = 0;
            } else {
                continue;
            }

            if (celleId == null || celleId.isEmpty()) {
                continue;
            }

            BlockPos pos = sign.getPos();
            seen.add(pos);

            Celle celle = cache.get(pos);
            boolean isNew = celle == null;
            if (isNew) {
                celle = new Celle(pos);
                cache.put(pos, celle);
            }

            // A celle that just came back into range after being unloaded
            // (isNew) has lost all its in-memory timer state - the previous
            // Celle object (and its valueUpdatedAt anchor) was discarded the
            // moment the chunk unloaded, by the cleanup below. Without
            // rehydrating from CellePositions first, re-entering render
            // distance before the sign's own ~20-minute refresh cycle would
            // wrongly treat whatever stale value is STILL displayed as a
            // brand new "just seen right now" reading - making the live
            // countdown appear to jump back up (reset) until the sign's
            // real next refresh corrects it. This is the fix for that.
            CellePositions.Entry remembered = isNew ? CellePositions.get(celleId) : null;
            boolean sameDimension = remembered != null && remembered.dimension == dimensionId;

            boolean statusChanged = celle.status != status;
            boolean valueChanged;
            boolean renewed;

            if (isNew && sameDimension) {
                if (remaining == remembered.remainingSeconds) {
                    // Sign hasn't actually refreshed since we last saw it -
                    // a stale read, not new information. Rehydrate the old
                    // anchor instead of restarting it at "now".
                    valueChanged = false;
                    celle.valueUpdatedAt = remembered.valueUpdatedAt;
                    celle.timerConfirmed = remembered.timerConfirmed;
                    renewed = false;
                } else {
                    // The sign genuinely shows something different than the
                    // last time we saw it - either it ticked down for real
                    // while we were away, or the owner bought more time.
                    // Either way this is fresh, confirmed information.
                    valueChanged = true;
                    long elapsedSeconds = (System.currentTimeMillis() - remembered.valueUpdatedAt) / 1000L;
                    long expected = Math.max(0L, remembered.remainingSeconds - elapsedSeconds);
                    renewed = remaining > expected + RENEWAL_SLACK_SECONDS;
                }
            } else {
                // The sign's own text only changes roughly every ~20 minutes;
                // only reset the countdown's "live" anchor point when the
                // value we read actually moved, not on every 1s scan.
                valueChanged = isNew || celle.remainingSeconds != remaining;
                // A countdown only ever decreases on its own - any time it
                // goes UP, the owner must have just bought more time. If
                // we'd already notified about this celle being close, that
                // notification (and any Discord ping sent for it) is now
                // stale and wrong.
                renewed = !isNew && remaining > celle.remainingSeconds;
            }

            celle.status = status;
            celle.celleId = celleId;
            celle.owner = owner;
            celle.remainingSeconds = remaining;
            celle.lastSeen = System.currentTimeMillis();

            if (valueChanged) {
                celle.valueUpdatedAt = celle.lastSeen;
                if (!isNew || sameDimension) {
                    // Either we were already tracking this celle and just
                    // caught its value actually move, or we just rehydrated
                    // and reconfirmed it against remembered state above -
                    // either way the anchor is now exact, not a guess.
                    celle.timerConfirmed = true;
                }
            }

            if (statusChanged) {
                // re-arm the in-game chat notification when the sign flips state
                celle.notified = false;
            }

            if (renewed) {
                // Re-arm so we'll notify again if/when it genuinely re-enters
                // the window later. (The equivalent "retract a stale Discord
                // alert" step now happens bot-side, since the bot - not this
                // client - decides when a special-celle alert fires; see
                // CelleScannerBot's state.js for the same renewal check.)
                celle.notified = false;
            }

            // Remember this celle's position AND timer state permanently (on
            // disk), independent of the live cache above - which drops both
            // entirely the moment this chunk unloads (see the cleanup
            // below). This is what lets Celle Finder point back to a celle
            // you scanned earlier, and what the rehydration above reads
            // from to avoid the countdown appearing to reset.
            CellePositions.record(celleId, pos, dimensionId, celle.remainingSeconds, celle.valueUpdatedAt, celle.timerConfirmed);

            checkNotify(celle);
        }

        // drop anything we did not see this pass - sign unloaded or removed
        Iterator<Map.Entry<BlockPos, Celle>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Celle> entry = it.next();
            if (!seen.contains(entry.getKey())) {
                it.remove();
            }
        }

        recomputeUpcoming();
        maybeReportToBot();
    }

    private void recomputeUpcoming() {
        List<Celle> result = new ArrayList<Celle>();
        for (Celle c : cache.values()) {
            if (c.status == CelleStatus.TIL_SALG) {
                // currently for sale - always shown, regardless of timer
                result.add(c);
                continue;
            }
            // SOLGT - only shown once it's about to become available
            double hours = c.liveRemainingHours();
            if (hours < CelleScannerMod.config.minHours || hours > CelleScannerMod.config.maxHours) {
                continue;
            }
            result.add(c);
        }

        Collections.sort(result, UPCOMING_COMPARATOR);

        upcomingCache = result;
    }

    /**
     * Display list for the HUD/ESP: every TIL_SALG celle (currently for
     * sale, shown unconditionally) plus every SOLGT celle whose remaining
     * time falls inside [minHours, maxHours] (about to become available),
     * soonest first. Cheap to call from a render loop - it just returns
     * the list built during the last scan.
     */
    public List<Celle> getUpcoming() {
        return upcomingCache;
    }

    private void checkNotify(Celle celle) {
        // Deliberately NOT filtered to TIL_SALG only: a SOLGT celle whose
        // timer is running low is exactly the "will become available soon"
        // case from the spec, and is the main real-world use case (most
        // celler are sold most of the time).
        if (!CelleScannerMod.config.notify) {
            return;
        }
        if (celle.notified) {
            return;
        }

        double hours = celle.liveRemainingHours();
        if (hours >= CelleScannerMod.config.minHours && hours <= CelleScannerMod.config.maxHours) {
            celle.notified = true;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                String verb = celle.status == CelleStatus.TIL_SALG ? "er til salg og" : "bliver snart ledig og";
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        EnumChatFormatting.GOLD + "Celle " + celle.celleId + " " + verb + " er nu indenfor "
                                + CelleScannerMod.config.maxHours + " timer."));
            }
        }
    }

    /**
     * Reports this client's ENTIRE local cache (not just the time-windowed
     * "upcoming" subset) to the companion bot, so the shared dashboard can
     * be a genuinely complete list of every celle any connected client has
     * scanned - not limited to whatever's currently loaded/rendered on one
     * particular player's screen. Only actually POSTs when something in the
     * cache changed, and never more often than REPORT_COOLDOWN_MS, so the
     * once-a-second scan loop doesn't hammer the bot.
     */
    private void maybeReportToBot() {
        if (!CelleScannerMod.config.botReportEnabled) {
            return;
        }
        if (System.currentTimeMillis() - lastReportPushMillis < REPORT_COOLDOWN_MS) {
            return;
        }

        String signature = buildReportSignature(cache.values());
        if (signature.equals(lastReportSignature)) {
            return;
        }

        lastReportSignature = signature;
        lastReportPushMillis = System.currentTimeMillis();
        // Both lists are copied here on the client thread before the report
        // runs on its background thread: the special-id list can be mutated
        // live from the GUI (add/remove/clear) and iterating the shared
        // instance off-thread would throw ConcurrentModificationException.
        ReportWebhookClient.report(new ArrayList<Celle>(cache.values()),
                new ArrayList<String>(CelleScannerMod.config.specialCelleIds));
    }

    /**
     * Minute-level granularity on purpose - second-level would change every
     * scan and never satisfy the cooldown. Includes timerConfirmed so a
     * confirmation-status flip (e.g. right after CellePositions rehydration
     * above resolves a previously-unconfirmed reading) pushes a fresh report
     * even in the rare case its remainingSeconds happens to land in the same
     * minute bucket as what was last sent.
     */
    private static String buildReportSignature(Collection<Celle> all) {
        List<Celle> sorted = new ArrayList<Celle>(all);
        Collections.sort(sorted, REPORT_COMPARATOR);
        StringBuilder sb = new StringBuilder();
        for (Celle c : sorted) {
            sb.append(c.celleId).append(':').append(c.status).append(':')
                    .append(c.liveRemainingSeconds() / 60).append(':')
                    .append(c.timerConfirmed).append(',');
        }
        return sb.toString();
    }

    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(text).trim();
    }

    public Map<BlockPos, Celle> getCache() {
        return cache;
    }

    public void clear() {
        cache.clear();
    }
}
