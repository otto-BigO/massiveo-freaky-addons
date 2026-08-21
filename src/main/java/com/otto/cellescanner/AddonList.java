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

    /**
     * Switches every addon off, once, on a fresh install.
     *
     * The registry is the source of truth rather than a list of config fields:
     * most of those booleans are how an addon behaves rather than whether it is
     * on, and turning off the likes of "show seconds" or "use iron pickaxes"
     * would be nonsense. Anything that reports itself active gets toggled.
     */
    public static void applyFirstRunDefaults() {
        CelleConfig config = MassiveOsFreakyAddons.config;
        if (config == null || !config.firstRun) {
            return;
        }
        ensureRegistered();
        int off = 0;
        for (MassiveoAddons.Addon a : MassiveoAddons.all()) {
            try {
                if (a.isActive()) {
                    a.toggle();
                    off++;
                }
            } catch (Throwable ignored) {
                // One addon that cannot report or change its own state is not
                // a reason to leave the rest half-configured.
            }
        }
        // Two stay on. Reporting is what feeds the shared celle picture and the
        // user list, and a client that never reports is invisible to everyone
        // else; auto-update is how a new install stops being an old one. Both
        // are set after the sweep rather than skipped during it, so this does
        // not depend on matching an addon by name.
        config.botReportEnabled = true;
        config.autoUpdateEnabled = true;

        config.firstRun = false;
        config.save();
        System.out.println("[CelleScanner] First run: switched " + off
                + " addons off, kept Celle Bot and Opdatering on.");
    }

    /** Registers every addon exactly once. Safe to call repeatedly (e.g. each hub open). */
    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        registered = true;

        final CelleConfig config = MassiveOsFreakyAddons.config;

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Freecam";
            }

            public String description() {
                return "Flyv rundt med kameraet mens du selv står stille";
            }

            public String category() {
                return "Quality of life";
            }

            /* This one reports whether the camera is actually out, not whether
               a config flag is set. The hub is the way to start it: the key
               binding ships unbound, so making this a flag would have left the
               feature with no way to turn it on at all. */
            public boolean isActive() {
                return Freecam.INSTANCE.isActive();
            }

            public boolean hasSettings() {
                return false;
            }

            public void open() {
            }

            public void toggle() {
                Freecam.INSTANCE.toggle();
            }
        });

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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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
        // GangRanges.init call in MassiveOsFreakyAddons.
        // MassiveoAddons.register(new MassiveoAddons.Addon() {
        //     public String name() { return "Gange"; }
        //     public String description() { return "Alle gange og deres celle-timere (højreklik skilte)"; }
        //     public String category() { return "Celler"; }
        //     public boolean isActive() { return config.gangAutoQuery; }
        //     public void open() { CelleActions.openGange(); }
        // });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "ESP";
            }

            public String description() {
                return "Se spillere gennem vægge, farvet efter bande, vagt eller andre";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.espAddonEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openEsp();
            }

            public void toggle() {
                config.espAddonEnabled = !config.espAddonEnabled; config.save();
            }
        });

        /* Shelved for now - Venne Telefon stays in repo for later use.
        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Venne Telefon";
            }

            public String description() {
                return "iPhone-stil mobil til at styre venner, iMessage whispers og blå ESP";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.friendEspEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openPhoneGui();
            }

            public void toggle() {
                config.friendEspEnabled = !config.friendEspEnabled;
                config.save();
            }
        });
        */

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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return true;
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
                return "Celle Bot";
            }

            public String description() {
                return "Deler dine scannede celler med de andre (til/fra)";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return config.botReportEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openBotScreen();
            }

            public void toggle() {
                config.botReportEnabled = !config.botReportEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Celle Buyer";
            }

            public String description() {
                return "Køber cellen i det sekund den bliver ledig (egen risiko)";
            }

            public String category() {
                return "Celler";
            }

            public boolean isActive() {
                return config.celleBuyerEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openCelleBuyer();
            }

            public void toggle() {
                config.celleBuyerEnabled = !config.celleBuyerEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Last hit";
            }

            public String description() {
                return "Tager sidste hit p\u00e5 vagter (" + VagtRoster.size() + " p\u00e5 listen - egen risiko)";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.vkStealerEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openVkStealer();
            }

            public void toggle() {
                config.vkStealerEnabled = !config.vkStealerEnabled; config.save();
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

            public boolean hasSettings() {
                return false;
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

            public boolean hasSettings() {
                return false;
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

            public boolean hasSettings() {
                return false;
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

        /* Shelved 2026-08-21 - the server will not accept it.

           FreakyVille rejects armour equips made by clicking in the inventory,
           and that is not a bug in this code: shift-clicking a piece by hand
           does the same thing, on for a moment and then the server sends the
           inventory back as it was. Right-click-to-equip was tried next, as a
           use-item packet rather than a window click, and that is refused too.

           Kept in the repo. If a route that works is ever found, this is ready
           to go back in.
        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto Armour";
            }

            public String description() {
                return "Tryk R (hotkey) for at tage rustning på/af hurtigt";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.autoArmorEnabled;
            }

            public boolean hasSettings() {
                return false;
            }

            public void open() {
                AutoArmor.toggleArmor();
            }

            public void toggle() {
                config.autoArmorEnabled = !config.autoArmorEnabled;
                config.save();
            }
        });
        */

        // Mod-brugere addon shelved for now - good idea, saved for later. The
        // code (GuiModIcon, ModUserIcon) is kept; to re-enable, restore this
        // registration and the ModUserIcon event registration in
        // MassiveOsFreakyAddons.enableAddons().
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
                return "Item Log";
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

            public boolean hasSettings() {
                return true;
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
                return "Anti AFK";
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

            public boolean hasSettings() {
                return true;
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
                return "Armour Skins";
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

            public boolean hasSettings() {
                return true;
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
                return "Armour HUD";
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

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openArmorHud();
            }

            public void toggle() {
                config.armorHudEnabled = !config.armorHudEnabled; config.save();
            }
        });

        /* Shelved for now - Item Værdi stays in repo for later use.
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

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openItemValues();
            }

            public void toggle() {
                config.itemValueEnabled = !config.itemValueEnabled; config.save();
            }
        });
        */

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

            public boolean showInActive() {
                return false;   // a page you open, not something running
            }

            public boolean hasSettings() {
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

            public boolean showInActive() {
                return false;   // a page you open, not something running
            }

            public boolean hasSettings() {
                return true;
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

            public boolean hasSettings() {
                return false;
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

            public boolean hasSettings() {
                return false;
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
                return "Kiste Organisering";
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

            public boolean hasSettings() {
                return false;
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
                return "Iron door lyde";
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

            public boolean hasSettings() {
                return false;
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
                return "Spiller Logger";
            }

            public String description() {
                return "Viser hvor andre spillere loggede ud henne i 3D";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.playerLoggerEnabled;
            }

            public boolean hasSettings() {
                return false;
            }

            public void open() {
                config.playerLoggerEnabled = !config.playerLoggerEnabled;
                config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                        new GuiAddonsHub("Tracking"));
            }

            public void toggle() {
                config.playerLoggerEnabled = !config.playerLoggerEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Farm Bot";
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

            public boolean hasSettings() {
                return false;
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
                return "Navne ESP";
            }

            public String description() {
                return "Navneskilte gennem vægge, farvet efter bande, vagt eller andre";
            }

            public String category() {
                return "Tracking";
            }

            public boolean isActive() {
                return config.playerEspEnabled;
            }

            public boolean hasSettings() {
                return true;
            }

            public void open() {
                CelleActions.openNameEsp();
            }

            public void toggle() {
                config.playerEspEnabled = !config.playerEspEnabled; config.save();
            }
        });

        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Auto Følg";
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

            public boolean hasSettings() {
                return true;
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

        /* Shelved 2026-08-21 - the idea is worth keeping, the numbers were not
           trustworthy enough to ship. Left in the repo to pick up later.
        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Mine Tracker";
            }

            public String description() {
                return "Hold øje med Iron Ore/min og estimerede Diamantblokke (DBs)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.mineTrackerEnabled;
            }

            public boolean hasSettings() {
                return false;
            }

            public void open() {
                config.mineTrackerEnabled = !config.mineTrackerEnabled; config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.mineTrackerEnabled = !config.mineTrackerEnabled; config.save();
            }
        });
        */

        /* Shelved 2026-08-21 - does not actually drop anything. Kept for a
           rewrite rather than deleted.
        MassiveoAddons.register(new MassiveoAddons.Addon() {
            public String name() {
                return "Skralde-Filter";
            }

            public String description() {
                return "Smid automatisk skrald ud (Cobblestone, Dirt, Træ/Sten redskaber)";
            }

            public String category() {
                return "Automation";
            }

            public boolean isActive() {
                return config.autoTrashEnabled;
            }

            public boolean hasSettings() {
                return false;
            }

            public void open() {
                config.autoTrashEnabled = !config.autoTrashEnabled; config.save();
                net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new GuiAddonsHub("Automation"));
            }

            public void toggle() {
                config.autoTrashEnabled = !config.autoTrashEnabled; config.save();
            }
        });
        */
    }
}
