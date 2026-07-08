package com.otto.cellescanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Brand + addon registry for Massiveo's Freaky Addons.
 *
 * The mod is a hub: the main menu (GuiAddonsHub) lists every addon registered
 * here, and each addon opens its own screen. Celle Scanner is the first addon;
 * adding another is just one {@link #register} call in CelleScannerMod.init()
 * plus that addon's own GuiScreen.
 *
 * The internal mod id stays "cellescanner" for config/save compatibility - this
 * is purely the user-facing branding and the list the hub is built from.
 */
public final class MassiveoAddons {

    public static final String BRAND = "Massiveo's Freaky Addons";

    /** One tile in the hub: a name, blurb, category, live on/off state, and how to open it. */
    public interface Addon {
        String name();

        String description();

        /** The genre this addon is grouped under in the hub (e.g. "Celler", "PvP", "World"). */
        String category();

        /** Whether the addon is currently enabled - shown as [Til]/[Fra] on its hub tile. */
        boolean isActive();

        void open();

        void toggle();
    }

    private static final List<Addon> ADDONS = new ArrayList<Addon>();

    private MassiveoAddons() {
    }

    public static void register(Addon addon) {
        ADDONS.add(addon);
    }

    /** Distinct category names in the order they were first registered. */
    public static List<String> categories() {
        List<String> cats = new ArrayList<String>();
        for (Addon a : ADDONS) {
            if (!cats.contains(a.category())) {
                cats.add(a.category());
            }
        }
        return cats;
    }

    public static List<Addon> addonsIn(String category) {
        List<Addon> list = new ArrayList<Addon>();
        for (Addon a : ADDONS) {
            if (a.category().equals(category)) {
                list.add(a);
            }
        }
        return list;
    }
}
