package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps a celle to its gang from its id, using number ranges within a level
 * letter - e.g. B286..B494 is "Hvid cellegang". This is how the Gange screen
 * resolves a gang WITHOUT any server lookup: the id already carries everything
 * (the letter is the level, the number places it in a gang's range).
 *
 * Ranges live in an editable file (config/massiveo_gang_ranges.json) so they can
 * be corrected/extended without a rebuild; any ranges baked in as defaults that
 * aren't already in the file are merged in on load, so a mod update can add new
 * ones while keeping the player's own edits.
 *
 * A gang learned exactly from a right-clicked sign (CellePositions.gang) still
 * takes priority over this - see GuiGange - since that came straight from the
 * server for that specific celle.
 */
public final class GangRanges {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // Letter prefix (level) + number, e.g. "B363" or "b1723"; leading zeros ok.
    private static final Pattern ID = Pattern.compile("([A-Za-z]{1,2})([0-9]{1,7})");

    private static File file;
    private static List<Range> ranges = new ArrayList<Range>();

    /** One gang's id span on one level: [from, to] inclusive, for ids starting with prefix. */
    public static final class Range {
        public String prefix; // level letter, e.g. "B"
        public long from;     // inclusive
        public long to;       // inclusive
        public String gang;   // gang name, e.g. "Hvid cellegang"
    }

    private GangRanges() {
    }

    public static void init(File configDir) {
        file = new File(configDir, "massiveo_gang_ranges.json");
        load();
    }

    public static void load() {
        List<Range> loaded = new ArrayList<Range>();
        if (file != null && file.exists()) {
            FileReader reader = null;
            try {
                reader = new FileReader(file);
                Type type = new TypeToken<List<Range>>() {
                }.getType();
                List<Range> parsed = GSON.fromJson(reader, type);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                // Broad catch: a hand-mangled file throws RuntimeExceptions from
                // gson. Fall back to defaults rather than crashing preInit.
                System.err.println("[CelleScanner] Kunne ikke læse gang-ranges: " + e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        // Merge in any baked default not already present, so a mod update can
        // ship new ranges without wiping the player's edited file.
        boolean changed = loaded.isEmpty() && (file == null || !file.exists());
        for (Range def : defaults()) {
            if (!containsSame(loaded, def)) {
                loaded.add(def);
                changed = true;
            }
        }
        ranges = loaded;
        if (changed) {
            save();
        }
    }

    private static boolean containsSame(List<Range> list, Range r) {
        for (Range e : list) {
            if (e != null && e.prefix != null && e.prefix.equalsIgnoreCase(r.prefix)
                    && e.from == r.from && e.to == r.to
                    && e.gang != null && e.gang.equals(r.gang)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The gang ranges that ship with the mod. Add every confirmed range here so
     * new installs (and, via the merge in load(), existing ones) resolve gange
     * with no server lookup at all.
     */
    private static List<Range> defaults() {
        List<Range> d = new ArrayList<Range>();
        d.add(range("B", 286, 494, "Hvid cellegang"));
        return d;
    }

    private static Range range(String prefix, long from, long to, String gang) {
        Range r = new Range();
        r.prefix = prefix;
        r.from = from;
        r.to = to;
        r.gang = gang;
        return r;
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
                GSON.toJson(ranges, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int count() {
        return ranges.size();
    }

    /** Gang for a celle id from the configured ranges, or null if none matches. */
    public static String gangFor(String celleId) {
        if (celleId == null) {
            return null;
        }
        Matcher m = ID.matcher(celleId.trim());
        if (!m.matches()) {
            return null;
        }
        String prefix = m.group(1);
        long num;
        try {
            num = Long.parseLong(m.group(2));
        } catch (NumberFormatException e) {
            return null;
        }
        for (Range r : ranges) {
            if (r == null || r.prefix == null || r.gang == null) {
                continue;
            }
            if (r.prefix.equalsIgnoreCase(prefix) && num >= r.from && num <= r.to) {
                return r.gang;
            }
        }
        return null;
    }
}
