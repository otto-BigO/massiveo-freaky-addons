package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fetches and holds the FreakyVille price guide (the same data the website's
 * /priser pages show) from their public API, for the in-game Prisguide browser.
 * The guide is a tree: block (A/B/C/Friheden) -> category -> price groups, each
 * group having a DB value range or a free-text price note.
 */
public final class PriceGuide {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String CATS_URL = "https://freakyville.dk/api/heads/categories";
    private static final String GROUPS_URL = "https://freakyville.dk/api/heads/price-groups";

    public static final class Cat {
        public int id;
        public String name;
        public Integer parent;
    }

    public static final class Group {
        public int id;
        public String name;
        public String minDbValue;
        public String maxDbValue;
        public String priceInfo;
        public int categoryId;
        public int sorting;
    }

    private static final class Resp<T> {
        boolean success;
        List<T> items;
    }

    private static final Gson GSON = new Gson();

    private static volatile boolean loading = false;
    private static volatile boolean loaded = false;
    private static volatile boolean failed = false;
    private static volatile String error = null;

    private static volatile List<Cat> cats = new ArrayList<Cat>();
    private static volatile List<Group> groups = new ArrayList<Group>();

    private PriceGuide() {
    }

    public static boolean isLoading() {
        return loading;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isFailed() {
        return failed;
    }

    public static String getError() {
        return error;
    }

    /** Kicks off a background fetch if one isn't already running / done. Pass force=true to refetch. */
    public static void fetch(boolean force) {
        if (loading || (loaded && !force)) {
            return;
        }
        loading = true;
        failed = false;
        error = null;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Cat> c = fetchList(CATS_URL, new TypeToken<Resp<Cat>>() {
                    }.getType());
                    List<Group> g = fetchList(GROUPS_URL, new TypeToken<Resp<Group>>() {
                    }.getType());
                    cats = c;
                    groups = g;
                    loaded = true;
                } catch (Exception e) {
                    error = e.getMessage();
                    failed = true;
                    System.err.println("[CelleScanner] Prisguide-hentning fejlede: " + e);
                } finally {
                    loading = false;
                }
            }
        }, "Massiveo-PriceGuide");
        t.setDaemon(true);
        t.start();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> fetchList(String url, Type respType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "CelleScannerMod/1.0 (Forge 1.8.9 client mod)");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        try {
            InputStreamReader reader = new InputStreamReader(is, UTF8);
            Resp<T> resp = GSON.fromJson(reader, respType);
            reader.close();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            return resp != null && resp.items != null ? resp.items : new ArrayList<T>();
        } finally {
            conn.disconnect();
        }
    }

    private static boolean hasGroups(int catId) {
        for (Group g : groups) {
            if (g.categoryId == catId) {
                return true;
            }
        }
        return false;
    }

    private static final Comparator<Cat> BY_NAME = new Comparator<Cat>() {
        @Override
        public int compare(Cat a, Cat b) {
            String an = a.name == null ? "" : a.name;
            String bn = b.name == null ? "" : b.name;
            return an.compareToIgnoreCase(bn);
        }
    };

    /** Top-level blocks (no parent) that contain any priced category below them. */
    public static List<Cat> topBlocks() {
        List<Cat> result = new ArrayList<Cat>();
        for (Cat c : cats) {
            boolean top = c.parent == null || c.parent == 0;
            if (top && anyPricedChild(c.id)) {
                result.add(c);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    private static boolean anyPricedChild(int blockId) {
        for (Cat c : cats) {
            if (c.parent != null && c.parent == blockId && hasGroups(c.id)) {
                return true;
            }
        }
        return false;
    }

    /** Priced categories directly under a block. */
    public static List<Cat> categoriesIn(int blockId) {
        List<Cat> result = new ArrayList<Cat>();
        for (Cat c : cats) {
            if (c.parent != null && c.parent == blockId && hasGroups(c.id)) {
                result.add(c);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /** Price groups in a category, in the site's display order. */
    public static List<Group> groupsIn(int catId) {
        List<Group> result = new ArrayList<Group>();
        for (Group g : groups) {
            if (g.categoryId == catId) {
                result.add(g);
            }
        }
        Collections.sort(result, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return Integer.compare(b.sorting, a.sorting);
            }
        });
        return result;
    }

    public static String nameOf(int catId) {
        for (Cat c : cats) {
            if (c.id == catId) {
                return c.name;
            }
        }
        return "?";
    }

    /** Human display of a group's value: a DB range, a single DB value, or its free-text note. */
    public static String valueText(Group g) {
        if (g.minDbValue != null && !g.minDbValue.isEmpty()) {
            String mn = trimDb(g.minDbValue);
            String mx = trimDb(g.maxDbValue);
            if (mx == null || mn.equals(mx)) {
                return mn + " DB";
            }
            return mn + "-" + mx + " DB";
        }
        return g.priceInfo != null && !g.priceInfo.trim().isEmpty() ? g.priceInfo.trim() : "?";
    }

    private static String trimDb(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(s);
            if (d == Math.floor(d)) {
                return Long.toString((long) d);
            }
            String r = String.format(Locale.US, "%.2f", d);
            while (r.endsWith("0")) {
                r = r.substring(0, r.length() - 1);
            }
            return r;
        } catch (NumberFormatException e) {
            return s;
        }
    }
}
