package com.otto.cellescanner;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fetches a player's skin by name from Mojang's public API, for showing offline
 * players in the Player Info menu. All network + image decoding happens on a
 * background thread; the decoded image is uploaded to a GL texture on the render
 * thread (in {@link #get}), since GL calls must run there. Results are cached per
 * name for the session.
 */
public final class SkinFetcher {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Map<String, Entry> CACHE = new HashMap<String, Entry>();

    public static final class Entry {
        public volatile String status = "henter";     // henter | ok | fejl
        public volatile boolean slim = false;
        public volatile boolean legacy = false;        // 64x32 skin
        public volatile ResourceLocation location;
        volatile BufferedImage pending;                // decoded, awaiting GL upload
    }

    private SkinFetcher() {
    }

    /** Returns the cached entry for a name, kicking off a fetch the first time. Call from the render thread. */
    public static Entry get(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        Entry e = CACHE.get(key);
        if (e == null) {
            e = new Entry();
            CACHE.put(key, e);
            fetchAsync(username, e);
        }
        // Upload a freshly-decoded image to a GL texture (render thread only).
        if (e.pending != null && e.location == null) {
            BufferedImage img = e.pending;
            e.pending = null;
            try {
                DynamicTexture tex = new DynamicTexture(img);
                e.location = Minecraft.getMinecraft().getTextureManager()
                        .getDynamicTextureLocation("massiveo_skin_" + key, tex);
                e.status = "ok";
            } catch (Throwable t) {
                e.status = "fejl";
            }
        }
        return e;
    }

    private static void fetchAsync(final String username, final Entry e) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String uuid = json(httpGet("https://api.mojang.com/users/profiles/minecraft/" + username))
                            .get("id").getAsString();
                    JsonObject profile = json(httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid));
                    String base64 = profile.getAsJsonArray("properties").get(0).getAsJsonObject()
                            .get("value").getAsString();
                    JsonObject tex = new JsonParser().parse(new String(Base64.getDecoder().decode(base64), UTF8))
                            .getAsJsonObject().getAsJsonObject("textures");
                    if (tex == null || !tex.has("SKIN")) {
                        e.status = "fejl";
                        return;
                    }
                    JsonObject skin = tex.getAsJsonObject("SKIN");
                    String url = skin.get("url").getAsString();
                    boolean slim = skin.has("metadata")
                            && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());

                    BufferedImage img = downloadImage(url);
                    if (img == null) {
                        e.status = "fejl";
                        return;
                    }
                    e.slim = slim;
                    e.legacy = img.getHeight() < 64;
                    e.pending = img;
                } catch (Throwable ex) {
                    e.status = "fejl";
                    System.err.println("[CelleScanner] Skin fetch failed for " + username + ": " + ex);
                }
            }
        }, "Massiveo-SkinFetcher");
        t.setDaemon(true);
        t.start();
    }

    private static String httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code);
        }
        InputStream is = conn.getInputStream();
        try {
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                sb.append(new String(buf, 0, n, UTF8));
            }
            return sb.toString();
        } finally {
            is.close();
            conn.disconnect();
        }
    }

    private static BufferedImage downloadImage(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "MassiveoFreakyAddons");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        InputStream is = conn.getInputStream();
        try {
            return ImageIO.read(is);
        } finally {
            is.close();
            conn.disconnect();
        }
    }

    private static JsonObject json(String s) {
        return new JsonParser().parse(s).getAsJsonObject();
    }
}
