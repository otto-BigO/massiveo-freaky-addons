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

        CelleConfig cfg = MassiveOsFreakyAddons.config;

        // Your own staff list, matched on the whole username. It used to also
        // accept the name appearing anywhere inside a display name, which turned
        // one short entry into a match on half the server.
        if (cfg != null && cfg.staffList != null) {
            for (String staff : cfg.staffList) {
                if (staff != null && !staff.isEmpty() && name.equals(staff.toLowerCase())) {
                    return true;
                }
            }
        }

        // Everything below is guesswork from words like "vagt". It is off by
        // default, because a plain substring test on a username marks anyone
        // called something like Vagtel or Nomad as staff, and "mod" and "admin"
        // are worse still. The roster holds every real account, so the guessing
        // has nothing left to add.
        if (cfg == null || !Boolean.TRUE.equals(cfg.vagtGuessByRank)) {
            return false;
        }

        String displayName = p.getDisplayName() != null
                ? p.getDisplayName().getUnformattedText().toLowerCase() : "";
        if (containsAny(name, NOT_A_GUARD) || containsAny(displayName, NOT_A_GUARD)) {
            return false;
        }

        // Only ever a rank tag the server assigned, never the username itself.
        // A rank reads as "[Vagt] Name", so the word has to sit inside brackets
        // rather than merely appear somewhere in the text.
        if (hasRankWord(displayName)) {
            return true;
        }
        String tag = BandeEsp.bandeTag(p);
        if (tag != null) {
            String tLower = tag.toLowerCase();
            if (!containsAny(tLower, NOT_A_GUARD) && hasRankWord(tLower)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the text carries a staff rank in brackets, for example "[Vagt]".
     *
     * Requiring the brackets is the whole point: it is what separates a rank the
     * server handed out from the same letters happening to appear in somebody's
     * chosen username.
     */
    static boolean hasRankWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String w : GUARD_WORDS) {
            int from = 0;
            while (true) {
                int i = text.indexOf(w, from);
                if (i < 0) {
                    break;
                }
                int open = -1;
                for (int j = i - 1; j >= 0 && i - j <= 3; j--) {
                    char c = text.charAt(j);
                    if (c == '[' || c == '(' || c == '<' || c == '\u00ab') { open = j; break; }
                    if (c != ' ') { break; }
                }
                if (open >= 0) {
                    for (int j = i + w.length(); j < text.length() && j - (i + w.length()) <= 6; j++) {
                        char c = text.charAt(j);
                        if (c == ']' || c == ')' || c == '>' || c == '\u00bb') {
                            return true;
                        }
                        if (c == ' ') { continue; }
                        if (!Character.isLetter(c)) { break; }
                    }
                }
                from = i + 1;
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
