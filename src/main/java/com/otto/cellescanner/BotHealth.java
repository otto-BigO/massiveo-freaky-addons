package com.otto.cellescanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Whether the bot is actually there.
 *
 * A webhook post succeeding proves nothing about the bot. Discord accepts the
 * message whether or not anything is listening, so a report can come back 204
 * and still be read by nobody. The only honest proof is the bot's own output:
 * it republishes what it knows to the website, stamped with the moment it did
 * so. A recent stamp means the bot is up and processing; an old one means it is
 * down or stuck; no answer at all means the whole box is unreachable.
 *
 * Checked on a background thread and never blocks a report. Reporting continues
 * regardless of what this says, because the bot reads the channel backlog when
 * it starts, so a report sent during an outage is delayed rather than lost.
 */
public final class BotHealth {

    /** The bot's own published output. Its timestamp is the heartbeat. */
    private static final String STATUS_URL = "https://ottomansfield.com/data/celler.json";

    /** Older than this and the bot is not keeping up, whatever the reason. */
    private static final long STALE_AFTER_MS = 30 * 60 * 1000L;

    /** How often to look, when reporting is on. */
    private static final long CHECK_EVERY_MS = 3 * 60 * 1000L;

    public static final int UNKNOWN = 0;
    public static final int ONLINE = 1;
    public static final int STALE = 2;
    public static final int UNREACHABLE = 3;

    private static volatile int state = UNKNOWN;
    private static volatile String detail = "ikke tjekket endnu";
    private static volatile long lastCheckedAt = 0L;
    private static volatile long lastGeneratedAt = 0L;
    private static volatile boolean checking = false;

    private BotHealth() {
    }

    public static int state() {
        return state;
    }

    /** One line for a screen, already coloured. */
    public static String line() {
        switch (state) {
            case ONLINE:
                return net.minecraft.util.EnumChatFormatting.GREEN + "Botten er online"
                        + net.minecraft.util.EnumChatFormatting.GRAY + "  (" + detail + ")";
            case STALE:
                return net.minecraft.util.EnumChatFormatting.GOLD + "Botten svarer ikke"
                        + net.minecraft.util.EnumChatFormatting.GRAY + "  (" + detail + ")";
            case UNREACHABLE:
                return net.minecraft.util.EnumChatFormatting.RED + "Endpoint ikke tilgængeligt"
                        + net.minecraft.util.EnumChatFormatting.GRAY + "  (" + detail + ")";
            default:
                return net.minecraft.util.EnumChatFormatting.GRAY + "Botstatus: " + detail;
        }
    }

    /** Short form for a HUD or a chat line. */
    public static String shortLine() {
        switch (state) {
            case ONLINE:      return "online";
            case STALE:       return "svarer ikke";
            case UNREACHABLE: return "ikke tilgængelig";
            default:          return "ukendt";
        }
    }

    /** True when there is a reason to warn the player about the bot. */
    public static boolean isTrouble() {
        return state == STALE || state == UNREACHABLE;
    }

    /**
     * Checks if it has been long enough since the last one. Cheap to call from
     * a tick, since it does nothing until the interval is up.
     */
    public static void checkIfDue() {
        if (checking) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastCheckedAt != 0L && now - lastCheckedAt < CHECK_EVERY_MS) {
            return;
        }
        checkNow();
    }

    /** Checks on a background thread. Never blocks the game. */
    public static void checkNow() {
        if (checking) {
            return;
        }
        checking = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    probe();
                } catch (Exception e) {
                    state = UNREACHABLE;
                    detail = shortReason(e);
                } finally {
                    lastCheckedAt = System.currentTimeMillis();
                    checking = false;
                }
            }
        }, "CelleScanner-BotHealth");
        t.setDaemon(true);
        t.start();
    }

    private static void probe() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(STATUS_URL).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons/" + MassiveOsFreakyAddons.VERSION);
        // The file is rewritten constantly, so a cached copy would be a lie
        // about how recently the bot did anything.
        conn.setRequestProperty("Cache-Control", "no-cache");

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            state = UNREACHABLE;
            // 5xx from the tunnel means the machine behind it is down, which is
            // a different thing from the site being missing.
            detail = code >= 500 ? "server svarer " + code : "HTTP " + code;
            return;
        }

        InputStreamReader reader = new InputStreamReader(conn.getInputStream(), Charset.forName("UTF-8"));
        try {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            long generatedAt = root.has("generatedAt") ? root.get("generatedAt").getAsLong() : 0L;
            lastGeneratedAt = generatedAt;

            if (generatedAt <= 0L) {
                state = STALE;
                detail = "ingen tidsstempel";
                return;
            }
            long age = System.currentTimeMillis() - generatedAt;
            if (age > STALE_AFTER_MS) {
                state = STALE;
                detail = "sidst opdateret " + human(age) + " siden";
            } else {
                state = ONLINE;
                detail = "opdateret " + human(age) + " siden";
            }
        } finally {
            try { reader.close(); } catch (Exception ignored) { }
        }
    }

    /** Turns the exception into something worth showing rather than a stack trace. */
    private static String shortReason(Exception e) {
        String n = e.getClass().getSimpleName();
        if (n.contains("UnknownHost")) {
            return "kan ikke slå adressen op";
        }
        if (n.contains("Timeout") || n.contains("SocketTimeout")) {
            return "svarede ikke i tide";
        }
        if (n.contains("Connect")) {
            return "ingen forbindelse";
        }
        String m = e.getMessage();
        return m == null || m.isEmpty() ? n : m;
    }

    private static String human(long ms) {
        long mins = ms / 60000L;
        if (mins < 1) {
            return "under et minut";
        }
        if (mins < 60) {
            return mins + " min";
        }
        long hours = mins / 60;
        if (hours < 24) {
            return hours + " t";
        }
        return (hours / 24) + " d";
    }
}
