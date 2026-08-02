package com.otto.cellescanner;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out the Protection level of an armor piece.
 *
 * FreakyVille does not always put a real Protection enchantment on the item.
 * Guard gear and shop-bought pieces often carry the level as plain lore text
 * instead, for example "Protection IV, Købt af: Muni_Jr" on one line. Asking
 * EnchantmentHelper alone returns 0 for those, so the skin never applied.
 * The real enchantment is checked first, then the display name and lore.
 */
public final class ArmorProtection {

    /** Protection is enchantment id 0 in 1.8. */
    private static final int PROTECTION_ID = 0;

    /** Matches "Protection IV", "protection 4", "Beskyttelse III" and the like. */
    private static final Pattern PROT = Pattern.compile(
            "(?i)(?:protection|beskyttelse)\\s*:?\\s*([ivx]+|\\d+)");

    private ArmorProtection() {
    }

    /** The piece's Protection level, from real NBT if present, otherwise from its text. */
    public static int level(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        int real = EnchantmentHelper.getEnchantmentLevel(PROTECTION_ID, stack);
        if (real > 0) {
            return real;
        }
        return fromText(stack);
    }

    /** True when the piece carries any enchantment, or any Protection level in its text. */
    public static boolean isEnchanted(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.isItemEnchanted()) {
            return true;
        }
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("ench")) {
            return true;
        }
        if (EnchantmentHelper.getEnchantments(stack).size() > 0) {
            return true;
        }
        return fromText(stack) > 0;
    }

    /** Scans the display name and lore lines for a Protection level. */
    private static int fromText(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return 0;
        }
        NBTTagCompound display = stack.getTagCompound().getCompoundTag("display");
        if (display == null) {
            return 0;
        }
        int best = parse(display.getString("Name"));
        NBTTagList lore = display.getTagList("Lore", 8);
        for (int i = 0; i < lore.tagCount(); i++) {
            int n = parse(lore.getStringTagAt(i));
            if (n > best) {
                best = n;
            }
        }
        return best;
    }

    /** Pulls the level out of one line, tolerating colour codes and roman numerals. */
    static int parse(String line) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        String clean = line.replaceAll("(?i)§[0-9a-fk-or]", "");
        Matcher m = PROT.matcher(clean);
        if (!m.find()) {
            return 0;
        }
        String value = m.group(1);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException notADigit) {
            return roman(value);
        }
    }

    /** Roman numerals, enough for the levels that actually appear on armor. */
    private static int roman(String s) {
        String v = s.toUpperCase();
        int total = 0;
        int prev = 0;
        for (int i = v.length() - 1; i >= 0; i--) {
            int d;
            switch (v.charAt(i)) {
                case 'I': d = 1; break;
                case 'V': d = 5; break;
                case 'X': d = 10; break;
                default: return 0;
            }
            if (d < prev) {
                total -= d;
            } else {
                total += d;
                prev = d;
            }
        }
        return total;
    }
}
