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

        // Stage the download to a ".pending" file (not a .jar, so Forge never
        // loads a half-download or a second copy of the mod).
        File tmp = new File(modsDir, assetName + ".pending");
        tmp.delete();
        download(assetUrl, tmp);

        File dest = new File(modsDir, assetName);
        dest.delete(); // Delete target version if it exists from a previous failed update
        // Fast path: where the OS doesn't lock the running jar (Linux/macOS) we
        // can remove the old one and move the new one in right now.
        boolean swapped = false;
        if (self.delete()) {
            swapped = tmp.renameTo(dest);
        }
        if (swapped) {
            status = "hentet " + latestVersion + " - genstart";
            pendingMessage = EnumChatFormatting.GREEN + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Opdateret til " + latestVersion + " - genstart spillet for at aktivere.";
            return;
        }

        // Locked (Windows/locked jar): the JVM still holds the jar open, so it can't be
        // replaced in place. Defer the swap to game-exit - a tiny detached
        // helper waits for the file lock to clear, deletes the old jar, moves
        // the staged one into place, then removes itself. The staged file stays
        // as ".pending" until then, so we never leave two loadable jars behind.
        if (scheduleSwapOnExit(self, tmp, dest)) {
            status = "hentet " + latestVersion + " - luk spillet helt";
            pendingMessage = EnumChatFormatting.GREEN + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Opdatering til " + latestVersion + " hentet - luk spillet helt og aabn igen for at aktivere.";
        } else {
            tmp.delete();
            status = "kan ikke erstatte automatisk";
            pendingMessage = EnumChatFormatting.AQUA + "[Massiveo] " + EnumChatFormatting.RESET
                    + "Ny version " + latestVersion + " findes. Hent den her: " + releaseHtmlUrl(release);
        }
    }

    // Set once a swap has been staged, so re-checks don't register several hooks.
    private static volatile boolean swapScheduled = false;

    /**
     * Registers a JVM shutdown hook that, as the game exits, launches a small
     * detached OS helper which waits for the running jar's lock to clear,
     * deletes it, moves the staged download into its place, then deletes itself.
     * This is how the update applies on Windows, where the jar can't be replaced
     * while the game holds it open. Returns false if the helper couldn't be
     * written (the caller then falls back to a manual-download message).
     */
    private static boolean scheduleSwapOnExit(final File oldJar, final File pending, final File dest) {
        if (swapScheduled) {
            return true;
        }
        final boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        final File script;
        try {
            script = writeSwapScript(oldJar, pending, dest, windows);
            script.setExecutable(true); // Ensure script can run on Unix systems
        } catch (Exception e) {
            System.err.println("[CelleScanner] Could not stage update swap: " + e);
            return false;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessBuilder pb = windows
                            ? new ProcessBuilder("cmd.exe", "/c", "start", "/min", "", script.getAbsolutePath())
                            : new ProcessBuilder("/bin/sh", "-c", "nohup /bin/sh \"" + script.getAbsolutePath() + "\" >/dev/null 2>&1 &");
                    pb.directory(dest.getParentFile());
                    pb.start();
                } catch (Exception e) {
                    System.err.println("[CelleScanner] Update swap launch failed: " + e);
                }
            }
        }, "Massiveo-UpdateSwap"));
        swapScheduled = true;
        return true;
    }

    private static File writeSwapScript(File oldJar, File pending, File dest, boolean windows) throws Exception {
        File dir = dest.getParentFile();
        String old = oldJar.getAbsolutePath();
        String pend = pending.getAbsolutePath();
        String fin = dest.getAbsolutePath();
        File script;
        String content;
        if (windows) {
            // Loop until the old jar can be deleted (i.e. the JVM has exited and
            // released it), then move the staged jar into place and self-delete.
            script = new File(dir, "massiveo-update.bat");
            content = "@echo off\r\n"
                    + "setlocal\r\n"
                    + ":wait\r\n"
                    + "del \"" + old + "\" >nul 2>&1\r\n"
                    + "if exist \"" + old + "\" (\r\n"
                    + "  ping -n 2 127.0.0.1 >nul\r\n"
                    + "  goto wait\r\n"
                    + ")\r\n"
                    + "move /y \"" + pend + "\" \"" + fin + "\" >nul 2>&1\r\n"
                    + "del \"%~f0\"\r\n";
        } else {
            script = new File(dir, "massiveo-update.sh");
            content = "#!/bin/sh\n"
                    + "while ! rm -f \"" + old + "\" 2>/dev/null; do sleep 1; done\n"
                    + "mv -f \"" + pend + "\" \"" + fin + "\"\n"
                    + "rm -- \"$0\"\n";
        }
        OutputStream out = new FileOutputStream(script);
        try {
            out.write(content.getBytes(UTF8));
        } finally {
            out.close();
        }
        return script;
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
            if (f.isFile() && f.getName().toLowerCase().endsWith(".jar")) {
                return f;
            }
        } catch (Exception ignored) {
        }

        // Fallback: search the mods directory for a cellescanner jar file
        try {
            File modsDir = new File(Minecraft.getMinecraft().mcDataDir, "mods");
            if (modsDir.isDirectory()) {
                File[] files = modsDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile() && f.getName().toLowerCase().endsWith(".jar") && f.getName().toLowerCase().contains("cellescanner")) {
                            return f;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static void download(String url, File dest) throws Exception {
        String currentUrl = url;
        HttpURLConnection conn = null;
        InputStream in = null;
        int redirects = 0;

        while (redirects < 5) {
            conn = (HttpURLConnection) new URL(currentUrl).openConnection();
            conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons-Updater");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60000);

            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307
                    || status == 308) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    currentUrl = loc;
                    redirects++;
                    conn.disconnect();
                    continue;
                }
            }
            if (status < 200 || status >= 300) {
                throw new Exception("HTTP " + status);
            }
            in = conn.getInputStream();
            break;
        }

        if (in == null) {
            throw new Exception("Too many redirects");
        }

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
