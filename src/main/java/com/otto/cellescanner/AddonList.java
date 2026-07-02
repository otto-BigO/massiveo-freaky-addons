package com.otto.cellescanner;

/**
 * Lazily registers the hub's addons. Kept out of the mod's init() so that this
 * class and its (anonymous) Addon classes are not loaded during startup - they
 * only load the first time the hub is opened. This keeps our startup footprint
 * minimal (relevant under LabyMod, whose render-thread watchdog is sensitive to
 * total startup class loading).
 */
public final class AddonList {

    private static boolean registered = false;

    private AddonList() {
    }

    /** Registers every addon exactly once. Safe to call repeatedly (e.g. each hub open). */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;

        final CelleConfig config = CelleScannerMod.config;

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Celle Scanner";
            }

            public String description() {
                return "Scan celle-skilte, HUD, ESP og Discord-rapportering";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return config.enabled;
            }

            public void open() {
                CelleActions.openMenu();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Mine Celler";
            }

            public String description() {
                return "Se og find dine egne, delte og inviterede celler";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return config.mineCellerEspEnabled;
            }

            public void open() {
                CelleActions.openMineCeller();
            }
        });

        // Gange addon shelved for now - the code (GuiGange, GangInfo,
        // GangRanges) is kept so it can be picked back up later. To re-enable,
        // restore this registration, the GangInfo event registration and the
        // GangRanges.init call in CelleScannerMod.
        // MassiveoAddons.register(new MassiveoAddons.Addon() {
        //     public String name() { return "Gange"; }
        //     public String description() { return "Alle gange og deres celle-timere (højreklik skilte)"; }
        //     public String category() { return "Celler"; }
        //     public boolean isActive() { return config.gangAutoQuery; }
        //     public void open() { CelleActions.openGange(); }
        // });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Bande ESP";
            }

            public String description() {
                return "Grøn kasse gennem vægge på spillere i din bande";
            }

            public String category() {
                return "PvP";
            }

            public boolean isActive() {
                return config.bandeEspEnabled;
            }

            public void open() {
                CelleActions.openBande();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Chest Alarm";
            }

            public String description() {
                return "Notifikation + lyd når en chest bliver åbnet i chatten";
            }

            public String category() {
                return "PvP";
            }

            public boolean isActive() {
                return config.chestAlarmEnabled;
            }

            public void open() {
                CelleActions.openChestAlarm();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Anti-AFK";
            }

            public String description() {
                return "Undgå at blive kicket for inaktivitet";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return config.antiAfkEnabled;
            }

            public void open() {
                CelleActions.openAntiAfk();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Rustnings-skins";
            }

            public String description() {
                return "Se forskel på Protection 1-4 jern/diamant rustning";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return config.armorSkinsEnabled;
            }

            public void open() {
                CelleActions.openArmorSkins();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Rustnings-HUD";
            }

            public String description() {
                return "Vis rustnings holdbarhed + advarsel når den er lav";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return config.armorHudEnabled;
            }

            public void open() {
                CelleActions.openArmorHud();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Item Værdi";
            }

            public String description() {
                return "Vis en vares værdi (DB/diamanter) i tooltippet";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return config.itemValueEnabled;
            }

            public void open() {
                CelleActions.openItemValues();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Prisguide";
            }

            public String description() {
                return "Bladr i FreakyVilles prisguide (hentet live)";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return true;
            }

            public void open() {
                CelleActions.openPriceGuide();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Opdatering";
            }

            public String description() {
                return "Auto-opdatering fra GitHub - tjek og hent ny version";
            }

            public String category() {
                return "World";
            }

            public boolean isActive() {
                return config.autoUpdateEnabled;
            }

            public void open() {
                CelleActions.openUpdate();
            }
        });
    }
}
