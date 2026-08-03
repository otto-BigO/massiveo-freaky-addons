package com.otto.cellescanner;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

/**
 * /celler                    -> opens the button GUI (GuiCelleMenu)
 * /celler menu | gui         -> same, explicit
 * /celler settings           -> opens the display-options screen
 * /celler help               -> lists every command in chat
 * /celler toggle             -> enable/disable the scanner
 * /celler notify             -> enable/disable notifications
 * /celler reload             -> reload config.json from disk
 * /celler debug              -> dump the current cache to chat
 * /celler clear              -> wipe the cache
 * /celler move                -> open the HUD-drag screen
 * /celler esp                 -> enable/disable the through-wall outline
 * /celler showseconds         -> toggle seconds in the countdown
 * /celler showowner           -> toggle owner name on HUD lines
 * /celler showstatus          -> toggle the (til salg)/(solgt) tag
 * /celler showdistance        -> toggle distance-in-meters on HUD lines
 * /celler esplabels            -> toggle the floating celle-id labels on ESP
 * /celler espdistance <blocks> -> set the max ESP render distance
 * /celler bot                  -> opens the bot-connection screen (use this
 *                                  for the reports webhook url - it has a
 *                                  text field with paste support, unlike
 *                                  chat which truncates long strings)
 * /celler bot <url>            -> set the reports webhook url via chat -
 *                                  fine as long as it fits Minecraft's chat
 *                                  input length cap
 * /celler bot off              -> disable (keeps the saved url)
 * /celler bot clear            -> disable and forget it
 * /celler bot test             -> send a test report
 * /celler special              -> opens the special-celle screen
 * /celler special add <id>     -> flag a celle as special (always on the
 *                                  dashboard, plus its own alert ping)
 * /celler special remove <id>  -> unflag it
 * /celler special clear        -> unflag everything
 * /celler special list         -> list the current special celle ids
 * /celler find                 -> opens the Celle Finder screen (type an id,
 *                                  highlights it via ESP + a HUD compass line
 *                                  using its last-known scanned position -
 *                                  works even if it's not currently loaded)
 * /celler find <id>            -> start finding that id directly via chat
 * /celler find stop            -> stop the finder
 * /celler min <hours>        -> set minHours directly
 * /celler max <hours>        -> set maxHours directly
 *
 * Every branch below just calls into CelleActions, the same helper class
 * the GUI buttons use - so typing a command and clicking the matching
 * button always do exactly the same thing and produce the same chat output.
 */
public class CommandCeller extends CommandBase {

    @Override
    public String getCommandName() {
        return "celler";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/celler [menu|settings|toggle|notify|reload|debug|clear|move|esp|"
                + "showseconds|showowner|showstatus|showdistance|esplabels|espdistance <blocks>|"
                + "bot <url|off|clear|test>|special <add|remove|clear|list> [id]|"
                + "find [<id>|stop]|min <t>|max <t>|help]";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            CelleActions.openMenu();
            return;
        }

        String sub = args[0].toLowerCase();

        if ("menu".equals(sub) || "gui".equals(sub)) {
            CelleActions.openMenu();
        } else if ("map".equals(sub)) {
            CelleActions.auditMap();
        } else if ("settings".equals(sub)) {
            CelleActions.openSettings();
        } else if ("help".equals(sub)) {
            printHelp();
        } else if ("toggle".equals(sub)) {
            CelleActions.toggleEnabled();
        } else if ("notify".equals(sub)) {
            CelleActions.toggleNotify();
        } else if ("reload".equals(sub)) {
            CelleActions.reloadConfig();
        } else if ("debug".equals(sub)) {
            CelleActions.debugDump();
        } else if ("signdump".equals(sub) || "sign".equals(sub)) {
            CelleActions.dumpNearestSign();
        } else if ("bandedump".equals(sub)) {
            if (args.length < 2) {
                CelleActions.message("Brug: /celler bandedump <spiller>");
            } else {
                CelleActions.dumpScoreboard(args[1]);
            }
        } else if ("clear".equals(sub)) {
            CelleActions.clearCache();
        } else if ("move".equals(sub)) {
            CelleActions.openMover();
        } else if ("esp".equals(sub)) {
            CelleActions.toggleEsp();
        } else if ("showseconds".equals(sub)) {
            CelleActions.toggleShowSeconds();
        } else if ("showowner".equals(sub)) {
            CelleActions.toggleShowOwner();
        } else if ("showstatus".equals(sub)) {
            CelleActions.toggleShowStatusTag();
        } else if ("showdistance".equals(sub)) {
            CelleActions.toggleShowDistance();
        } else if ("esplabels".equals(sub)) {
            CelleActions.toggleEspLabels();
        } else if ("espdistance".equals(sub)) {
            setEspDistance(args);
        } else if ("timing".equals(sub)) {
            for (String line : CelleTimingLog.summary()) {
                CelleActions.message(line);
            }
            CelleActions.message("Log: " + CelleTimingLog.path());
        } else if ("bot".equals(sub)) {
            handleBot(args);
        } else if ("buyer".equals(sub) || "koeb".equals(sub)) {
            handleBuyer(args);
        } else if ("special".equals(sub)) {
            handleSpecial(args);
        } else if ("find".equals(sub)) {
            handleFind(args);
        } else if ("min".equals(sub)) {
            setHours(args, true);
        } else if ("max".equals(sub)) {
            setHours(args, false);
        } else {
            CelleActions.message("Ukendt kommando. " + getCommandUsage(sender));
        }
    }

    private void setEspDistance(String[] args) {
        if (args.length < 2) {
            CelleActions.message("Brug: /celler espdistance <blokke>");
            return;
        }
        try {
            double value = Double.parseDouble(args[1]);
            CelleActions.setEspMaxDistance(value);
        } catch (NumberFormatException e) {
            CelleActions.message("\"" + args[1] + "\" er ikke et gyldigt tal.");
        }
    }

    private void handleBot(String[] args) {
        if (args.length < 2) {
            CelleActions.openBotScreen();
            return;
        }
        String arg = args[1];
        // The webhook ships with the mod, so there is nothing to point anywhere.
        if ("off".equalsIgnoreCase(arg)) {
            CelleActions.disableBotReport();
        } else if ("on".equalsIgnoreCase(arg)) {
            CelleActions.enableBotReport();
        } else if ("test".equalsIgnoreCase(arg)) {
            CelleActions.testBotConnection();
        } else {
            CelleActions.message("Brug: /celler bot <on|off|test>");
        }
    }

    private void handleBuyer(String[] args) {
        if (args.length < 2) {
            CelleActions.openCelleBuyer();
            return;
        }
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null) {
            return;
        }
        String arg = args[1];
        if ("on".equalsIgnoreCase(arg)) {
            cfg.celleBuyerEnabled = true;
            cfg.save();
            CelleActions.message("Celle Buyer er slaaet til.");
            warnIfNothingPicked(cfg);
        } else if ("off".equalsIgnoreCase(arg)) {
            cfg.celleBuyerEnabled = false;
            cfg.save();
            CelleActions.message("Celle Buyer er slaaet fra.");
        } else if ("list".equalsIgnoreCase(arg) || "liste".equalsIgnoreCase(arg)) {
            listPicks(cfg);
        } else if ("add".equalsIgnoreCase(arg) || "tilfoej".equalsIgnoreCase(arg)) {
            if (args.length < 3) {
                CelleActions.message("Brug: /celler buyer add <celle-id>");
                return;
            }
            String id = CelleBuyer.normalizeId(args[2]);
            if (CelleBuyer.addToWhitelist(cfg, args[2])) {
                CelleActions.message(id + " tilfoejet."
                        + (CellePositions.get(id) == null
                        ? " Den er ikke scannet endnu, saa der er ingen boks endnu."
                        : " Se regnbue-boksen."));
            } else {
                CelleActions.message(id + " er allerede paa listen.");
            }
        } else if ("remove".equalsIgnoreCase(arg) || "fjern".equalsIgnoreCase(arg)) {
            if (args.length < 3) {
                CelleActions.message("Brug: /celler buyer fjern <celle-id>");
                return;
            }
            CelleBuyer.removeFromWhitelist(cfg, args[2]);
            CelleActions.message(CelleBuyer.normalizeId(args[2]) + " fjernet.");
        } else if ("clear".equalsIgnoreCase(arg) || "ryd".equalsIgnoreCase(arg)) {
            if (cfg.celleBuyerWhitelist != null) {
                cfg.celleBuyerWhitelist.clear();
            }
            cfg.save();
            CelleActions.message("Listen er ryddet.");
        } else {
            CelleActions.message("Brug: /celler buyer <on|off|add|fjern|liste|ryd>");
        }
    }

    private void listPicks(CelleConfig cfg) {
        if (cfg.celleBuyerWhitelist == null || cfg.celleBuyerWhitelist.isEmpty()) {
            CelleActions.message("Ingen celler valgt.");
            warnIfNothingPicked(cfg);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cfg.celleBuyerWhitelist.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(cfg.celleBuyerWhitelist.get(i));
        }
        CelleActions.message("Valgte celler (" + cfg.celleBuyerWhitelist.size() + "): " + sb);
    }

    /** The one combination that runs but never fires, said out loud. */
    private void warnIfNothingPicked(CelleConfig cfg) {
        if (cfg.celleBuyerEnabled && cfg.celleBuyerUseWhitelist
                && (cfg.celleBuyerWhitelist == null || cfg.celleBuyerWhitelist.isEmpty())) {
            CelleActions.message("Listen er tom, saa den koeber ingenting."
                    + " Tilfoej en celle med /celler buyer add <id>.");
        }
    }

    private void handleSpecial(String[] args) {
        if (args.length < 2) {
            CelleActions.openSpecialScreen();
            return;
        }
        String action = args[1].toLowerCase();
        if ("add".equals(action)) {
            if (args.length < 3) {
                CelleActions.message("Brug: /celler special add <id>");
                return;
            }
            CelleActions.addSpecialCelle(args[2]);
        } else if ("remove".equals(action)) {
            if (args.length < 3) {
                CelleActions.message("Brug: /celler special remove <id>");
                return;
            }
            CelleActions.removeSpecialCelle(args[2]);
        } else if ("clear".equals(action)) {
            CelleActions.clearSpecialCelles();
        } else if ("list".equals(action)) {
            CelleActions.listSpecialCelles();
        } else {
            CelleActions.message("Brug: /celler special <add|remove|clear|list> [id]");
        }
    }

    private void handleFind(String[] args) {
        if (args.length < 2) {
            CelleActions.openFinderScreen();
            return;
        }
        if ("stop".equalsIgnoreCase(args[1])) {
            CelleActions.clearFinderTarget();
        } else {
            CelleActions.setFinderTarget(args[1]);
        }
    }

    private void setHours(String[] args, boolean isMin) {
        if (args.length < 2) {
            CelleActions.message("Brug: /celler " + (isMin ? "min" : "max") + " <timer>");
            return;
        }
        try {
            int value = Integer.parseInt(args[1]);
            if (isMin) {
                CelleActions.setMinHours(value);
            } else {
                CelleActions.setMaxHours(value);
            }
        } catch (NumberFormatException e) {
            CelleActions.message("\"" + args[1] + "\" er ikke et gyldigt tal.");
        }
    }

    private void printHelp() {
        CelleActions.message("Kommandoer:");
        CelleActions.message("/celler - åbn kontrolpanelet (GUI)");
        CelleActions.message("/celler settings - åbn visningsindstillinger");
        CelleActions.message("/celler toggle - slå scanneren til/fra");
        CelleActions.message("/celler notify - slå notifikationer til/fra");
        CelleActions.message("/celler reload - genindlæs config.json");
        CelleActions.message("/celler debug - vis cachen i chat");
        CelleActions.message("/celler clear - ryd cachen");
        CelleActions.message("/celler move - træk HUD'et til en ny placering");
        CelleActions.message("/celler esp - slå gennemsigtig outline til/fra");
        CelleActions.message("/celler showseconds - vis/skjul sekunder i nedtælling");
        CelleActions.message("/celler showowner - vis/skjul ejernavn på HUD");
        CelleActions.message("/celler showstatus - vis/skjul status-mærke på HUD");
        CelleActions.message("/celler showdistance - vis/skjul afstand på HUD");
        CelleActions.message("/celler esplabels - vis/skjul celle-id label over ESP-kasser");
        CelleActions.message("/celler espdistance <blokke> - sæt maks-afstand for ESP");
        CelleActions.message("/celler bot - åbn Celle Bot-skærmen");
        CelleActions.message("/celler bot <on|off> - slå deling af scannede celler til eller fra");
        CelleActions.message("/celler bot test - send en test-rapport");
        CelleActions.message("/celler buyer - åbn Celle Buyer-skærmen");
        CelleActions.message("/celler buyer <on|off> - slå automatisk køb til eller fra");
        CelleActions.message("/celler buyer add <id> - vælg en celle den må købe");
        CelleActions.message("/celler buyer fjern <id> - fjern en celle fra listen");
        CelleActions.message("/celler buyer liste - vis de valgte celler");
        CelleActions.message("/celler buyer ryd - tøm listen");
        CelleActions.message("/celler special - åbn special-celle skærmen");
        CelleActions.message("/celler special add <id> - flag en celle som special");
        CelleActions.message("/celler special remove <id> - fjern flaget igen");
        CelleActions.message("/celler special clear - fjern alle special-celler");
        CelleActions.message("/celler special list - vis special-celler i chat");
        CelleActions.message("/celler find - åbn Celle Finder-skærmen");
        CelleActions.message("/celler find <id> - fremhæv en celle via ESP + HUD-kompas");
        CelleActions.message("/celler find stop - stop finderen");
        CelleActions.message("/celler min <timer> - sæt minHours");
        CelleActions.message("/celler max <timer> - sæt maxHours");
    }
}
