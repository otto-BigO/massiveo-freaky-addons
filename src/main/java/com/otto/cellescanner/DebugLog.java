package com.otto.cellescanner;

import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Writes debug messages from the debug addons (FlipDebug, debugDump, dumpNearestSign, etc.)
 * to a rotating log file in the config directory. Only writes when debug mode is enabled.
 *
 * Thread-safe: synchronized on the class, so the background webhook thread
 * can call log() without racing the client tick that also calls it.
 *
 * The file is named "massiveo_debug.log" and is appended to across sessions,
 * with a clear session header written once when the first message arrives.
 * Call openSession() at mod init so the header goes in before any messages.
 */
public final class DebugLog {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static File logFile;
    private static boolean sessionOpened = false;

    private DebugLog() {
    }

    /**
     * Call once at preInit (after the config file is known) to set the
     * log file path. Does NOT open or write the file yet.
     */
    public static synchronized void init(File configDir) {
        logFile = new File(configDir, "massiveo_debug.log");
    }

    /**
     * Writes a session-start banner to the file. Call when the mod first
     * becomes active so later messages can be correlated to a launch.
     * No-op if called more than once per JVM run or if debug logging is off.
     */
    public static synchronized void openSession() {
        if (sessionOpened || logFile == null) {
            return;
        }
        if (CelleScannerMod.config == null || !isEnabled()) {
            return;
        }
        sessionOpened = true;
        writeRaw("=== Massiveo debug session opened at " + DATE_FMT.format(new Date())
                + " (v" + CelleScannerMod.VERSION + ") ===");
    }

    /**
     * Appends one timestamped line to the log file, stripping Minecraft
     * color/formatting codes first so the file is readable as plain text.
     * Silently no-ops if debug logging is disabled or the file can't be
     * written (we never want a log failure to crash the client).
     */
    public static synchronized void log(String source, String message) {
        if (logFile == null || !isEnabled()) {
            return;
        }
        if (!sessionOpened) {
            openSession();
        }
        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(message);
        writeRaw("[" + DATE_FMT.format(new Date()) + "] [" + source + "] " + clean);
    }

    /**
     * Same as log(source, message) but with an empty source field -
     * a shortcut for callers that already include context in the message.
     */
    public static synchronized void log(String message) {
        log("debug", message);
    }

    /** Returns the absolute path to the log file (for showing in the debug GUI). */
    public static synchronized String getFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "(ikke initialiseret)";
    }

    /**
     * Whether debug file logging is currently active.
     * True when both debugEnabled AND debugLogEnabled are set.
     */
    public static boolean isEnabled() {
        CelleConfig cfg = CelleScannerMod.config;
        return cfg != null
                && Boolean.TRUE.equals(cfg.debugEnabled)
                && Boolean.TRUE.equals(cfg.debugLogEnabled);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /** Appends a raw line (no formatting applied) to the file. Creates the file if needed. */
    private static void writeRaw(String line) {
        try {
            if (logFile.getParentFile() != null) {
                logFile.getParentFile().mkdirs();
            }
            PrintWriter writer = new PrintWriter(new FileWriter(logFile, /* append */ true));
            try {
                writer.println(line);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            // Best-effort: never crash the client over a log failure.
            System.err.println("[CelleScanner] DebugLog write failed: " + e);
        }
    }
}
