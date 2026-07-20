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

            public void toggle() {
                config.enabled = !config.enabled; config.save();
            }
        });

        // Celle Finder is its own tile in the Celler theme now (used to be a
        // button buried inside the Celle Scanner menu) so it's quicker to reach.
        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Celle Finder";
            }

            public String description() {
                return "Søg efter en bestemt celle og få vist vej til den";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return CelleFinder.hasTarget();
            }

            public void open() {
                CelleActions.openFinderScreen();
            }

            public void toggle() {
                CelleActions.clearFinderTarget();
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

            public void toggle() {
                config.mineCellerEspEnabled = !config.mineCellerEspEnabled; config.save();
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
                return "Tracking";
            }

            public boolean isActive() {
                return config.bandeEspEnabled;
            }

            public void open() {
                CelleActions.openBande();
            }

            public void toggle() {
                config.bandeEspEnabled = !config.bandeEspEnabled; config.save();
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
                return "Tracking";
            }

            public boolean isActive() {
                return config.chestAlarmEnabled;
            }

            public void open() {
                CelleActions.openChestAlarm();
            }

            public void toggle() {
                config.chestAlarmEnabled = !config.chestAlarmEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Spiller Info";
            }

            public String description() {
                return "Shift + højreklik en spiller for at se rustning + info";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.playerInfoEnabled;
            }

            public void open() {
                CelleActions.openPlayerInfoMenu();
            }

            public void toggle() {
                config.playerInfoEnabled = !config.playerInfoEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Troll Lyde";
            }

            public String description() {
                return "Fjollede lyde på død, kill, hop, AFK m.m. (kun du hører dem)";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.trollEnabled;
            }

            public void open() {
                CelleActions.openTroll();
            }

            public void toggle() {
                config.trollEnabled = !config.trollEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "PvP Mine";
            }

            public String description() {
                return "Drop-timer + alarm når nogen er i PvP minen";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.pvpMineEnabled;
            }

            public void open() {
                CelleActions.openPvpMine();
            }

            public void toggle() {
                config.pvpMineEnabled = !config.pvpMineEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto Mine";
            }

            public String description() {
                return "Auto-miner mine-området (automatisering - egen risiko)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.autoMineEnabled;
            }

            public void open() {
                CelleActions.openAutoMine();
            }

            public void toggle() {
                config.autoMineEnabled = !config.autoMineEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto Fish";
            }

            public String description() {
                return "Auto-fisker i fiske-zoner (automatisering - egen risiko)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.autoFishEnabled;
            }

            public void open() {
                config.autoFishEnabled = !config.autoFishEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.autoFishEnabled = !config.autoFishEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto Crate";
            }

            public String description() {
                return "Åbner kasser automatisk (automatisering - egen risiko)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.autoCrateEnabled;
            }

            public void open() {
                config.autoCrateEnabled = !config.autoCrateEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.autoCrateEnabled = !config.autoCrateEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Fast Mine";
            }

            public String description() {
                return "Miner i takt med musen når du graver (automatisering - egen risiko)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.fastMineEnabled;
            }

            public void open() {
                config.fastMineEnabled = !config.fastMineEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.fastMineEnabled = !config.fastMineEnabled;
                config.save();
            }
        });

        // Mod-brugere addon shelved for now - good idea, saved for later. The
        // code (GuiModIcon, ModUserIcon) is kept; to re-enable, restore this
        // registration and the ModUserIcon event registration in
        // CelleScannerMod.enableAddons().
        // MassiveoAddons.register(new MassiveoAddons.Addon() {
        //     public String name() { return "Mod-brugere"; }
        //     public String description() { return "Lilla ikon foran andre mod-brugeres navn (test)"; }
        //     public String category() { return "Tracking"; }
        //     public boolean isActive() { return config.modIconEnabled != null && config.modIconEnabled; }
        //     public void open() { CelleActions.openModIcon(); }
        //     public void toggle() { config.modIconEnabled = (config.modIconEnabled == null ? true : !config.modIconEnabled); config.save(); }
        // });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Item-log";
            }

            public String description() {
                return "Lille \"+N vare\" notifikation nederst til højre";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.itemPickupEnabled;
            }

            public void open() {
                CelleActions.openItemLog();
            }

            public void toggle() {
                config.itemPickupEnabled = !config.itemPickupEnabled; config.save();
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
                return "Automation";
            }

            public boolean isActive() {
                return config.antiAfkEnabled;
            }

            public void open() {
                CelleActions.openAntiAfk();
            }

            public void toggle() {
                config.antiAfkEnabled = !config.antiAfkEnabled; config.save();
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
                return "Quality of life";
            }

            public boolean isActive() {
                return config.armorSkinsEnabled;
            }

            public void open() {
                CelleActions.openArmorSkins();
            }

            public void toggle() {
                config.armorSkinsEnabled = !config.armorSkinsEnabled; config.save();
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
                return "Quality of life";
            }

            public boolean isActive() {
                return config.armorHudEnabled;
            }

            public void open() {
                CelleActions.openArmorHud();
            }

            public void toggle() {
                config.armorHudEnabled = !config.armorHudEnabled; config.save();
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
                return "Quality of life";
            }

            public boolean isActive() {
                return config.itemValueEnabled;
            }

            public void open() {
                CelleActions.openItemValues();
            }

            public void toggle() {
                config.itemValueEnabled = !config.itemValueEnabled; config.save();
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
                return "Quality of life";
            }

            public boolean isActive() {
                return true;
            }

            public void open() {
                CelleActions.openPriceGuide();
            }

            public void toggle() {
                
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
                return "Quality of life";
            }

            public boolean isActive() {
                return config.autoUpdateEnabled;
            }

            public void open() {
                CelleActions.openUpdate();
            }

            public void toggle() {
                config.autoUpdateEnabled = !config.autoUpdateEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Flip Case";
            }

            public String description() {
                return "CS:GO-stil case-åbning animation når du flipper - erstatter flip-kisten";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.flipCaseEnabled;
            }

            public void open() {
                config.flipCaseEnabled = !config.flipCaseEnabled;
                config.save();
                // Re-open the hub at the same category so the [Til]/[Fra] label refreshes.
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Quality of life"));
            }

            public void toggle() {
                config.flipCaseEnabled = !config.flipCaseEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Celle Alarm";
            }

            public String description() {
                return "Lyd- og skærm-alarmer når en fulgt celle udløber (2m, 1m, 30s og countdown)";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return config.celleExpiryAlertsEnabled;
            }

            public void open() {
                config.celleExpiryAlertsEnabled = !config.celleExpiryAlertsEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Celler"));
            }

            public void toggle() {
                config.celleExpiryAlertsEnabled = !config.celleExpiryAlertsEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Kiste-organisering";
            }

            public String description() {
                return "Venstreklik en kiste for at tilføje et flydende 3D-ikon";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.chestOrganizerEnabled;
            }

            public void open() {
                config.chestOrganizerEnabled = !config.chestOrganizerEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Quality of life"));
            }

            public void toggle() {
                config.chestOrganizerEnabled = !config.chestOrganizerEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Jernlåge-lyde";
            }

            public String description() {
                return "Afspiller dør-lyde når jernlåger åbnes og lukkes på serveren";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.ironDoorSoundsEnabled;
            }

            public void open() {
                config.ironDoorSoundsEnabled = !config.ironDoorSoundsEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Quality of life"));
            }

            public void toggle() {
                config.ironDoorSoundsEnabled = !config.ironDoorSoundsEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Spiller-logger";
            }

            public String description() {
                return "Viser hvor andre spillere loggede ud henne i 3D";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.playerLoggerEnabled;
            }

            public void open() {
                config.playerLoggerEnabled = !config.playerLoggerEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Quality of life"));
            }

            public void toggle() {
                config.playerLoggerEnabled = !config.playerLoggerEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Farm-bot";
            }

            public String description() {
                return "Høster og genplanter automatisk modne afgrøder";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.farmBotEnabled;
            }

            public void open() {
                config.farmBotEnabled = !config.farmBotEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.farmBotEnabled = !config.farmBotEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Spiller ESP";
            }

            public String description() {
                return "Se spillere og deres navneskilte gennem vægge i 3D";
            }

            public String category() {
                return "Quality of life";
            }

            public boolean isActive() {
                return config.playerEspEnabled;
            }

            public void open() {
                config.playerEspEnabled = !config.playerEspEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Quality of life"));
            }

            public void toggle() {
                config.playerEspEnabled = !config.playerEspEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto-Følg";
            }

            public String description() {
                return "Gå og løb bag en anden spiller automatisk (Auto Follow)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return AutoFollow.isActive();
            }

            public void open() {
                if (AutoFollow.isActive()) {
                    AutoFollow.stop();
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                            new GuiAddonsHub("Automation"));
                } else {
                    net.minecraft.client.Minecraft.getMinecraft().thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText("§eBrug /følg <navn> for at starte auto-follow."));
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(null);
                }
            }

            public void toggle() {
                if (AutoFollow.isActive()) {
                    AutoFollow.stop();
                }
            }
        });
    }
}
