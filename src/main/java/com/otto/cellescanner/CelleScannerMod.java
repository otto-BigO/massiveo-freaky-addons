package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = CelleScannerMod.MODID, name = CelleScannerMod.NAME, version = CelleScannerMod.VERSION, clientSideOnly = true)
public class CelleScannerMod {

    // Mod id stays "cellescanner" so existing config/save files keep loading;
    // the display name is the new hub brand. See MassiveoAddons.
    public static final String MODID = "cellescanner";
    public static final String NAME = "Massiveo's Freaky Addons";
    public static final String VERSION = "2.6.0-test";

    public static CelleConfig config;
    public static CelleScanner scanner;
    public static CelleHud hud;
    public static CelleEsp esp;

    /** True once the feature addons have been put on the event bus (after licence verify). */
    private static boolean addonsEnabled = false;
    public static KeyBinding openMenuKey;
    public static KeyBinding autoMineKey;
    public static KeyBinding phoneKey;
    public static KeyBinding majesticaKey;
    public static KeyBinding freecamKey;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new CelleConfig(event.getSuggestedConfigurationFile());
        config.load();
        CellePositions.init(event.getSuggestedConfigurationFile().getParentFile());
        ItemValues.init(event.getSuggestedConfigurationFile().getParentFile());
        ChestOrganizerPositions.init(event.getSuggestedConfigurationFile().getParentFile());
        // Gange feature shelved - GangRanges.init(...) left out for now.
        ArmorSkins.registerVariants();
        MajesticaWeapons.INSTANCE.init();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        scanner = new CelleScanner();
        hud = new CelleHud();
        esp = new CelleEsp();

        // The hub keybind and the auto-updater. The addons themselves are put on
        // the bus by enableAddons() at the end of init().
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        // The update check is NOT started here. Doing network + class loading
        // during startup can contend with (Laby)Mod's own startup on the shared
        // classloader and trip its render-thread watchdog. AutoUpdater kicks the
        // check off a few seconds into the first client ticks instead.
        MinecraftForge.EVENT_BUS.register(new AutoUpdater());

        ClientCommandHandler.instance.registerCommand(new CommandCeller());
        ClientCommandHandler.instance.registerCommand(new CommandClearLogouts());
        ClientCommandHandler.instance.registerCommand(new CommandFollow());

        // Addons are registered lazily (AddonList.ensureRegistered, called
        // when the hub first opens) to keep startup class loading minimal.

        openMenuKey = new KeyBinding("key.cellescanner.menu", Keyboard.KEY_B, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(openMenuKey);
        
        autoMineKey = new KeyBinding("key.cellescanner.automine", Keyboard.KEY_NONE, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(autoMineKey);

        majesticaKey = new KeyBinding("key.cellescanner.majestica", Keyboard.KEY_V, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(majesticaKey);

        freecamKey = new KeyBinding("key.cellescanner.freecam", Keyboard.KEY_U, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(freecamKey);

        // phoneKey shelved for now - code stays in repo for later.
        // phoneKey = new KeyBinding("key.cellescanner.phone", Keyboard.KEY_P, "key.categories.cellescanner");
        // ClientRegistry.registerKeyBinding(phoneKey);

        // Licence gate shelved: it was only half-wired (no key-entry screen) and
        // blocked every addon from registering, so the mod loaded but did nothing.
        // Register all addons directly so everything just works. The gate code
        // (AccessGate, AccessSystem, GuiAccessKey) stays in the repo for later.
        enableAddons();
    }

    /**
     * Registers all the feature addons on the event bus. Called once at the end of
     * init(). Idempotent - the addonsEnabled guard makes repeat calls a no-op.
     */
    public static void enableAddons() {
        if (addonsEnabled) {
            return;
        }
        addonsEnabled = true;

        MinecraftForge.EVENT_BUS.register(scanner);
        MinecraftForge.EVENT_BUS.register(hud);
        MinecraftForge.EVENT_BUS.register(esp);

        MinecraftForge.EVENT_BUS.register(new AntiAfk());
        MinecraftForge.EVENT_BUS.register(new BandeEsp());
        MinecraftForge.EVENT_BUS.register(new ChestAlarm());
        MinecraftForge.EVENT_BUS.register(new ArmorSkins());
        MinecraftForge.EVENT_BUS.register(new MineCeller());
        // GangInfo (passive gang detection) shelved - not registered for now.
        MinecraftForge.EVENT_BUS.register(new PlayerInfo());
        MinecraftForge.EVENT_BUS.register(new TrollSounds());
        MinecraftForge.EVENT_BUS.register(new ItemPickupNotify());
        MinecraftForge.EVENT_BUS.register(new PvpMine());
        MinecraftForge.EVENT_BUS.register(new AutoMine());
        MinecraftForge.EVENT_BUS.register(new AutoFish());
        MinecraftForge.EVENT_BUS.register(new AutoCrate());
        MinecraftForge.EVENT_BUS.register(new FastMine());
        MinecraftForge.EVENT_BUS.register(new ChestOrganizer());
        MinecraftForge.EVENT_BUS.register(new IronDoorSounds());
        MinecraftForge.EVENT_BUS.register(PlayerLogger.INSTANCE);
        // PhoneNotification (Venne Telefon) shelved for now - code stays in repo.
        // MinecraftForge.EVENT_BUS.register(new PhoneNotification());
        MinecraftForge.EVENT_BUS.register(new FarmBot());
        MinecraftForge.EVENT_BUS.register(new PlayerEsp());
        MinecraftForge.EVENT_BUS.register(new PathWalker());
        MinecraftForge.EVENT_BUS.register(new AutoFollow());
        MinecraftForge.EVENT_BUS.register(new FlipDebug());
        // Mod-brugere (ModUserIcon) shelved for now - see AddonList. Not
        // registered so it does nothing until we pick it back up.
        MinecraftForge.EVENT_BUS.register(new ItemValues());
        MinecraftForge.EVENT_BUS.register(new ArmorHud());
        MinecraftForge.EVENT_BUS.register(MajesticaWeapons.INSTANCE);
        MinecraftForge.EVENT_BUS.register(Freecam.INSTANCE);
    }
}
