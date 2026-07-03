package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple JSON-backed config:
 * {
 *   "enabled": true,
 *   "minHours": 1,
 *   "maxHours": 10,
 *   "maxHudEntries": 10,
 *   "notify": true
 * }
 *
 * hudX/hudY are persisted too so the draggable HUD remembers its position.
 */
public class CelleConfig {

    private final transient File file;
    private final transient Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public int minHours = 1;
    public int maxHours = 10;
    public int maxHudEntries = 10;
    public boolean notify = true;
    public int hudX = 10;
    public int hudY = 10;
    public boolean espEnabled = true;

    // Optional HUD display info - configurable via the in-game Settings screen.
    public boolean showSeconds = true;
    public boolean showOwner = true;
    public boolean showStatusTag = true;
    public boolean showDistance = false;
    public boolean espLabels = true;
    public double espMaxDistance = 64.0;

    // Reports this client's full local celle cache into a Discord webhook
    // pointed at a dedicated "reports" channel, instead of posting a
    // dashboard directly (see the CelleScannerBot project). The companion
    // bot reads that channel, merges sightings from every connected client
    // into one shared dashboard, and handles special-celle alerts centrally
    // - a plain webhook alone can't combine what multiple players' clients
    // each individually know, which is why the bot exists at all.
    //
    // Using a webhook as the transport (instead of the mod POSTing straight
    // to a server the bot runs) means the bot never needs an open inbound
    // port or a reachable hostname - a Discord webhook URL always works
    // from anywhere with no networking setup on the bot's end.
    //
    // Baked in as a default so the mod "ships ready to go" - anyone who
    // installs it gets this value the first time config.json is created,
    // with nothing to configure by hand. Still "" until the reports channel
    // + its webhook exist for real; flip botReportEnabled to true and fill
    // this in once that's set up, then rebuild and redistribute the jar.
    //
    // NOTE: baking a secret into the jar only keeps out casual snooping -
    // anyone who decompiles it (trivial for a Java mod) can read this URL
    // straight out, same as any Discord webhook shared with a group. Fine
    // for a friend group; rotate it (create a new webhook, update this
    // default, rebuild) if that jar ever spreads further than intended.
    public boolean botReportEnabled = false;
    public String reportsWebhookUrl = "";

    // Anti-AFK addon: periodically performs a tiny action so the server's
    // idle-timer never trips. Off by default. (Kept in this shared config file
    // - the mod id is still "cellescanner" - so all addons persist together.)
    public boolean antiAfkEnabled = false;
    public int antiAfkIntervalSeconds = 30;
    public boolean antiAfkSwing = true;
    public boolean antiAfkRotate = true;
    public boolean antiAfkJump = false;

    // Bande ESP addon: draws a green outline through walls around players who
    // are in your bande. Membership is a manual name list (reliable on any
    // server), optionally augmented by "same scoreboard team" auto-detection
    // if the server happens to back bande membership with a real team.
    public boolean bandeEspEnabled = true;
    public boolean bandeAutoTeam = false;
    // When on, the ESP boxes EVERY player - bande members green, everyone else
    // red - so you can see all players through walls, not just your bande.
    public boolean bandeEspAll = false;
    public List<String> bandeMembers = new ArrayList<String>();

    // Chest Alarm addon: watches chat for a keyword (the server's chest-alarm
    // line) and, when it appears, flashes a small on-screen notification and
    // plays a note-block sound.
    public boolean chestAlarmEnabled = true;
    public boolean chestAlarmToast = true;
    public boolean chestAlarmSound = true;
    public String chestAlarmKeyword = "CHEST-ALARM";

    // Armor Skins addon: shows the server's protection-level armor with the
    // MesterHolm-style textures so you can tell iron/diamond P1-P4 apart on
    // players, without the rest of that texture pack. Detected the same way the
    // pack does: the Protection enchant level (enchant id 0) on the armor.
    public boolean armorSkinsEnabled = true;
    // Which bundled texture set the armor skins use: "mesterholm" (FreakyVille)
    // or "hypixel" (SkyBlock-style). Both live under textures/models/armor/<pack>/.
    public String armorSkinPack = "mesterholm";

    // Mine Celler addon: your own/co-owned/invited celle ids, captured from the
    // server's "/ce find <you>" chat reply, highlighted with a gold ESP box so
    // you can find your celler among everything else.
    public boolean mineCellerEspEnabled = true;
    public List<String> myCelleIds = new ArrayList<String>();

    // Gange screen: quietly run "/ce info <id>" for scanned celler we don't yet
    // know the gang (corridor) of, learn it from the reply, and group every
    // remembered celle by gang with its live timer. The auto-queries are hidden
    // from chat and throttled; turning this off keeps the screen but stops it
    // filling in new gange on its own. Boxed Boolean so a config written before
    // this field existed loads as null (-> default on) rather than false.
    public Boolean gangAutoQuery = Boolean.TRUE;

    // Item Value addon: shows an item's worth (from the FreakyVille price list)
    // as a line in its tooltip. Prices live in a separate editable file
    // (config/massiveo_prices.json) so they can be updated without a rebuild.
    public boolean itemValueEnabled = true;

    // Auto-update: on launch, check the GitHub repo's latest release and, if
    // newer, download the jar into the mods folder so a restart applies it.
    public boolean autoUpdateEnabled = true;

    // When on, the updater also considers pre-release (test) builds, not just
    // stable releases - so you can ride the test channel. Off by default; a
    // stable release always beats a pre-release of the same version number.
    public boolean autoUpdatePreRelease = false;

    // Player Info addon: shift + right-click a player to see their equipped
    // armor + enchants (and, later, command-derived info) in a popup menu.
    // Boxed Boolean so a config written before this field existed loads as null
    // (-> default on) rather than false.
    public Boolean playerInfoEnabled = Boolean.TRUE;

    // Troll Sounds addon: plays goofy sound effects (client-side, only you hear
    // them) reacting to your gameplay - death, kill, first hit, jump, AFK. Off by
    // default; each event can be toggled individually.
    public boolean trollEnabled = false;
    public Boolean trollDeath = Boolean.TRUE;
    public Boolean trollKill = Boolean.TRUE;
    public Boolean trollFirstHit = Boolean.TRUE;
    public Boolean trollJump = Boolean.TRUE;
    public Boolean trollAfk = Boolean.TRUE;

    // Item pickup log: a small "+N Item" notification that fades out when items
    // enter your inventory (SkyHanni style). Position is boxed so null = "use the
    // default spot (bottom-right)"; set once dragged in the HUD editor.
    public Boolean itemPickupEnabled = Boolean.TRUE;
    public Integer itemPickupX = null;
    public Integer itemPickupY = null;

    // PvP Mine watcher: a HUD with the drop-timer sign, and an alert when another
    // player is inside the mine area (in render distance). Position boxed the same
    // way (null = default, bottom-left).
    public boolean pvpMineEnabled = false;
    public Boolean pvpMineAlert = Boolean.TRUE;
    public Integer pvpMineX = null;
    public Integer pvpMineY = null;

    // Auto Mine addon: automation (like Anti-AFK). Off by default; use only where
    // the server allows it. Mines a fixed mine box, returns to a deposit point
    // when the inventory is full, and resumes when the mine resets.
    public boolean autoMineEnabled = false;

    // Mod-user badge: a small icon before the name of players who also run the
    // mod (like Lunar/LabyMod). Testing: a purple circle before every player.
    public Boolean modIconEnabled = Boolean.TRUE;

    // Armor HUD: shows your equipped armor pieces + durability on screen, with a
    // red warning when a piece drops below armorHudWarnPercent.
    public boolean armorHudEnabled = true;
    public int armorHudX = 5;
    public int armorHudY = 140;
    public int armorHudWarnPercent = 10;

    // Celle ids the user specifically wants watched/highlighted - reported
    // to the bot alongside every scan so it always shows them on the shared
    // dashboard regardless of the minHours/maxHours window, and can trigger
    // a separate one-off alert when one becomes available/imminent. Set via
    // /celler special or the GUI screen.
    public List<String> specialCelleIds = new ArrayList<String>();

    public boolean isSpecial(String celleId) {
        if (celleId == null) {
            return false;
        }
        for (String id : specialCelleIds) {
            if (id != null && id.equalsIgnoreCase(celleId.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean isBandeMember(String playerName) {
        if (playerName == null) {
            return false;
        }
        String trimmed = playerName.trim();
        for (String name : bandeMembers) {
            if (name != null && name.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public boolean isMyCelle(String celleId) {
        if (celleId == null) {
            return false;
        }
        String trimmed = celleId.trim();
        for (String id : myCelleIds) {
            if (id != null && id.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public CelleConfig(File file) {
        this.file = file;
    }

    // Exists only so Gson runs the field initializers above when deserializing.
    // Without a no-arg constructor Gson allocates via Unsafe and skips them, so
    // any field missing from an older config file would load as false/0/null
    // instead of its intended default (e.g. a new "enabled" toggle defaulting
    // to true). Not used directly by the mod.
    private CelleConfig() {
        this.file = null;
    }

    public void load() {
        if (file == null || !file.exists()) {
            save();
            return;
        }

        FileReader reader = null;
        try {
            reader = new FileReader(file);
            // gson.fromJson throws JsonSyntaxException/JsonIOException (both
            // RuntimeExceptions) on a truncated or hand-mangled file, so catch
            // broadly and keep the in-memory defaults rather than letting it
            // propagate out of preInit and hard-crash the client at startup.
            CelleConfig loaded = gson.fromJson(reader, CelleConfig.class);
            if (loaded != null) {
                this.enabled = loaded.enabled;
                this.minHours = loaded.minHours;
                this.maxHours = loaded.maxHours;
                this.maxHudEntries = loaded.maxHudEntries;
                this.notify = loaded.notify;
                this.hudX = loaded.hudX;
                this.hudY = loaded.hudY;
                this.espEnabled = loaded.espEnabled;
                this.showSeconds = loaded.showSeconds;
                this.showOwner = loaded.showOwner;
                this.showStatusTag = loaded.showStatusTag;
                this.showDistance = loaded.showDistance;
                this.espLabels = loaded.espLabels;
                this.espMaxDistance = loaded.espMaxDistance;
                this.botReportEnabled = loaded.botReportEnabled;
                this.reportsWebhookUrl = loaded.reportsWebhookUrl;
                this.antiAfkEnabled = loaded.antiAfkEnabled;
                // A config written before this field existed deserializes it as
                // 0; fall back to the sane default instead of an insane interval.
                this.antiAfkIntervalSeconds = loaded.antiAfkIntervalSeconds > 0 ? loaded.antiAfkIntervalSeconds : 30;
                this.antiAfkSwing = loaded.antiAfkSwing;
                this.antiAfkRotate = loaded.antiAfkRotate;
                this.antiAfkJump = loaded.antiAfkJump;
                this.bandeEspEnabled = loaded.bandeEspEnabled;
                this.bandeAutoTeam = loaded.bandeAutoTeam;
                this.bandeEspAll = loaded.bandeEspAll;
                this.bandeMembers = loaded.bandeMembers != null ? loaded.bandeMembers : new ArrayList<String>();
                this.chestAlarmEnabled = loaded.chestAlarmEnabled;
                this.chestAlarmToast = loaded.chestAlarmToast;
                this.chestAlarmSound = loaded.chestAlarmSound;
                this.chestAlarmKeyword = loaded.chestAlarmKeyword != null && !loaded.chestAlarmKeyword.isEmpty()
                        ? loaded.chestAlarmKeyword : "CHEST-ALARM";
                this.armorSkinsEnabled = loaded.armorSkinsEnabled;
                this.armorSkinPack = "hypixel".equals(loaded.armorSkinPack) ? "hypixel" : "mesterholm";
                this.mineCellerEspEnabled = loaded.mineCellerEspEnabled;
                this.myCelleIds = loaded.myCelleIds != null ? loaded.myCelleIds : new ArrayList<String>();
                this.gangAutoQuery = loaded.gangAutoQuery == null ? Boolean.TRUE : loaded.gangAutoQuery;
                this.playerInfoEnabled = loaded.playerInfoEnabled == null ? Boolean.TRUE : loaded.playerInfoEnabled;
                this.trollEnabled = loaded.trollEnabled;
                this.trollDeath = loaded.trollDeath == null ? Boolean.TRUE : loaded.trollDeath;
                this.trollKill = loaded.trollKill == null ? Boolean.TRUE : loaded.trollKill;
                this.trollFirstHit = loaded.trollFirstHit == null ? Boolean.TRUE : loaded.trollFirstHit;
                this.trollJump = loaded.trollJump == null ? Boolean.TRUE : loaded.trollJump;
                this.trollAfk = loaded.trollAfk == null ? Boolean.TRUE : loaded.trollAfk;
                this.itemPickupEnabled = loaded.itemPickupEnabled == null ? Boolean.TRUE : loaded.itemPickupEnabled;
                this.itemPickupX = loaded.itemPickupX;
                this.itemPickupY = loaded.itemPickupY;
                this.pvpMineEnabled = loaded.pvpMineEnabled;
                this.pvpMineAlert = loaded.pvpMineAlert == null ? Boolean.TRUE : loaded.pvpMineAlert;
                this.pvpMineX = loaded.pvpMineX;
                this.pvpMineY = loaded.pvpMineY;
                this.autoMineEnabled = loaded.autoMineEnabled;
                this.modIconEnabled = loaded.modIconEnabled == null ? Boolean.TRUE : loaded.modIconEnabled;
                this.itemValueEnabled = loaded.itemValueEnabled;
                this.autoUpdateEnabled = loaded.autoUpdateEnabled;
                this.autoUpdatePreRelease = loaded.autoUpdatePreRelease;
                this.armorHudEnabled = loaded.armorHudEnabled;
                this.armorHudX = loaded.armorHudX;
                this.armorHudY = loaded.armorHudY;
                this.armorHudWarnPercent = loaded.armorHudWarnPercent > 0 ? loaded.armorHudWarnPercent : 10;
                this.specialCelleIds = loaded.specialCelleIds != null ? loaded.specialCelleIds : new ArrayList<String>();
            }
        } catch (Exception e) {
            System.err.println("[CelleScanner] Kunne ikke læse config, bruger standardværdier: " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void save() {
        if (file == null) {
            return;
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            try {
                gson.toJson(this, writer);
            } finally {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
