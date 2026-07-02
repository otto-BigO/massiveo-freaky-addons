package com.otto.cellescanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Checks the GitHub repo's latest release on launch and, if it's newer than the
 * running version, downloads the new jar into the mods folder and removes the
 * old one, so the next restart is on the new version. A mod can't relaunch
 * Minecraft itself, so it just tells the player to restart.
 *
 * On systems that lock the running jar (Windows), the old jar can't be deleted
 * while the game runs; in that case it falls back to telling the player where
 * to download the update manually rather than leaving two jars behind (which
 * would be a duplicate-mod crash on next launch).
 */
public class AutoUpdater {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String OWNER = "otto-BigO";
    private static final String REPO = "massiveo-freaky-addons";
    private static final String LATEST_URL = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    // The full release list (newest first), including pre-releases - used when
    // the player has opted in to updating to pre-release (test) builds.
    private static final String RELEASES_URL = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=30";

    // Set by the background check, read/shown on the main thread.
    private static volatile String latestVersion = null;
    private static volatile String status = "ikke tjekket";
    private static volatile String pendingMessage = null;

    private static volatile boolean checking = false;
    private boolean posted = false;

    public static String getLatestVersion() {
        return latestVersion;
    }

    public static String getStatus() {
        return status;
    }

    /** Starts the version check on a background daemon thread. Called when the Opdatering screen opens; no-op if a check is already running. */
    public static void checkAsync() {
        if (checking) {
            return;
        }
        checking = true;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    check();
                } catch (Exception e) {
                    status = "tjek fejlede: " + e.getMessage();
                    System.err.println("[CelleScanner] Update check failed: " + e);
                } finally {
                    checking = false;
                }
            }
        }, "Massiveo-AutoUpdater");
        t.setDaemon(true);
        t.start();
    }

    private static void check() throws Exception {
        status = "tjekker...";
        JsonObject release = fetchBestRelease();
        if (release == null || !release.has("tag_name")) {
            status = "ingen release fundet";
            return;
        }
        latestVersion = release.get("tag_name").getAsString();
        String current = CelleScannerMod.VERSION;

        if (compareVersions(latestVersion, current) <= 0) {
            status = "opdateret (" + current + ")";
            return;
        }
        status = "ny version: " + latestVersion;

        if (!CelleScannerMod.config.autoUpdateEnabled) {
            pendingMessage = EnumChatFormatting.AQUA + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Ny version " + latestVersion + " findes. Auto-opdatering er slået fra.";
            return;
        }

        String assetUrl = findJarAssetUrl(release);
        String assetName = findJarAssetName(release);
        if (assetUrl == null || assetName == null) {
            pendingMessage = EnumChatFormatting.AQUA + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Ny version " + latestVersion + " findes, men ingen jar i releasen.";
            return;
        }

        File self = runningJar();
        if (self == null) {
            // Dev environment (running from classes, not a jar) - nothing to swap.
            status = "ny version " + latestVersion + " (dev, springer download over)";
            return;
        }
        File modsDir = self.getParentFile();

        File tmp = new File(modsDir, assetName + ".tmp");
        download(assetUrl, tmp);

        if (self.delete()) {
            File dest = new File(modsDir, assetName);
            if (tmp.renameTo(dest)) {
                status = "hentet " + latestVersion + " - genstart";
                pendingMessage = EnumChatFormatting.GREEN + "[Massiveo] " + EnumChatFormatting.RESET
                        + "Opdateret til " + latestVersion + " - genstart spillet for at aktivere.";
            } else {
                status = "kunne ikke omdøbe";
                pendingMessage = EnumChatFormatting.RED + "[Massiveo] " + EnumChatFormatting.RESET
                        + "Kunne ikke installere opdateringen. Hent den her: " + releaseHtmlUrl(release);
            }
        } else {
            // Can't delete the running jar (locked, e.g. Windows). Don't leave a
            // second jar in mods - fall back to a manual-download message.
            tmp.delete();
            status = "kan ikke erstatte automatisk";
            pendingMessage = EnumChatFormatting.AQUA + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Ny version " + latestVersion + " findes. Hent den her: " + releaseHtmlUrl(release);
        }
    }

    /**
     * The release to offer: the newest stable one normally, or - when the
     * player has opted in - the newest of ALL releases including pre-release
     * (test) builds. "Newest" is by version, so a stable release always wins a
     * tie against a pre-release of the same base (see compareVersions).
     */
    private static JsonObject fetchBestRelease() throws Exception {
        if (!CelleScannerMod.config.autoUpdatePreRelease) {
            return fetchLatestRelease();
        }
        JsonElement el = fetchJson(RELEASES_URL);
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray arr = el.getAsJsonArray();
        JsonObject best = null;
        String bestTag = null;
        for (int i = 0; i < arr.size(); i++) {
            JsonObject r = arr.get(i).getAsJsonObject();
            if (r.has("draft") && r.get("draft").getAsBoolean()) {
                continue; // never offer an unpublished draft
            }
            if (!r.has("tag_name")) {
                continue;
            }
            String tag = r.get("tag_name").getAsString();
            if (best == null || compareVersions(tag, bestTag) > 0) {
                best = r;
                bestTag = tag;
            }
        }
        return best;
    }

    private static JsonObject fetchLatestRelease() throws Exception {
        JsonElement el = fetchJson(LATEST_URL);
        return el == null ? null : el.getAsJsonObject();
    }

    /** GETs a GitHub API URL and returns the parsed JSON, or null if there are no releases yet (404). */
    private static JsonElement fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons-Updater");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try {
            if (is == null) {
                throw new Exception("HTTP " + code);
            }
            InputStreamReader reader = new InputStreamReader(is, UTF8);
            JsonElement el = new JsonParser().parse(reader);
            reader.close();
            if (code == 404) {
                return null; // no releases yet
            }
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            return el;
        } finally {
            conn.disconnect();
        }
    }

    private static String findJarAssetUrl(JsonObject release) {
        if (!release.has("assets")) {
            return null;
        }
        JsonArray assets = release.getAsJsonArray("assets");
        for (int i = 0; i < assets.size(); i++) {
            JsonObject a = assets.get(i).getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            if (name.toLowerCase().endsWith(".jar")) {
                return a.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private static String findJarAssetName(JsonObject release) {
        if (!release.has("assets")) {
            return null;
        }
        JsonArray assets = release.getAsJsonArray("assets");
        for (int i = 0; i < assets.size(); i++) {
            JsonObject a = assets.get(i).getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            if (name.toLowerCase().endsWith(".jar")) {
                return name;
            }
        }
        return null;
    }

    private static String releaseHtmlUrl(JsonObject release) {
        return release.has("html_url") ? release.get("html_url").getAsString()
                : "https://github.com/" + OWNER + "/" + REPO + "/releases";
    }

    /** The jar this mod is running from, or null if running from a classes dir (dev). */
    private static File runningJar() {
        try {
            File f = new File(AutoUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void download(String url, File dest) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons-Updater");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        InputStream in = conn.getInputStream();
        try {
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
            conn.disconnect();
        }
    }

    /**
     * Positive if a &gt; b, negative if a &lt; b, 0 if equal. Ignores a leading
     * 'v'. Compares the numeric parts first (1.0.10 &gt; 1.0.9); on a tie, a
     * pre-release suffix ("-t1") ranks BELOW the plain release of the same base
     * (so 1.0.9 &gt; 1.0.9-t1), and two pre-releases compare by their suffix
     * number (1.0.9-t2 &gt; 1.0.9-t1). This is what lets the updater move between
     * test builds when pre-releases are enabled.
     */
    static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return Integer.compare(preReleaseRank(a), preReleaseRank(b));
    }

    /**
     * Ordering rank of the pre-release suffix: a plain release (no "-suffix")
     * outranks every pre-release of the same base, so it returns MAX_VALUE.
     * "1.0.9-t2" returns 2, "1.0.9-t1" returns 1; a suffix with no number is 0.
     */
    private static int preReleaseRank(String v) {
        if (v == null) {
            return Integer.MAX_VALUE;
        }
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        int dash = s.indexOf('-');
        if (dash < 0) {
            return Integer.MAX_VALUE; // no pre-release suffix -> a plain release
        }
        String digits = s.substring(dash + 1).replaceAll("^[^0-9]*", "").replaceAll("[^0-9].*$", "");
        try {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int[] parseVersion(String v) {
        if (v == null) {
            return new int[0];
        }
        v = v.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        String[] parts = v.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int val = 0;
            String digits = parts[i].replaceAll("[^0-9].*$", "");
            try {
                val = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
            }
            out[i] = val;
        }
        return out;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || posted || pendingMessage == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        posted = true;
        mc.thePlayer.addChatMessage(new ChatComponentText(pendingMessage));
    }
}
