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
        } else if ("bot".equals(sub)) {
            handleBot(args);
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
        if ("off".equalsIgnoreCase(arg)) {
            CelleActions.disableBotReport();
        } else if ("clear".equalsIgnoreCase(arg)) {
            CelleActions.clearBotReport();
        } else if ("test".equalsIgnoreCase(arg)) {
            CelleActions.testBotConnection();
        } else if (arg.toLowerCase().startsWith("http")) {
            CelleActions.setReportsWebhookUrl(arg);
        } else {
            CelleActions.message("Brug: /celler bot <url|off|clear|test>");
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
        CelleActions.message("/celler bot - åbn bot-forbindelses-skærmen (brug denne til webhook-url'en)");
        CelleActions.message("/celler bot <url> - sæt reports-webhook-url via chat");
        CelleActions.message("/celler bot off - deaktiver rapportering (beholder url'en)");
        CelleActions.message("/celler bot clear - deaktiver og glem url'en");
        CelleActions.message("/celler bot test - send en test-rapport");
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
