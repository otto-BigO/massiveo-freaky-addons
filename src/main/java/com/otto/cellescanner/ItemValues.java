package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item Value addon: adds a "Værdi:" line to an item's tooltip based on the
 * FreakyVille price guide.
 *
 * Prices live in config/massiveo_prices.json - a plain JSON object mapping an
 * item id to a value string (shown as-is), e.g.:
 *
 *   {
 *     "minecraft:wool": "25-35 DB",
 *     "minecraft:stained_hardened_clay": "15-20 DB",
 *     "minecraft:wool:14": "40 DB"
 *   }
 *
 * A key is the item's registry id ("minecraft:wool"); for items with subtypes
 * (wool, dye, stained clay, ...) append ":<meta>" for a per-variant price that
 * overrides the base. The FreakyVille site only prices a few item groups (most
 * of it is head rarities - see the Prisguide addon for those), so this list is
 * seeded with those groups and meant to be edited by hand. Edit the file and
 * hit "Genindlæs priser" - no rebuild or restart needed.
 */
public class ItemValues {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Seeded from the site's "Rare Items" category (freakyville.dk/priser/nprison/30):
    //  - wool           -> "Rare Uld"      25-35 DB
    //  - stained clay   -> "Clay priser"   15-20 DB
    //  - everything else in that category -> "Design blocks" 3-6 DB
    // Refine per-colour with ":meta" keys, or edit the json directly.
    private static final Map<String, String> DEFAULT_PRICES = new LinkedHashMap<String, String>();
    static {
        DEFAULT_PRICES.put("minecraft:wool", "25-35 DB");
        DEFAULT_PRICES.put("minecraft:stained_hardened_clay", "15-20 DB");
        DEFAULT_PRICES.put("minecraft:hardened_clay", "15-20 DB");

        String design = "3-6 DB";
        String[] designBlocks = {
                "minecraft:glass", "minecraft:glass_pane", "minecraft:stained_glass_pane",
                "minecraft:carpet", "minecraft:banner", "minecraft:bookshelf", "minecraft:rail",
                "minecraft:stonebrick", "minecraft:coal_ore", "minecraft:lapis_ore", "minecraft:sand",
                "minecraft:string", "minecraft:paper", "minecraft:bed", "minecraft:painting",
                "minecraft:fishing_rod", "minecraft:tripwire_hook", "minecraft:hopper_minecart",
                "minecraft:golden_helmet", "minecraft:stone_pickaxe",
                // andesite / diorite / granite (+ their polished variants), not plain stone:0
                "minecraft:stone:1", "minecraft:stone:2", "minecraft:stone:3",
                "minecraft:stone:4", "minecraft:stone:5", "minecraft:stone:6",
                // the specific rare dyes in the category (red, purple, cyan, magenta, orange, bonemeal)
                "minecraft:dye:1", "minecraft:dye:5", "minecraft:dye:6",
                "minecraft:dye:13", "minecraft:dye:14", "minecraft:dye:15",
        };
        for (String key : designBlocks) {
            DEFAULT_PRICES.put(key, design);
        }
    }

    private static File file;
    private static Map<String, String> prices = new HashMap<String, String>();

    public static void init(File configDir) {
        file = new File(configDir, "massiveo_prices.json");
        if (!file.exists()) {
            writeDefault();
        }
        load();
    }

    private static void writeDefault() {
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(DEFAULT_PRICES, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("[CelleScanner] Kunne ikke skrive standard-prisfil: " + e);
        }
    }

    public static void load() {
        if (file == null || !file.exists()) {
            prices = new HashMap<String, String>(DEFAULT_PRICES);
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            Type type = new TypeToken<HashMap<String, String>>() {
            }.getType();
            Map<String, String> loaded = GSON.fromJson(reader, type);
            prices = loaded != null ? loaded : new HashMap<String, String>(DEFAULT_PRICES);
        } catch (Exception e) {
            System.err.println("[CelleScanner] Kunne ikke læse prisfil, bruger standard: " + e);
            prices = new HashMap<String, String>(DEFAULT_PRICES);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

        // Top up with any default prices the file is missing (e.g. a file made
        // before new items were added) and persist, so the file and the count
        // stay in sync. Values you've customized for existing keys are kept.
        int before = prices.size();
        for (Map.Entry<String, String> e : DEFAULT_PRICES.entrySet()) {
            if (!prices.containsKey(e.getKey())) {
                prices.put(e.getKey(), e.getValue());
            }
        }
        if (prices.size() != before) {
            savePrices();
        }
    }

    private static void savePrices() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(prices, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("[CelleScanner] Kunne ikke gemme prisfil: " + e);
        }
    }

    public static int count() {
        return prices.size();
    }

    private static boolean isDiamondArmor(Item item) {
        return item == Items.diamond_helmet || item == Items.diamond_chestplate
                || item == Items.diamond_leggings || item == Items.diamond_boots;
    }

    /** Value string for this stack, or null if we have no price for it. */
    private static String valueFor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Item item = stack.getItem();

        // "Leg gear" = legendary armour = Protection 5 diamond armour (enchant
        // id 0), which no plain item-id key can capture, so it's checked here.
        if (isDiamondArmor(item) && EnchantmentHelper.getEnchantmentLevel(0, stack) >= 5) {
            return "6-8 DB";
        }

        ResourceLocation id = Item.itemRegistry.getNameForObject(item);
        if (id == null) {
            return null;
        }
        String base = id.toString();
        if (item.getHasSubtypes()) {
            String withMeta = prices.get(base + ":" + stack.getItemDamage());
            if (withMeta != null) {
                return withMeta;
            }
        }
        return prices.get(base);
    }

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!CelleScannerMod.config.itemValueEnabled || event.itemStack == null || event.toolTip == null) {
            return;
        }
        String value = valueFor(event.itemStack);
        if (value == null || value.isEmpty()) {
            return;
        }

        // Skyblocker-style block appended at the bottom: gold labels, aqua
        // right-aligned values, plus a diamond-equivalent line (1 DB = 9 dia).
        java.util.List<String[]> rows = new java.util.ArrayList<String[]>();
        rows.add(new String[]{"Værdi:", value});
        long[] dia = diamondRange(value);
        if (dia != null) {
            String diaText = dia[0] == dia[1] ? fmt(dia[0]) : fmt(dia[0]) + " - " + fmt(dia[1]);
            rows.add(new String[]{"Diamanter:", diaText});
        }

        event.toolTip.add("");
        event.toolTip.addAll(rightAlign(rows));
    }

    private static final java.util.regex.Pattern DB_PATTERN =
            java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:-\\s*(\\d+(?:\\.\\d+)?))?\\s*DB", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** min/max diamonds for a "X DB" / "X-Y DB" value (1 DB = 9 diamonds), or null if not that format. */
    private static long[] diamondRange(String value) {
        java.util.regex.Matcher m = DB_PATTERN.matcher(value);
        if (!m.find()) {
            return null;
        }
        try {
            double min = Double.parseDouble(m.group(1));
            double max = m.group(2) != null ? Double.parseDouble(m.group(2)) : min;
            return new long[]{Math.round(min * 9), Math.round(max * 9)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(long n) {
        return String.format(java.util.Locale.US, "%,d", n);
    }

    /** Colors each row (gold label, aqua value) and space-pads so all values line up to the right edge. */
    private static java.util.List<String> rightAlign(java.util.List<String[]> rows) {
        net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;
        int maxCombined = 0;
        for (String[] r : rows) {
            maxCombined = Math.max(maxCombined, fr.getStringWidth(r[0]) + fr.getStringWidth(r[1]));
        }
        int gapPx = 12;
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String[] r : rows) {
            int padPx = (maxCombined - fr.getStringWidth(r[0]) - fr.getStringWidth(r[1])) + gapPx;
            int spaces = Math.max(1, Math.round(padPx / 4.0f));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < spaces; i++) {
                sb.append(' ');
            }
            out.add(EnumChatFormatting.GOLD + r[0] + sb + EnumChatFormatting.AQUA + r[1]);
        }
        return out;
    }
}
