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

    // Session cache: every celle we've seen this run, keyed by normalized id,
    // together with the dimension it was seen in. Unlike the live "cache"
    // above (which drops a celle the instant its chunk unloads), this is only
    // ever cleared when the game closes - so a celle you scanned stays on the
    // HUD/ESP through chunk unloads, relogs and deaths for the rest of the
    // session, its countdown still ticking down from the last real reading.
    // Static so it survives even if the scanner instance is ever recreated.
    private static final Map<String, Celle> SESSION_CACHE = new HashMap<String, Celle>();
    private static final Map<String, Integer> SESSION_DIM = new HashMap<String, Integer>();

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

        // Live expiry alerts checker
        if (CelleScannerMod.config.celleExpiryAlertsEnabled) {
            for (Celle c : cache.values()) {
                if (c.status != CelleStatus.SOLGT) {
                    continue;
                }
                // Only alert for followed/tracked celler
                if (!CelleScannerMod.config.myCelleIds.contains(c.celleId) && !CelleScannerMod.config.specialCelleIds.contains(c.celleId)) {
                    continue;
                }
                long seconds = c.liveRemainingSeconds();
                
                // Reset fired flags if the timer has been extended/refreshed
                if (seconds > 120) {
                    c.alert120Fired = false;
                }
                if (seconds > 60) {
                    c.alert60Fired = false;
                }
                if (seconds > 30) {
                    c.alert30Fired = false;
                }
                if (seconds > 0) {
                    c.alert0Fired = false;
                }

                checkExpiryAlerts(c, seconds);
                c.lastAlertSeconds = seconds;
            }
        }

        tickCounter++;
        if (tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        scan(mc.theWorld);
    }

    private void checkExpiryAlerts(Celle c, long seconds) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        // 1. One-off threshold alerts (lag-proof)
        if (seconds <= 120 && seconds > 60 && !c.alert120Fired) {
            c.alert120Fired = true;
            mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
            showTitle(mc, EnumChatFormatting.GOLD + c.celleId,
                    EnumChatFormatting.YELLOW + "Ledig om 2 minutter!", 10, 40, 10);
        } else if (seconds <= 60 && seconds > 30 && !c.alert60Fired) {
            c.alert60Fired = true;
            mc.thePlayer.playSound("note.pling", 1.0F, 1.2F);
            showTitle(mc, EnumChatFormatting.GOLD + c.celleId,
                    EnumChatFormatting.YELLOW + "Ledig om 1 minut!", 10, 40, 10);
        } else if (seconds <= 30 && seconds > 10 && !c.alert30Fired) {
            c.alert30Fired = true;
            mc.thePlayer.playSound("note.pling", 1.0F, 1.5F);
            showTitle(mc, EnumChatFormatting.GOLD + c.celleId,
                    EnumChatFormatting.RED + "Ledig om 30 sekunder!", 10, 40, 10);
        } else if (seconds == 0 && !c.alert0Fired) {
            c.alert0Fired = true;
            mc.thePlayer.playSound("random.levelup", 1.0F, 1.0F);
            showTitle(mc, EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD + c.celleId,
                    EnumChatFormatting.GREEN + "ER LEDIG NU!", 5, 60, 15);
        }

        // 2. Second-by-second countdown for the final 10 seconds
        if (seconds > 0 && seconds <= 10 && seconds != c.lastAlertSeconds) {
            mc.thePlayer.playSound("random.click", 0.5F, 1.0F);
            showTitle(mc, EnumChatFormatting.RED + c.celleId,
                    EnumChatFormatting.GOLD + "Ledig om " + seconds + "s...", 0, 20, 5);
        }
    }

    /**
     * Shows a title + subtitle overlay. Vanilla's displayTitle() applies only ONE
     * thing per call (the title branch ignores both the subtitle argument and the
     * fade times), so it has to be called three times, times first, exactly like
     * the server's title packets do. A single combined call would show the title
     * with no subtitle and whatever fade times a previous title left behind.
     */
    private static void showTitle(Minecraft mc, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        mc.ingameGUI.displayTitle(null, null, fadeIn, stay, fadeOut);
        mc.ingameGUI.displayTitle(null, subtitle, 0, 0, 0);
        mc.ingameGUI.displayTitle(title, null, 0, 0, 0);
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

            // Remember this celle for the rest of the session (see SESSION_CACHE
            // above). Points at the freshest live object each scan, so a
            // currently-loaded celle stays live and an unloaded one keeps the
            // last object (frozen timer anchor) to extrapolate from.
            String sessionKey = celleId.trim().toLowerCase(java.util.Locale.ROOT);
            SESSION_CACHE.put(sessionKey, celle);
            SESSION_DIM.put(sessionKey, dimensionId);

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

    /**
     * The HUD/ESP display list, built from the SESSION cache (not just the
     * currently-loaded live cache) so celler don't vanish when their chunk
     * unloads or after a relog/death. Same window rule as {@link
     * #recomputeUpcoming()}: every TIL_SALG celle plus every SOLGT celle whose
     * live timer sits inside [minHours, maxHours], soonest first, limited to
     * the dimension the player is currently in (a remembered position from
     * another dimension would be meaningless here). Cheap enough to call from a
     * render frame - it's a filter+sort over at most a few hundred entries.
     */
    public static List<Celle> upcomingForDimension(int dimension) {
        List<Celle> result = new ArrayList<Celle>();
        for (Map.Entry<String, Celle> e : SESSION_CACHE.entrySet()) {
            Integer dim = SESSION_DIM.get(e.getKey());
            if (dim == null || dim.intValue() != dimension) {
                continue;
            }
            Celle c = e.getValue();
            if (c.status == CelleStatus.TIL_SALG) {
                result.add(c);
                continue;
            }
            double hours = c.liveRemainingHours();
            if (hours < CelleScannerMod.config.minHours || hours > CelleScannerMod.config.maxHours) {
                continue;
            }
            result.add(c);
        }
        Collections.sort(result, UPCOMING_COMPARATOR);
        return result;
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
