package com.otto.cellescanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports this client's local scan results into a Discord webhook pointed at
 * the companion CelleScannerBot's dedicated "reports" channel. The bot merges
 * whatever this (and every other connected client) posts into one shared
 * dashboard.
 *
 * A busy scan can produce 90+ celler, well past Discord's 2000-char message
 * limit, so the list is split across several messages. Discord rate-limits a
 * webhook hard, so those messages must NOT be fired as one burst - this class:
 *
 *   1. Paces messages apart (a floor delay, and it waits out the bucket when
 *      Discord's X-RateLimit-Remaining header says it's empty).
 *   2. On a 429, sleeps exactly as long as Discord's retry_after asks and then
 *      retries the same message instead of dropping it and spamming chat.
 *   3. Coalesces: only the newest pending report is kept, so if a send is slow
 *      (backing off), the reports queued behind it collapse into one instead
 *      of piling up and then bursting.
 *
 * Everything runs on a single background daemon thread, so a slow or
 * unreachable webhook can never stall the game.
 */
public final class ReportWebhookClient {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    // Kept small so even max-length id/owner/status fields per entry can't push
    // a chunk's JSON past Discord's 2000-char message content limit.
    private static final int CHUNK_SIZE = 12;

    // Retries for a single message before giving up on it. 429s (rate limits)
    // and transient 5xx are retried using the delay Discord itself asks for; a
    // hard 4xx (e.g. 404 bad/expired webhook) is treated as permanent.
    private static final int MAX_RETRIES = 5;

    // A floor between consecutive posts even when Discord says budget remains,
    // so a large cache trickles out instead of firing as one burst.
    private static final long MIN_SPACING_MS = 400L;

    // Ceiling on any single backoff sleep, so a bogus retry_after can't wedge
    // the worker for minutes.
    private static final long MAX_BACKOFF_MS = 30000L;

    // Single-slot mailbox: only the newest pending report matters. One that
    // arrives while an older one is still sending simply replaces it, so
    // retries/backoff never build a backlog that later bursts.
    private static final AtomicReference<Report> PENDING = new AtomicReference<Report>();
    private static final Object WORKER_LOCK = new Object();
    private static Thread worker;

    // Only chat-spam a failure once per failure streak.
    private static volatile boolean reportErrorWarned = false;

    private ReportWebhookClient() {
    }

    /**
     * Immutable snapshot of one celle, taken on the client thread so the
     * background sender never reads fields the scan loop is concurrently
     * mutating.
     */
    private static final class Snap {
        final String id;
        final String status;
        final String owner;
        final long remainingSeconds;
        final long lastSeen;
        final boolean confirmed;

        Snap(Celle c) {
            this.id = c.celleId;
            this.status = c.status == CelleStatus.TIL_SALG ? "TIL_SALG" : "SOLGT";
            this.owner = c.owner;
            this.remainingSeconds = c.liveRemainingSeconds();
            this.lastSeen = c.lastSeen;
            this.confirmed = c.timerConfirmed;
        }
    }

    private static final class Report {
        final String url;
        final String reporter;
        final List<Snap> celler;
        final List<String> specialIds;

        Report(String url, String reporter, List<Snap> celler, List<String> specialIds) {
            this.url = url;
            this.reporter = reporter;
            this.celler = celler;
            this.specialIds = specialIds;
        }
    }

    /** Fire-and-forget: hands the newest report to the background worker. */
    public static void report(final List<Celle> celler, final List<String> specialIds) {
        final String url = CelleScannerMod.config.reportsWebhookUrl;
        if (url == null || url.trim().isEmpty()) {
            return;
        }

        // Snapshot everything on the calling (client) thread.
        List<Snap> snaps = new ArrayList<Snap>();
        for (Celle c : celler) {
            if (c.celleId != null && !c.celleId.isEmpty()) {
                snaps.add(new Snap(c));
            }
        }
        List<String> specials = new ArrayList<String>();
        for (String id : specialIds) {
            if (id != null && !id.trim().isEmpty()) {
                specials.add(id.trim());
            }
        }

        PENDING.set(new Report(url.trim(), reporterName(), snaps, specials));
        ensureWorker();
    }

    /** Used by /celler bot test - a small report with no celler, just to verify the webhook url works. */
    public static void testConnection() {
        final String url = CelleScannerMod.config.reportsWebhookUrl;
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JsonObject payload = buildReportPayload(reporterName(),
                            Collections.<Snap>emptyList(), Collections.<String>emptyList());
                    postWithRetry(url.trim(), payload);
                    notifyMain("Webhook svarede OK - forbindelsen virker.");
                } catch (Exception e) {
                    System.err.println("[CelleScanner] Webhook test failed: " + e);
                    notifyMain("Webhook-test fejlede: " + e.getMessage());
                }
            }
        }, "CelleScanner-WebhookTest");
        t.setDaemon(true);
        t.start();
    }

    private static void ensureWorker() {
        synchronized (WORKER_LOCK) {
            if (worker == null) {
                worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        workerLoop();
                    }
                }, "CelleScanner-ReportWebhook");
                worker.setDaemon(true);
                worker.start();
            }
            WORKER_LOCK.notifyAll();
        }
    }

    private static void workerLoop() {
        while (true) {
            Report r = PENDING.getAndSet(null);
            if (r == null) {
                synchronized (WORKER_LOCK) {
                    // Re-check under the lock so a report set between the
                    // getAndSet above and here can't be missed.
                    if (PENDING.get() == null) {
                        try {
                            WORKER_LOCK.wait();
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
                continue;
            }
            try {
                send(r);
                reportErrorWarned = false;
            } catch (InterruptedException e) {
                return; // shutting down
            } catch (Exception e) {
                System.err.println("[CelleScanner] Webhook report failed: " + e);
                if (!reportErrorWarned) {
                    reportErrorWarned = true;
                    notifyMain("Rapportering til webhook fejlede: " + e.getMessage());
                }
            }
        }
    }

    private static void send(Report r) throws IOException, InterruptedException {
        List<List<Snap>> chunks = chunk(r.celler, CHUNK_SIZE);
        if (chunks.isEmpty()) {
            // still send one (empty) report so reporter/specialIds reach the bot
            chunks.add(new ArrayList<Snap>());
        }
        for (List<Snap> part : chunks) {
            // A newer report landed while we were sending - abandon the rest of
            // this now-stale one and let the loop pick up the fresh data.
            if (PENDING.get() != null) {
                return;
            }
            JsonObject payload = buildReportPayload(r.reporter, part, r.specialIds);
            postWithRetry(r.url, payload);
        }
    }

    /** Posts one message, retrying on 429/5xx with Discord's own delay, then paces the next one. */
    private static void postWithRetry(String url, JsonObject payload) throws IOException, InterruptedException {
        for (int attempt = 0; ; attempt++) {
            Response resp = postJson(url, payload);

            if (resp.code >= 200 && resp.code < 300) {
                // Pace the next message: if Discord says this bucket is now
                // empty, wait for it to reset; otherwise a gentle floor.
                if (resp.remaining == 0 && resp.resetAfterMs > 0) {
                    sleep(Math.min(resp.resetAfterMs, MAX_BACKOFF_MS));
                } else {
                    sleep(MIN_SPACING_MS);
                }
                return;
            }

            if (resp.code == 429 || (resp.code >= 500 && resp.code < 600)) {
                if (attempt >= MAX_RETRIES) {
                    throw new IOException("HTTP " + resp.code + " efter " + (attempt + 1) + " forsøg"
                            + (resp.body.isEmpty() ? "" : " - " + truncate(resp.body, 150)));
                }
                long wait = resp.retryAfterMs > 0 ? resp.retryAfterMs : backoff(attempt);
                sleep(Math.min(wait, MAX_BACKOFF_MS));
                continue;
            }

            // Any other 4xx is permanent (bad/expired webhook, etc.) - don't retry.
            throw new IOException("HTTP " + resp.code + (resp.body.isEmpty() ? "" : " - " + truncate(resp.body, 150)));
        }
    }

    private static final class Response {
        int code;
        String body = "";
        long retryAfterMs;   // 0 if unknown
        int remaining = -1;  // -1 if unknown
        long resetAfterMs;   // 0 if unknown
    }

    /** POSTs the payload as the webhook message's plain-text content (not an embed) so the bot can JSON.parse() it back out. */
    private static Response postJson(String webhookUrl, JsonObject payload) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("content", payload.toString());

        HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "CelleScannerMod/1.0 (Forge 1.8.9 client mod)");
        conn.setDoOutput(true);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        byte[] bytes = body.toString().getBytes(UTF8);
        OutputStream os = conn.getOutputStream();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }

        Response resp = new Response();
        resp.code = conn.getResponseCode();
        InputStream is = (resp.code >= 200 && resp.code < 300) ? conn.getInputStream() : conn.getErrorStream();
        resp.body = readAll(is);
        resp.remaining = parseIntHeader(conn, "X-RateLimit-Remaining", -1);
        resp.resetAfterMs = parseSecondsHeaderMs(conn, "X-RateLimit-Reset-After");
        resp.retryAfterMs = parseRetryAfterMs(conn, resp.body);
        conn.disconnect();

        if (resp.code < 200 || resp.code >= 300) {
            System.err.println("[CelleScanner] Webhook returned " + resp.code
                    + (resp.body.isEmpty() ? "" : ": " + truncate(resp.body, 150)));
        }
        return resp;
    }

    private static int parseIntHeader(HttpURLConnection conn, String name, int def) {
        String v = conn.getHeaderField(name);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseSecondsHeaderMs(HttpURLConnection conn, String name) {
        String v = conn.getHeaderField(name);
        if (v == null) {
            return 0L;
        }
        try {
            return (long) (Double.parseDouble(v.trim()) * 1000.0);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * How long Discord asks us to wait after a 429. Prefers the JSON body's
     * retry_after (seconds, float) that the webhook API returns, and falls back
     * to the standard Retry-After header. A small buffer is added so we land
     * just past the reset rather than right on it.
     */
    private static long parseRetryAfterMs(HttpURLConnection conn, String body) {
        try {
            if (body != null && !body.isEmpty()) {
                JsonObject o = new JsonParser().parse(body).getAsJsonObject();
                if (o.has("retry_after")) {
                    return (long) (o.get("retry_after").getAsDouble() * 1000.0) + 250L;
                }
            }
        } catch (Exception ignored) {
        }
        String h = conn.getHeaderField("Retry-After");
        if (h != null) {
            try {
                return (long) (Double.parseDouble(h.trim()) * 1000.0) + 250L;
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }

    private static long backoff(int attempt) {
        long base = 1000L << Math.min(attempt, 5); // 1s, 2s, 4s, ... capped below
        return Math.min(base, MAX_BACKOFF_MS);
    }

    private static void sleep(long ms) throws InterruptedException {
        if (ms > 0) {
            Thread.sleep(ms);
        }
    }

    private static List<List<Snap>> chunk(List<Snap> celler, int size) {
        List<List<Snap>> result = new ArrayList<List<Snap>>();
        for (int i = 0; i < celler.size(); i += size) {
            result.add(new ArrayList<Snap>(celler.subList(i, Math.min(celler.size(), i + size))));
        }
        return result;
    }

    private static String reporterName() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && mc.thePlayer.getName() != null) {
            return mc.thePlayer.getName();
        }
        return "ukendt";
    }

    private static JsonObject buildReportPayload(String reporter, List<Snap> celler, List<String> specialIds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("reporter", reporter);

        JsonArray specialArray = new JsonArray();
        for (String id : specialIds) {
            specialArray.add(new JsonPrimitive(id));
        }
        payload.add("specialIds", specialArray);

        JsonArray celleArray = new JsonArray();
        for (Snap c : celler) {
            if (c.id == null || c.id.isEmpty()) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", c.id);
            entry.addProperty("status", c.status);
            if (c.owner != null) {
                entry.addProperty("owner", c.owner);
            }
            entry.addProperty("remainingSeconds", Long.valueOf(c.remainingSeconds));
            // This celle's own last-seen time, NOT "now" - otherwise the bot's
            // "freshest report wins" merge would treat a celle we last saw ages
            // ago (still in cache but out of render distance) as brand new.
            entry.addProperty("lastSeen", Long.valueOf(c.lastSeen));
            entry.addProperty("confirmed", Boolean.valueOf(c.confirmed));
            celleArray.add(entry);
        }
        payload.add("celler", celleArray);
        return payload;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(is, UTF8);
        char[] buf = new char[1024];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        reader.close();
        return sb.toString();
    }

    /** Runs a chat message on the main client thread - safe to call from the report thread. */
    private static void notifyMain(final String text) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                CelleActions.message(text);
            }
        });
    }
}
