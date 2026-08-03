package com.otto.cellescanner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Passive measurement of how a celle sign actually behaves. Records only, never
 * acts.
 *
 * This exists to answer two questions that cannot be guessed, and that any
 * automated buying would depend on:
 *
 *  1. How often does the countdown really change, and by how much? The sign only
 *     shows whole minutes, and the code has long assumed a roughly 20 minute
 *     server refresh. If it actually ticks every minute, a prediction can be far
 *     sharper than that assumption allows.
 *
 *  2. When the countdown is extrapolated to zero, how far off is that from the
 *     moment the celle really becomes free? Reading "5m" tells you the remainder
 *     is somewhere in 300 to 359 seconds, depending on whether the server floors
 *     or ceils, so there is a constant offset hiding in every prediction. It has
 *     to be measured.
 *
 * Written as JSON lines to cellescanner_timing.jsonl next to the config, one
 * event per line, so it can be appended to cheaply and read back later.
 */
public final class CelleTimingLog {

    private static final String FILE_NAME = "cellescanner_timing.jsonl";
    private static File file;

    /** Last confirmed anchor per celle, used to predict when it should hit zero. */
    private static final Map<String, long[]> ANCHORS = new HashMap<String, long[]>();

    private CelleTimingLog() {
    }

    public static void init(File configDir) {
        if (configDir == null) {
            return;
        }
        file = new File(configDir, FILE_NAME);
    }

    private static boolean enabled() {
        return file != null && MassiveOsFreakyAddons.config != null
                && MassiveOsFreakyAddons.config.timingLogEnabled;
    }

    /**
     * The sign's countdown changed. Records how long it had been showing the old
     * value and how far it jumped, which is what reveals the real cadence.
     *
     * @param previousRemaining what the sign said before, in seconds
     * @param newRemaining      what it says now, in seconds
     * @param previousChangeAt  when we last saw it change, 0 if unknown
     * @param confirmed         whether this client witnessed the change itself
     */
    public static void tick(String celleId, long previousRemaining, long newRemaining,
                            long previousChangeAt, boolean confirmed) {
        long now = System.currentTimeMillis();
        if (confirmed) {
            ANCHORS.put(celleId, new long[]{now, newRemaining});
        }
        if (!enabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        field(sb, "event", "tick");
        field(sb, "celle", celleId);
        num(sb, "t", now);
        num(sb, "prevRemaining", previousRemaining);
        num(sb, "newRemaining", newRemaining);
        // How much the displayed number dropped. If this is consistently 60 the
        // sign ticks per minute; if it is ~1200 the 20 minute assumption holds.
        num(sb, "droppedSeconds", previousRemaining - newRemaining);
        // Wall time since the previous observed change, which is the real cadence.
        num(sb, "sincePrevChangeMs", previousChangeAt > 0 ? now - previousChangeAt : -1);
        bool(sb, "confirmed", confirmed);
        sb.setLength(sb.length() - 1);
        sb.append('}');
        append(sb.toString());
    }

    /**
     * The celle just became buyable. This is the measurement that matters: the
     * gap between where the anchor said zero would be and where it actually was.
     */
    public static void becameFree(String celleId) {
        long now = System.currentTimeMillis();
        long[] anchor = ANCHORS.get(celleId);
        if (!enabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder(200);
        sb.append('{');
        field(sb, "event", "free");
        field(sb, "celle", celleId);
        num(sb, "t", now);
        if (anchor != null) {
            long predicted = anchor[0] + anchor[1] * 1000L;
            num(sb, "anchorAt", anchor[0]);
            num(sb, "anchorRemaining", anchor[1]);
            num(sb, "predictedFreeAt", predicted);
            // Positive means it became free later than predicted.
            num(sb, "offsetMs", now - predicted);
            num(sb, "anchorAgeMs", now - anchor[0]);
        } else {
            bool(sb, "noAnchor", true);
        }
        sb.setLength(sb.length() - 1);
        sb.append('}');
        append(sb.toString());
    }

    /** Someone bought it. Shows how long a free celle survives. */
    public static void wasBought(String celleId, String owner, long freeSinceMs) {
        if (!enabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(160);
        sb.append('{');
        field(sb, "event", "bought");
        field(sb, "celle", celleId);
        num(sb, "t", now);
        if (owner != null) {
            field(sb, "owner", owner);
        }
        num(sb, "freeForMs", freeSinceMs > 0 ? now - freeSinceMs : -1);
        sb.setLength(sb.length() - 1);
        sb.append('}');
        append(sb.toString());
    }

    /**
     * Reads the log back and reports what it has learned so far. Kept here rather
     * than analysed by hand, because the whole point is a number you can act on.
     */
    public static List<String> summary() {
        List<String> out = new ArrayList<String>();
        if (file == null || !file.exists()) {
            out.add("Ingen timing-log endnu.");
            return out;
        }

        List<Long> offsets = new ArrayList<Long>();
        List<Long> drops = new ArrayList<Long>();
        List<Long> cadences = new ArrayList<Long>();
        int ticks = 0, frees = 0, boughts = 0, noAnchor = 0;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf("\"tick\"") >= 0) {
                    ticks++;
                    long d = readNum(line, "droppedSeconds");
                    if (d > 0L && d != Long.MIN_VALUE) drops.add(Long.valueOf(d));
                    long c = readNum(line, "sincePrevChangeMs");
                    if (c > 0L && c != Long.MIN_VALUE) cadences.add(Long.valueOf(c));
                } else if (line.indexOf("\"free\"") >= 0) {
                    frees++;
                    if (line.indexOf("noAnchor") >= 0) {
                        noAnchor++;
                    } else {
                        long off = readNum(line, "offsetMs");
                        if (off != Long.MIN_VALUE) {
                            offsets.add(Long.valueOf(off));
                        }
                    }
                } else if (line.indexOf("\"bought\"") >= 0) {
                    boughts++;
                }
            }
        } catch (IOException e) {
            out.add("Kunne ikke laese timing-loggen: " + e.getMessage());
            return out;
        } finally {
            close(reader);
        }

        out.add("Timing-log: " + ticks + " tick, " + frees + " frigivet, " + boughts + " koebt");

        if (!drops.isEmpty()) {
            out.add("Skiltet falder typisk " + median(drops) + "s pr. opdatering"
                    + " (min " + Collections.min(drops) + "s, max " + Collections.max(drops) + "s)");
        }
        if (!cadences.isEmpty()) {
            out.add("Opdaterer ca. hvert " + (median(cadences) / 1000L) + "s"
                    + " (min " + (Collections.min(cadences) / 1000L) + "s, max "
                    + (Collections.max(cadences) / 1000L) + "s)");
        }

        if (offsets.isEmpty()) {
            out.add("Endnu ingen maalt offset. Der skal ses en celle blive fri med et bekraeftet anker.");
            if (noAnchor > 0) {
                out.add(noAnchor + " blev fri uden anker og kunne ikke bruges.");
            }
        } else {
            long med = median(offsets);
            out.add("OFFSET: median " + med + " ms over " + offsets.size() + " maalinger"
                    + " (min " + Collections.min(offsets) + ", max " + Collections.max(offsets) + ")");
            long spread = Collections.max(offsets) - Collections.min(offsets);
            if (offsets.size() < 5) {
                out.add("For faa maalinger til at stole paa endnu. Sigt efter mindst 5.");
            } else if (spread > 5000L) {
                out.add("Spredningen er " + (spread / 1000L) + "s, saa skiltet skifter ikke praecist."
                        + " Et koeb kan ikke times paa det her alene.");
            } else {
                out.add("Spredningen er " + spread + " ms, saa forudsigelsen er brugbar.");
            }
        }
        return out;
    }

    // --- tiny JSON writing, no dependency needed for one line at a time ---

    private static void field(StringBuilder sb, String k, String v) {
        sb.append('"').append(k).append("\":\"").append(escape(v)).append("\",");
    }

    private static void num(StringBuilder sb, String k, long v) {
        sb.append('"').append(k).append("\":").append(v).append(',');
    }

    private static void bool(StringBuilder sb, String k, boolean v) {
        sb.append('"').append(k).append("\":").append(v).append(',');
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static long readNum(String line, String key) {
        int i = line.indexOf('"' + key + "\":");
        if (i < 0) {
            return Long.MIN_VALUE;
        }
        int start = i + key.length() + 3;
        // Tolerate whitespace after the colon so a log written by anything other
        // than this class still reads back.
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        int end = start;
        if (end < line.length() && line.charAt(end) == '-') {
            end++;
        }
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        if (end == start) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(line.substring(start, end));
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static long median(List<Long> values) {
        List<Long> copy = new ArrayList<Long>(values);
        Collections.sort(copy);
        return copy.get(copy.size() / 2).longValue();
    }

    private static void append(String line) {
        PrintWriter writer = null;
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            writer = new PrintWriter(new FileWriter(file, true));
            writer.println(line);
        } catch (IOException e) {
            System.err.println("[CelleScanner] Could not write timing log: " + e);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static void close(BufferedReader r) {
        if (r != null) {
            try {
                r.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Where the log lives, for the chat message. */
    public static String path() {
        return file == null ? "(ikke sat op)" : file.getAbsolutePath();
    }

    @SuppressWarnings("unused")
    private static String stamp(long t) {
        return new SimpleDateFormat("HH:mm:ss").format(new Date(t));
    }
}
