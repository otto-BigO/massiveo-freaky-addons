package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers where every celle sign this client has ever scanned actually
 * is, AND its last known timer state, keyed by celle id, persisted to disk
 * so both survive relogging, restarting, and simply walking out of render
 * distance - unlike CelleScanner's live cache, which drops a celle
 * entirely the instant its chunk unloads (see the "drop anything we did
 * not see this pass" cleanup in CelleScanner.scan()).
 *
 * Two things depend on this surviving longer than the live cache does:
 *
 * 1. "Celle Finder" (GuiCelleFinder) - after a long session of scanning
 *    celler while walking around, you're left with a pile of ids and no
 *    idea where any of them physically are the moment you walk away.
 * 2. The countdown NOT appearing to reset when you leave and come back.
 *    Without remembering remainingSeconds/valueUpdatedAt/timerConfirmed
 *    here, re-entering a celle's render distance before its sign has done
 *    its own ~20-minute refresh would treat the same stale number still
 *    displayed as a brand new "just seen right now" reading - making the
 *    live countdown jump back up until the sign's real next refresh
 *    corrects it. CelleScanner.scan() rehydrates from here instead.
 *
 * Deliberately client-local and never reported to the bot or shared with
 * other players - a BlockPos only makes sense inside one specific
 * world/server, and a stale position on a mismatched server would be
 * actively misleading rather than merely unhelpful.
 */
public final class CellePositions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Hard cap on remembered celler so the map can't grow without bound over a
    // very long session. Far more than the Finder's quick-pick ever shows; when
    // exceeded, the least-recently-seen entries are dropped first.
    private static final int MAX_ENTRIES = 1000;

    private static File file;
    private static Map<String, Entry> known = new HashMap<String, Entry>();

    public static final class Entry {
        public int x;
        public int y;
        public int z;
        public int dimension;
        public long lastSeen;

        // The id exactly as it was read off the sign (original casing). The map
        // key is normalized (lowercased) for lookups, so this is kept separately
        // for display - e.g. the Finder's recent-celler picker. Null on entries
        // written by an older build; callers fall back to the key.
        public String displayId;

        // Last known REAL (raw, not live-extrapolated) timer state - see the
        // class doc above for why this needs to outlive the live cache.
        public long remainingSeconds;
        public long valueUpdatedAt;
        public boolean timerConfirmed;

        // Which "gang" (corridor) this celle sits on, e.g. "Hvid cellegang".
        // The sign doesn't show it; it's learned from the server's "/ce info"
        // reply (see GangInfo) and powers the Gange screen. Null until looked up.
        public String gang;

        /** remainingSeconds, extrapolated down to "now". Never negative. */
        public long liveRemainingSeconds() {
            long elapsed = (System.currentTimeMillis() - valueUpdatedAt) / 1000L;
            return Math.max(0L, remainingSeconds - elapsed);
        }
    }

    private CellePositions() {
    }

    public static void init(File configDir) {
        file = new File(configDir, "cellescanner_positions.json");
        load();
    }

    private static void load() {
        if (file == null || !file.exists()) {
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            Type type = new TypeToken<HashMap<String, Entry>>() {
            }.getType();
            Map<String, Entry> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                // Re-key everything through normalizeKey so a file written by
                // an older build (which keyed by the raw trimmed id) still
                // looks up correctly, and so any accidental case-duplicates
                // collapse into one entry.
                Map<String, Entry> normalized = new HashMap<String, Entry>();
                for (Map.Entry<String, Entry> e : loaded.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        normalized.put(normalizeKey(e.getKey()), e.getValue());
                    }
                }
                known = normalized;
            }
        } catch (Exception e) {
            // Broad catch: gson throws RuntimeExceptions on a truncated/mangled
            // file. Keep an empty map rather than crashing preInit.
            System.err.println("[CelleScanner] Kunne ikke læse positions-fil: " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Records/updates where a celle id currently is and its latest known
     * real timer state. Cheap to call every scan tick for every
     * currently-loaded celle - only actually writes to disk when something
     * genuinely changed (a brand new id, a moved sign, or a real timer
     * update), not on every single repeated sighting of the same values.
     */
    public static void record(String celleId, BlockPos pos, int dimension, long remainingSeconds, long valueUpdatedAt, boolean timerConfirmed) {
        if (celleId == null || celleId.isEmpty() || pos == null) {
            return;
        }
        String key = normalizeKey(celleId);
        String displayId = celleId.trim();
        Entry existing = known.get(key);

        boolean changed = existing == null
                || existing.x != pos.getX() || existing.y != pos.getY() || existing.z != pos.getZ() || existing.dimension != dimension
                || existing.remainingSeconds != remainingSeconds || existing.valueUpdatedAt != valueUpdatedAt || existing.timerConfirmed != timerConfirmed
                || !displayId.equals(existing == null ? null : existing.displayId);

        boolean isNew = (existing == null);
        if (existing == null) {
            existing = new Entry();
            known.put(key, existing);
        }
        existing.displayId = displayId;
        existing.x = pos.getX();
        existing.y = pos.getY();
        existing.z = pos.getZ();
        existing.dimension = dimension;
        existing.remainingSeconds = remainingSeconds;
        existing.valueUpdatedAt = valueUpdatedAt;
        existing.timerConfirmed = timerConfirmed;
        existing.lastSeen = System.currentTimeMillis();

        if (known.size() > MAX_ENTRIES) {
            prune();
        }

        if (changed) {
            save();
            if (isNew) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            net.minecraft.util.EnumChatFormatting.GREEN + "[Celle Scanner] Ny celle kortlagt: " 
                            + displayId + " (" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")"
                    ));
                }
            }
        }
    }

    /**
     * Drops the least-recently-seen entries until the map is back at
     * MAX_ENTRIES. Only runs on the rare tick that pushes it over the cap, so
     * the sort cost is paid almost never.
     */
    private static void prune() {
        java.util.List<Map.Entry<String, Entry>> entries =
                new java.util.ArrayList<Map.Entry<String, Entry>>(known.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<String, Entry>>() {
            @Override
            public int compare(Map.Entry<String, Entry> a, Map.Entry<String, Entry> b) {
                return Long.compare(b.getValue().lastSeen, a.getValue().lastSeen);
            }
        });
        for (int i = MAX_ENTRIES; i < entries.size(); i++) {
            known.remove(entries.get(i).getKey());
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(known, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Merges what a "/ce info" reply told us about a celle into its remembered
     * entry: its gang (when non-null), and optionally a fresh timer reading from
     * the reply's "Tid:" line (pass remainingSeconds &lt; 0 to leave the timer
     * alone). No-op if we've never scanned this celle's sign, since there's no
     * entry (and no position) to attach the gang to.
     */
    public static void recordInfo(String celleId, String gang, long remainingSeconds) {
        if (file == null || celleId == null) {
            return;
        }
        String key = normalizeKey(celleId);
        Entry e = known.get(key);
        boolean isNew = (e == null);
        if (e == null) {
            e = new Entry();
            e.displayId = celleId.trim();
            known.put(key, e);
        }
        boolean changed = isNew;
        if (gang != null && !gang.isEmpty() && !gang.equals(e.gang)) {
            e.gang = gang;
            changed = true;
        }
        if (remainingSeconds >= 0) {
            long now = System.currentTimeMillis();
            e.remainingSeconds = remainingSeconds;
            e.valueUpdatedAt = now;
            e.timerConfirmed = true;
            e.lastSeen = now;
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    /** A shallow copy of every remembered celle, for the grouping screens. */
    public static Map<String, Entry> snapshot() {
        return new HashMap<String, Entry>(known);
    }

    /** Case-insensitive lookup - null if this client has never scanned that id. */
    public static Entry get(String celleId) {
        if (celleId == null) {
            return null;
        }
        return known.get(normalizeKey(celleId));
    }

    /**
     * The canonical map key for a celle id: trimmed and lowercased, so record()
     * and get() always agree and ids that differ only in case can't create two
     * separate entries. Uses ROOT locale so behaviour never depends on the
     * client's system locale (e.g. the Turkish-i pitfall).
     */
    private static String normalizeKey(String celleId) {
        return celleId.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Up to {@code limit} celle ids this client has scanned, most recently seen
     * first - for the Finder's quick-pick list, so the player can tap a celle
     * instead of remembering and typing its id. Ids are returned in their
     * original casing (falling back to the normalized key for entries written
     * by an older build that didn't store it).
     */
    public static java.util.List<String> recentIds(int limit) {
        java.util.List<Map.Entry<String, Entry>> entries =
                new java.util.ArrayList<Map.Entry<String, Entry>>(known.entrySet());
        java.util.Collections.sort(entries, new java.util.Comparator<Map.Entry<String, Entry>>() {
            @Override
            public int compare(Map.Entry<String, Entry> a, Map.Entry<String, Entry> b) {
                return Long.compare(b.getValue().lastSeen, a.getValue().lastSeen);
            }
        });
        java.util.List<String> result = new java.util.ArrayList<String>();
        for (Map.Entry<String, Entry> e : entries) {
            if (limit > 0 && result.size() >= limit) {
                break;
            }
            String display = e.getValue().displayId;
            result.add(display != null && !display.isEmpty() ? display : e.getKey());
        }
        return result;
    }
}
