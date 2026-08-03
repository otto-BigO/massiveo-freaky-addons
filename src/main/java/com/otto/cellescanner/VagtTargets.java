package com.otto.cellescanner;

import net.minecraft.entity.player.EntityPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared identification of vagter and officers, and a best effort read of how much
 * health one has left.
 *
 * The detection was previously private to BandeEsp. Anything that needs to know what
 * counts as a vagt goes through here instead, so the ESP and the VK Stealer cannot
 * end up disagreeing about which entity is a guard.
 */
public final class VagtTargets {

    /** Name fragments that mark holograms and shop stands rather than a real guard. */
    private static final String[] NOT_A_GUARD = {"kills", "top", "shop", "stats", "npc"};

    /** Fallback keywords when the configured staff list does not match. */
    private static final String[] GUARD_WORDS = {"vagt", "guard", "officer", "mod", "admin"};

    /** Matches "340/500" style health in a floating tag. */
    private static final Pattern HEALTH_FRACTION = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)");
    /** Matches a bare number next to a heart glyph, for example "340 ❤". */
    private static final Pattern HEALTH_HEART = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*[❤♥]|[❤♥]\\s*(\\d+(?:[.,]\\d+)?)");

    private VagtTargets() {
    }

    /** True when this player entity is a vagt or officer. */
    public static boolean isVagt(EntityPlayer p) {
        if (p == null) {
            return false;
        }
        String name = p.getName() != null ? p.getName().toLowerCase() : "";

        // The roster is the authority. These are real accounts, so an exact
        // username match beats every guess made from tags or display names.
        if (VagtRoster.contains(name)) {
            return true;
        }

        if (containsAny(name, NOT_A_GUARD)) {
            return false;
        }
        String displayName = p.getDisplayName() != null
                ? p.getDisplayName().getUnformattedText().toLowerCase() : "";
        if (displayName.contains("kills") || displayName.contains("top") || displayName.contains("shop")) {
            return false;
        }

        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg != null && cfg.staffList != null) {
            for (String staff : cfg.staffList) {
                if (staff != null && !staff.isEmpty()) {
                    String sLower = staff.toLowerCase();
                    if (name.equals(sLower) || displayName.contains(sLower)) {
                        return true;
                    }
                }
            }
        }

        if (containsAny(name, GUARD_WORDS) || containsAny(displayName, GUARD_WORDS)) {
            return true;
        }

        String tag = BandeEsp.bandeTag(p);
        if (tag != null) {
            String tLower = tag.toLowerCase();
            if (containsAny(tLower, GUARD_WORDS) && !tLower.contains("kills") && !tLower.contains("top")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Health left on a guard as a fraction from 0 to 1, or -1 when it cannot be read.
     *
     * Two sources are tried. The floating tag above the guard is preferred, because a
     * server that runs its own health system usually shows the real value there while
     * leaving the entity's synced health untouched at full. The entity's own health is
     * the fallback.
     */
    public static float healthFraction(EntityPlayer p) {
        if (p == null) {
            return -1f;
        }
        float fromTag = tagHealthFraction(BandeEsp.bandeTag(p));
        if (fromTag >= 0f) {
            return fromTag;
        }
        float max = p.getMaxHealth();
        if (max > 0f) {
            float cur = p.getHealth();
            if (cur >= 0f && cur <= max) {
                return cur / max;
            }
        }
        return -1f;
    }

    /** Parses "340/500" or "340 ❤" out of a floating tag. Returns -1 when absent. */
    static float tagHealthFraction(String tag) {
        if (tag == null || tag.isEmpty()) {
            return -1f;
        }
        Matcher frac = HEALTH_FRACTION.matcher(tag);
        if (frac.find()) {
            float cur = parse(frac.group(1));
            float max = parse(frac.group(2));
            if (max > 0f && cur >= 0f) {
                float f = cur / max;
                return f > 1f ? 1f : f;
            }
        }
        Matcher heart = HEALTH_HEART.matcher(tag);
        if (heart.find()) {
            String g = heart.group(1) != null ? heart.group(1) : heart.group(2);
            float cur = parse(g);
            // A bare number has no maximum to compare against, so it cannot become a
            // fraction. Callers that only need "is it nearly dead" use rawTagHealth.
            if (cur >= 0f) {
                return -1f;
            }
        }
        return -1f;
    }

    /** The absolute health number in a floating tag, or -1 when there is none. */
    public static float rawTagHealth(EntityPlayer p) {
        String tag = BandeEsp.bandeTag(p);
        if (tag == null || tag.isEmpty()) {
            return -1f;
        }
        Matcher frac = HEALTH_FRACTION.matcher(tag);
        if (frac.find()) {
            return parse(frac.group(1));
        }
        Matcher heart = HEALTH_HEART.matcher(tag);
        if (heart.find()) {
            String g = heart.group(1) != null ? heart.group(1) : heart.group(2);
            return parse(g);
        }
        return -1f;
    }

    private static float parse(String s) {
        if (s == null) {
            return -1f;
        }
        try {
            return Float.parseFloat(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
