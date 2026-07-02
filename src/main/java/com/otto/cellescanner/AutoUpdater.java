package com.otto.cellescanner;

import com.google.gson.JsonArray;
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
        JsonObject release = fetchLatestRelease();
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

    private static JsonObject fetchLatestRelease() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(LATEST_URL).openConnection();
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
            JsonObject obj = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();
            if (code == 404) {
                return null; // no releases yet
            }
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            return obj;
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

    /** Positive if a > b, negative if a < b, 0 if equal. Ignores a leading 'v' and any non-numeric suffix. */
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
        return 0;
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
