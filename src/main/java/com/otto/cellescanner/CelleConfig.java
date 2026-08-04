package com.otto.cellescanner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    public boolean hideBuyableCeller = false;
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
    /**
     * The shared reporting webhook, bundled so the addon reports out of the box.
     *
     * This ships inside a public release jar, so treat it as public: anyone can
     * read it out of the jar and post into the data channel. Rotate it by making
     * a new webhook in Discord and shipping a build with the new value.
     */
    public static final String DEFAULT_REPORTS_WEBHOOK_URL =
            "https://discord.com/api/webhooks/1533878453823213598/wEyDKzEWhB-6ztDV491Wcdvg7v_PbjwSyC9-pHN6iqTeBnisuEIs6fsjq6GD4yJEgtjU";

    /** Passive timing measurement for the sign countdown. Records only. */
    public boolean timingLogEnabled = true;

    public boolean botReportEnabled = true;

    /**
     * Bumped when the reporting defaults change. A config written before the
     * webhook shipped with the mod has this at 0 and has reporting off, which
     * meant "never set up" rather than "turned off on purpose", so it gets
     * switched on once. After that the saved choice is respected.
     */
    public int reportConfigVersion = 1;

    /** The bundled webhook. Not a saved setting, so nothing can point it elsewhere. */
    public static String reportsWebhookUrl() {
        return DEFAULT_REPORTS_WEBHOOK_URL;
    }

    // Anti-AFK addon: periodically performs a tiny action so the server's
    // idle-timer never trips. Off by default. (Kept in this shared config file
    // - the mod id is still "cellescanner" - so all addons persist together.)
    public boolean antiAfkEnabled = false;
    public int antiAfkIntervalSeconds = 30;
    public boolean antiAfkSwing = true;
    public boolean antiAfkRotate = true;
    public boolean antiAfkJump = false;
    // Strafes a step to the right and back to the original spot - actually
    // moves the player, which stricter idle checks want, without drifting away.
    public boolean antiAfkStrafe = false;

    // Bande ESP addon: draws a green outline through walls around players who
    // are in your bande. Membership is a manual name list (reliable on any
    // server), optionally augmented by "same scoreboard team" auto-detection
    // if the server happens to back bande membership with a real team.
    public boolean bandeEspEnabled = true;
    public boolean bandeAutoTeam = false;
    // When on, the ESP boxes EVERY player - bande members green, everyone else
    // red - so you can see all players through walls, not just your bande.
    public boolean bandeEspAll = false;
    public String bandeEspMode = "2D"; // "2D", "Corners", "3D", "Outline"
    public List<String> bandeMembers = new ArrayList<String>();

    // Venne Telefon / Friend ESP fields
    public boolean friendEspEnabled = true;
    public Set<String> friendsList = new HashSet<String>();

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

    // Custom Auto Mine area: two corners the player picks in-game (right-click
    // each). When mineAreaSet is true the bot mines this box instead of the
    // built-in default one, and the box outline is drawn in the world.
    /**
     * Named mines. Each one carries its own area and its own shop, deposit and
     * iron positions, so switching mine is picking a profile rather than
     * re-teaching the bot every location.
     */
    public List<MineProfile> mineProfiles = new ArrayList<MineProfile>();
    public int mineProfileIndex = 0;

    public boolean mineAreaSet = false;
    public int mineAreaX1, mineAreaY1, mineAreaZ1;
    public int mineAreaX2, mineAreaY2, mineAreaZ2;
    public int mineAreaDim = 0;

    // Crazy mode: raw performance - instant snap aiming (no humanized easing)
    // and no wait-until-facing walk gate. Faster, but looks robotic.
    public boolean autoMineCrazy = false;

    // Auto Mine tunables (GUI: Auto Mine -> Indstillinger -> Finjustering).
    // How close (blocks) the bot walks to a block before it stops and mines it.
    public double autoMineApproachDist = 2.7;
    // Mining reach (blocks). Capped GUI-side to a legit value - do not exceed
    // vanilla or anti-cheat flags it.
    public double autoMineReach = 4.5;
    // Swap/discard a pickaxe once its remaining durability is <= this (0 = use
    // it until it breaks).
    public int autoMinePickaxeMin = 0;
    // Whether to detour to pick up our own dropped iron while mining. Nullable
    // so an older config (key absent) defaults to ON, not the Gson-Unsafe false.
    public Boolean autoMineCollectDrops = Boolean.TRUE;
    // Command sent to leave the mine when inventory is 100% full of iron ore.
    public String autoMineLeaveCommand = "/spawn";
    public Boolean autoMineHumanizedDelays = Boolean.TRUE;
    public Boolean autoMineAimJitter = Boolean.TRUE;
    public Boolean autoMineStaffAlert = Boolean.TRUE;
    public Boolean autoMineStaffDisconnect = Boolean.FALSE;
    public Boolean autoMineSmartScaffold = Boolean.TRUE;
    public List<String> staffList = new ArrayList<String>();

    // VK Stealer (vagt kill). Off by default, this only runs when switched on.
    public boolean vkStealerEnabled = false;
    /** Hold fire until the guard is low, so the finishing hit is the one that lands. */
    public boolean vkStealOnly = true;
    /** Attack once health drops to this fraction or below. */
    public float vkHealthThreshold = 0.30f;
    /** When health cannot be read at all, attack anyway rather than never firing. */
    public boolean vkAttackWhenHealthUnknown = true;
    /** Rotation is sent with the packet only, the camera does not move. */
    public boolean vkSilentAim = true;
    /** Only used when silent aim is off: ease onto the target instead of snapping. */
    public boolean vkSmoothAim = true;
    public float vkReach = 3.0f;
    public int vkAttackDelayMs = 100;
    public boolean vkRequireLineOfSight = true;
    public boolean vkTargetInvisible = false;
    // Celle Buyer. Off by default, this only runs when switched on.
    public boolean celleBuyerEnabled = false;
    /** Rotation goes in the packet only, the camera does not move. */
    public boolean celleBuyerSilentAim = true;
    /** Unlocks reach past vanilla, so you can stand back out of the crowd. */
    public boolean celleBuyerExtendedReach = false;
    /** Only used when extended reach is on, otherwise reach is held at vanilla. */
    public float celleBuyerReach = 5.0f;
    /** Start clicking this long before the predicted flip. */
    public int celleBuyerPreClickMs = 250;
    public int celleBuyerClickIntervalMs = 100;
    /** How far ahead of a predicted expiry to start tracking a celle. */
    public int celleBuyerArmSeconds = 60;
    /**
     * Only predict from a countdown this client watched change. Without it the
     * sign may have been read partway through a step and the anchor is early by
     * up to a full twenty minutes.
     */
    public boolean celleBuyerOnlyConfirmed = true;
    /**
     * Only buy celler on the list. On by default, because an addon that claims
     * whatever happens to free up next to you is not something to leave running.
     * An empty list with this on buys nothing, which is the safe way round.
     */
    public boolean celleBuyerUseWhitelist = true;
    /** Picked celle ids, stored uppercase so the sign's own casing does not matter. */
    public List<String> celleBuyerWhitelist = new ArrayList<String>();
    public Boolean majesticaEnabled = Boolean.TRUE;
    public String majesticaSelectedWeaponId = "";
    public Boolean freecamEnabled = Boolean.TRUE;
    public double freecamSpeed = 1.5;

    // Mod-user badge: a small icon before the name of players who also run the
    // mod (like Lunar/LabyMod). Testing: a purple circle before every player.
    public Boolean modIconEnabled = Boolean.TRUE;

    // Debug: dumps the contents of the "Flip!" GUI to chat (for building the flip
    // case-opening addon). Off by default.
    public Boolean debugEnabled = Boolean.FALSE;
    // When debugEnabled is on, also mirror every debug message to massiveo_debug.log
    // in the config directory. Off by default so the file isn't created unless wanted.
    public Boolean debugLogEnabled = Boolean.FALSE;
    public boolean debugOverlayEnabled = false;
    public int debugX = 6;
    public int debugY = 6;

    // Flip Case Opening: replaces the vanilla "Flip!" chest with a CS:GO-style
    // case-opening animation. On by default.
    public boolean flipCaseEnabled = true;

    // Granular HUD and Timer Alert Settings
    public float hudFontScale = 1.0f;
    public float debugScale = 1.0f;
    public float armorHudScale = 1.0f;
    public float itemPickupScale = 1.0f;
    public float pvpMineScale = 1.0f;
    public float mineTrackerScale = 1.0f;
    public boolean mineTrackerEnabled = true;
    public int mineTrackerX = 10;
    public int mineTrackerY = 120;
    public int ironPerDbRatio = 64;

    public boolean autoTrashEnabled = true;
    public boolean trashWoodTools = true;
    public boolean trashStoneTools = true;
    public boolean trashBlocks = true;
    public float hudBgAlpha = 0.6f;
    public boolean alert10mEnabled = true;
    public boolean alert5mEnabled = true;
    public boolean alert2mEnabled = true;
    public boolean alert1mEnabled = true;
    public boolean alert30sEnabled = true;
    public boolean alert10sEnabled = true;

    // Custom Theme & Personalization Settings
    public int themeAccentColor = 0x00FF88; // Default Emerald Matrix (#00FF88)
    public float themeBgAlpha = 0.65f;
    public int themeTitleStyle = 0; // 0 = Rainbow HSB, 1 = Pulsing Glow, 2 = Static Accent
    public String alertSound = "note.pling"; // note.pling, random.levelup, orb.pickup, random.click

    // Custom ESP Colors
    public int espColorBande = 0x00A2FF; // Blue
    public int espColorVagt = 0x00FF88;  // Green
    public int espColorOther = 0xFF3366; // Red

    public boolean autoArmorEnabled = true;

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

    // Auto Fish Addon
    public boolean autoFishEnabled = false;

    // Auto Crate Addon
    public boolean autoCrateEnabled = false;

    // Fast Mine Addon
    public boolean fastMineEnabled = false;

    // Celle Expiry Alerts Addon
    public boolean celleExpiryAlertsEnabled = true;

    // Player Nameplate ESP Addon
    public boolean playerEspEnabled = true;

    // Chest Organizer Addon
    public boolean chestOrganizerEnabled = true;

    // Iron Door Sounds Addon
    public boolean ironDoorSoundsEnabled = true;

    // Player Logger Addon
    public boolean playerLoggerEnabled = true;

    // Farm Bot Addon
    public boolean farmBotEnabled = false;

    // Mod License/Access Key
    public String accessKey = "";
    // Last key + HWID that verified successfully, used to fail open (keep working)
    // if the licence server is briefly unreachable later.
    public String verifiedKey = "";
    public String verifiedHwid = "";

    // Smart Trash Filter for AutoMine bot
    public List<String> trashItems = new ArrayList<String>(java.util.Arrays.asList("Cobblestone", "Sandstone", "Lapis Blok", "Lapis Lazuli"));

    public boolean isTrash(net.minecraft.item.ItemStack s) {
        if (s == null) return false;
        net.minecraft.item.Item it = s.getItem();
        if (it == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.cobblestone)) {
            return trashItems.contains("Cobblestone");
        }
        if (it == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.sandstone)) {
            return trashItems.contains("Sandstone");
        }
        if (it == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.lapis_block)) {
            return trashItems.contains("Lapis Blok");
        }
        if (it == net.minecraft.init.Items.dye && s.getMetadata() == 4) {
            return trashItems.contains("Lapis Lazuli");
        }
        return false;
    }

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
                // Reporting is on by default now that the webhook ships with the mod.
                // An older config written before that has the flag off with no url,
                // which meant "never set up" rather than "turned off on purpose".
                this.timingLogEnabled = loaded.timingLogEnabled;
                this.reportConfigVersion = loaded.reportConfigVersion;
                if (loaded.reportConfigVersion < 1) {
                    this.botReportEnabled = true;      // one time migration
                    this.reportConfigVersion = 1;
                } else {
                    this.botReportEnabled = loaded.botReportEnabled;
                }
                this.hideBuyableCeller = loaded.hideBuyableCeller;

                this.antiAfkEnabled = loaded.antiAfkEnabled;
                // A config written before this field existed deserializes it as
                // 0; fall back to the sane default instead of an insane interval.
                this.antiAfkIntervalSeconds = loaded.antiAfkIntervalSeconds > 0 ? loaded.antiAfkIntervalSeconds : 30;
                this.antiAfkSwing = loaded.antiAfkSwing;
                this.antiAfkRotate = loaded.antiAfkRotate;
                this.antiAfkJump = loaded.antiAfkJump;
                this.antiAfkStrafe = loaded.antiAfkStrafe;
                this.bandeEspEnabled = loaded.bandeEspEnabled;
                this.bandeAutoTeam = loaded.bandeAutoTeam;
                this.bandeEspAll = loaded.bandeEspAll;
                this.bandeEspMode = loaded.bandeEspMode != null ? loaded.bandeEspMode : "2D";
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
                this.mineAreaSet = loaded.mineAreaSet;
                this.mineAreaX1 = loaded.mineAreaX1;
                this.mineAreaY1 = loaded.mineAreaY1;
                this.mineAreaZ1 = loaded.mineAreaZ1;
                this.mineAreaX2 = loaded.mineAreaX2;
                this.mineAreaY2 = loaded.mineAreaY2;
                this.mineAreaZ2 = loaded.mineAreaZ2;
                this.mineAreaDim = loaded.mineAreaDim;
                if (loaded.mineProfiles != null) {
                    this.mineProfiles = loaded.mineProfiles;
                }
                this.mineProfileIndex = loaded.mineProfileIndex;
                migrateMineProfiles();
                this.autoMineCrazy = loaded.autoMineCrazy;
                this.autoMineApproachDist = loaded.autoMineApproachDist > 0 ? loaded.autoMineApproachDist : 2.7;
                this.autoMineReach = loaded.autoMineReach > 0 ? loaded.autoMineReach : 4.5;
                this.autoMinePickaxeMin = loaded.autoMinePickaxeMin;
                this.autoMineCollectDrops = loaded.autoMineCollectDrops != null ? loaded.autoMineCollectDrops : Boolean.TRUE;
                this.autoMineLeaveCommand = (loaded.autoMineLeaveCommand != null && !loaded.autoMineLeaveCommand.isEmpty()) ? loaded.autoMineLeaveCommand : "/spawn";
                this.autoMineHumanizedDelays = loaded.autoMineHumanizedDelays != null ? loaded.autoMineHumanizedDelays : Boolean.TRUE;
                this.autoMineAimJitter = loaded.autoMineAimJitter != null ? loaded.autoMineAimJitter : Boolean.TRUE;
                this.autoMineStaffAlert = loaded.autoMineStaffAlert != null ? loaded.autoMineStaffAlert : Boolean.TRUE;
                this.autoMineStaffDisconnect = loaded.autoMineStaffDisconnect != null ? loaded.autoMineStaffDisconnect : Boolean.FALSE;
                this.autoMineSmartScaffold = loaded.autoMineSmartScaffold != null ? loaded.autoMineSmartScaffold : Boolean.TRUE;
                this.staffList = loaded.staffList != null ? loaded.staffList : new ArrayList<String>();
                this.vkStealerEnabled = loaded.vkStealerEnabled;
                this.vkStealOnly = loaded.vkStealOnly;
                this.vkHealthThreshold = Math.max(0.05f, Math.min(1.0f,
                        loaded.vkHealthThreshold <= 0f ? 0.30f : loaded.vkHealthThreshold));
                this.vkAttackWhenHealthUnknown = loaded.vkAttackWhenHealthUnknown;
                this.vkSilentAim = loaded.vkSilentAim;
                this.vkSmoothAim = loaded.vkSmoothAim;
                this.vkReach = Math.max(1.0f, Math.min(3.0f, loaded.vkReach <= 0f ? 3.0f : loaded.vkReach));
                this.vkAttackDelayMs = Math.max(50, loaded.vkAttackDelayMs <= 0 ? 100 : loaded.vkAttackDelayMs);
                this.vkRequireLineOfSight = loaded.vkRequireLineOfSight;
                this.vkTargetInvisible = loaded.vkTargetInvisible;
                this.celleBuyerEnabled = loaded.celleBuyerEnabled;
                this.celleBuyerSilentAim = loaded.celleBuyerSilentAim;
                this.celleBuyerExtendedReach = loaded.celleBuyerExtendedReach;
                this.celleBuyerReach = Math.max(CelleBuyer.VANILLA_REACH,
                        Math.min(CelleBuyer.MAX_REACH,
                                loaded.celleBuyerReach <= 0f ? 5.0f : loaded.celleBuyerReach));
                this.celleBuyerPreClickMs = Math.max(0,
                        Math.min(1000, loaded.celleBuyerPreClickMs));
                this.celleBuyerClickIntervalMs = Math.max(50,
                        loaded.celleBuyerClickIntervalMs <= 0 ? 100 : loaded.celleBuyerClickIntervalMs);
                this.celleBuyerArmSeconds = Math.max(5,
                        Math.min(600, loaded.celleBuyerArmSeconds <= 0 ? 60 : loaded.celleBuyerArmSeconds));
                this.celleBuyerOnlyConfirmed = loaded.celleBuyerOnlyConfirmed;
                this.celleBuyerUseWhitelist = loaded.celleBuyerUseWhitelist;
                if (loaded.celleBuyerWhitelist != null) {
                    this.celleBuyerWhitelist = loaded.celleBuyerWhitelist;
                }
                if (this.staffList.isEmpty()) {
                    String[] defaultStaff = {
                        "Direktør", "Inspektør", "Officer", "Vagt", "Ejer", "Hoved Administrator", "Administrator", "Moderator", "Hjælper",
                        "HiJnDK", "Lucas_AS", "Sykopingvin", "EmiiiloVich", "Gyymmr", "LegendenVoldby", "Slyqnn", "Svambz", "Sw1chhh", "V3gaaa", "Zeqnix",
                        "__Kn0x__", "_Annemette_v3", "_Flexiii_", "_G1ey", "_PinkBee_", "0ks3n", "123dipper", "3D4L", "90GRIMLOCK90", "AdamAbe2009",
                        "Al3xfsjensen", "Aleex3nder", "AndersTekkitv2", "Ant0n__", "AskeVII", "badensoe", "Bangebuks", "BetaBoris", "Blebman",
                        "BlockGaarAmok", "Bongo_Bent", "Buchwaldtnr2", "C4trineSejr", "Cubra", "D1ScReeTs", "Daniel_kongen", "DISNE7", "DoecProductions",
                        "ducks_o", "El_broero07", "ElChapo_AKAV2", "Forfatter", "FreddyForCarry", "GaardXD", "GirlBoost", "Idag_", "ItsVictorV2_",
                        "JasonInflation", "Jeg3lskerV4fler", "Juhll", "jullekrog", "jyttahXD", "K0CAA", "Kaho0t", "Kasper_Kejser", "KattenTheo",
                        "KevinKris", "Killerbody234", "Kio4567_", "L4uiseSejr", "LasseAaB", "LinusFrede", "luffegamerv4", "lugi0012", "Macaaaroni",
                        "Magnus_fed", "mathiaske", "mini_pleb", "molin18", "Muni_Jr", "Neeeeed", "NotMakker", "Nuddi_Gaming", "Oliber1337",
                        "OneGlitchs", "orkenrottendiego", "OskarFrede", "Ostepopss", "OzzyDK", "PapEske", "PizzaJarlen", "Platov1", "PokiPoscar",
                        "Pr1nglesMan", "R1ck99", "Rallemuzen", "ROMEO_BRIX", "Sanderhaj", "ShambyGod", "Siintro", "Skayerboy", "Skyperch_",
                        "Sparegrisenn", "Stilheden", "T1ldsen", "TechnoF1shQ", "TewrrV", "The_SnowTroll", "TheguubDX", "Tiske_TaskeV2", "Tobi_Cake",
                        "TruckerHD", "Tyenda", "Vengeld_", "ViktorFanzyMan", "WarpV2", "wikzzzz", "xPamgo", "yelruts", "freakdk", "aNdIrEas",
                        "OGreenC", "sumsar1812", "Asbjorn", "Yohanx", "Androte", "Bil3s", "DD_Was_Taken", "Fraxizz", "FredeJH", "FvForum",
                        "iiNilo", "JakobFogh", "SebFyren", "xSelmaa", "Zel0DK", "Zunit"
                    };
                    for (String name : defaultStaff) {
                        this.staffList.add(name);
                    }
                }
                this.majesticaEnabled = loaded.majesticaEnabled == null ? Boolean.TRUE : loaded.majesticaEnabled;
                this.majesticaSelectedWeaponId = loaded.majesticaSelectedWeaponId != null ? loaded.majesticaSelectedWeaponId : "";
                this.freecamEnabled = loaded.freecamEnabled == null ? Boolean.TRUE : loaded.freecamEnabled;
                this.freecamSpeed = loaded.freecamSpeed > 0 ? loaded.freecamSpeed : 1.5;
                this.modIconEnabled = loaded.modIconEnabled == null ? Boolean.TRUE : loaded.modIconEnabled;
                this.debugEnabled = loaded.debugEnabled == null ? Boolean.FALSE : loaded.debugEnabled;
                this.debugLogEnabled = loaded.debugLogEnabled == null ? Boolean.FALSE : loaded.debugLogEnabled;
                this.debugOverlayEnabled = loaded.debugOverlayEnabled;
                this.debugX = loaded.debugX;
                this.debugY = loaded.debugY;
                this.flipCaseEnabled = loaded.flipCaseEnabled;
                this.hudFontScale = loaded.hudFontScale > 0.1f ? loaded.hudFontScale : 1.0f;
                this.debugScale = loaded.debugScale > 0.1f ? loaded.debugScale : 1.0f;
                this.armorHudScale = loaded.armorHudScale > 0.1f ? loaded.armorHudScale : 1.0f;
                this.itemPickupScale = loaded.itemPickupScale > 0.1f ? loaded.itemPickupScale : 1.0f;
                this.pvpMineScale = loaded.pvpMineScale > 0.1f ? loaded.pvpMineScale : 1.0f;
                this.mineTrackerScale = loaded.mineTrackerScale > 0.1f ? loaded.mineTrackerScale : 1.0f;
                this.mineTrackerEnabled = loaded.mineTrackerEnabled;
                this.mineTrackerX = loaded.mineTrackerX;
                this.mineTrackerY = loaded.mineTrackerY;
                this.ironPerDbRatio = loaded.ironPerDbRatio > 0 ? loaded.ironPerDbRatio : 64;
                this.autoTrashEnabled = loaded.autoTrashEnabled;
                this.trashWoodTools = loaded.trashWoodTools;
                this.trashStoneTools = loaded.trashStoneTools;
                this.trashBlocks = loaded.trashBlocks;
                this.hudBgAlpha = loaded.hudBgAlpha;
                this.alert10mEnabled = loaded.alert10mEnabled;
                this.alert5mEnabled = loaded.alert5mEnabled;
                this.alert2mEnabled = loaded.alert2mEnabled;
                this.alert1mEnabled = loaded.alert1mEnabled;
                this.alert30sEnabled = loaded.alert30sEnabled;
                this.alert10sEnabled = loaded.alert10sEnabled;
                this.themeAccentColor = loaded.themeAccentColor != 0 ? loaded.themeAccentColor : 0x00FF88;
                // Clamp into the usable range instead of treating any non-positive
                // value as "unset", which silently threw away a saved setting.
                this.themeBgAlpha = loaded.themeBgAlpha <= 0f ? 0.65f
                        : Math.max(0.20f, Math.min(1.0f, loaded.themeBgAlpha));
                this.themeTitleStyle = loaded.themeTitleStyle;
                this.alertSound = loaded.alertSound != null && !loaded.alertSound.isEmpty() ? loaded.alertSound : "note.pling";
                this.espColorBande = loaded.espColorBande != 0 ? loaded.espColorBande : 0x00A2FF;
                this.espColorVagt = loaded.espColorVagt != 0 ? loaded.espColorVagt : 0x00FF88;
                this.espColorOther = loaded.espColorOther != 0 ? loaded.espColorOther : 0xFF3366;
                this.autoArmorEnabled = loaded.autoArmorEnabled;
                this.itemValueEnabled = loaded.itemValueEnabled;
                this.autoUpdateEnabled = loaded.autoUpdateEnabled;
                this.autoUpdatePreRelease = loaded.autoUpdatePreRelease;
                this.armorHudEnabled = loaded.armorHudEnabled;
                this.armorHudX = loaded.armorHudX;
                this.armorHudY = loaded.armorHudY;
                this.armorHudWarnPercent = loaded.armorHudWarnPercent > 0 ? loaded.armorHudWarnPercent : 10;
                this.specialCelleIds = loaded.specialCelleIds != null ? loaded.specialCelleIds : new ArrayList<String>();
                this.autoFishEnabled = loaded.autoFishEnabled;
                this.autoCrateEnabled = loaded.autoCrateEnabled;
                this.celleExpiryAlertsEnabled = loaded.celleExpiryAlertsEnabled;
                this.playerEspEnabled = loaded.playerEspEnabled;
                this.chestOrganizerEnabled = loaded.chestOrganizerEnabled;
                this.ironDoorSoundsEnabled = loaded.ironDoorSoundsEnabled;
                this.playerLoggerEnabled = loaded.playerLoggerEnabled;
                this.farmBotEnabled = loaded.farmBotEnabled;
                this.fastMineEnabled = loaded.fastMineEnabled;
                this.friendEspEnabled = loaded.friendEspEnabled;
                this.friendsList = loaded.friendsList != null ? loaded.friendsList : new HashSet<String>();
                this.accessKey = loaded.accessKey != null ? loaded.accessKey : "";
                this.verifiedKey = loaded.verifiedKey != null ? loaded.verifiedKey : "";
                this.verifiedHwid = loaded.verifiedHwid != null ? loaded.verifiedHwid : "";
                this.trashItems = loaded.trashItems != null ? loaded.trashItems : new ArrayList<String>(java.util.Arrays.asList("Cobblestone", "Sandstone", "Lapis Blok", "Lapis Lazuli"));
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

    /**
     * Makes sure there is always at least one profile, and carries a pre-profile
     * config across. Someone who had already set a mine area keeps it as their
     * first profile rather than finding the bot pointed back at the built-in box.
     */
    public void migrateMineProfiles() {
        if (mineProfiles == null) {
            mineProfiles = new ArrayList<MineProfile>();
        }
        if (mineProfiles.isEmpty()) {
            MineProfile p = new MineProfile(mineAreaSet ? "Min mine" : "Standard");
            if (mineAreaSet) {
                p.areaSet = true;
                p.x1 = mineAreaX1; p.y1 = mineAreaY1; p.z1 = mineAreaZ1;
                p.x2 = mineAreaX2; p.y2 = mineAreaY2; p.z2 = mineAreaZ2;
                p.dimension = mineAreaDim;
            }
            mineProfiles.add(p);
        }
        if (mineProfileIndex < 0 || mineProfileIndex >= mineProfiles.size()) {
            mineProfileIndex = 0;
        }
    }

    /** The mine the bot is currently set to. Never null. */
    public MineProfile activeMineProfile() {
        migrateMineProfiles();
        return mineProfiles.get(mineProfileIndex);
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
