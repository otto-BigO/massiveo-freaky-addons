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

@Mod(modid = MassiveOsFreakyAddons.MODID, name = MassiveOsFreakyAddons.NAME, version = MassiveOsFreakyAddons.VERSION, clientSideOnly = true)
public class MassiveOsFreakyAddons {

    // Mod id stays "cellescanner" so existing config/save files keep loading;
    // the display name is the new hub brand. See MassiveoAddons.
    public static final String MODID = "cellescanner";
    public static final String NAME = "Massiveo's Freaky Addons 4.2.3";
    public static final String VERSION = "4.9.0";

    public static CelleConfig config;
    public static CelleScanner scanner;
    public static CelleHud hud;
    public static CelleEsp esp;

    /** True once the feature addons have been put on the event bus. */
    private static boolean addonsEnabled = false;
    public static KeyBinding openMenuKey;
    public static KeyBinding autoMineKey;
    public static KeyBinding phoneKey;
    public static KeyBinding majesticaKey;
    public static KeyBinding freecamKey;
    public static KeyBinding armorKey;
    public static KeyBinding debugOverlayKey;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new CelleConfig(event.getSuggestedConfigurationFile());
        config.load();
        // Only touches anything on a genuinely fresh install, where it turns
        // every addon off. Registration is normally deferred to the first hub
        // open to keep startup light, so this pays that cost exactly once.
        AddonList.applyFirstRunDefaults();
        CellePositions.init(event.getSuggestedConfigurationFile().getParentFile());
        CelleTimingLog.init(event.getSuggestedConfigurationFile().getParentFile());
        ItemValues.init(event.getSuggestedConfigurationFile().getParentFile());
        ChestOrganizerPositions.init(event.getSuggestedConfigurationFile().getParentFile());
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
        MinecraftForge.EVENT_BUS.register(new AutoUpdater());

        ClientCommandHandler.instance.registerCommand(new CommandCeller());
        ClientCommandHandler.instance.registerCommand(new CommandClearLogouts());
        ClientCommandHandler.instance.registerCommand(new CommandFollow());

        openMenuKey = new KeyBinding("key.cellescanner.menu", Keyboard.KEY_B, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(openMenuKey);
        
        autoMineKey = new KeyBinding("key.cellescanner.automine", Keyboard.KEY_NONE, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(autoMineKey);

        armorKey = new KeyBinding("key.cellescanner.armor", Keyboard.KEY_R, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(armorKey);

        debugOverlayKey = new KeyBinding("key.cellescanner.debug", Keyboard.KEY_F12, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(debugOverlayKey);

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
        MinecraftForge.EVENT_BUS.register(new Esp());
        MinecraftForge.EVENT_BUS.register(new PlayerEsp());
        MinecraftForge.EVENT_BUS.register(new ChestAlarm());
        MinecraftForge.EVENT_BUS.register(new ArmorSkins());
        MinecraftForge.EVENT_BUS.register(AutoTrash.INSTANCE);
        MinecraftForge.EVENT_BUS.register(MineTracker.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new AutoUpdater());
        MinecraftForge.EVENT_BUS.register(new MineCeller());
        MinecraftForge.EVENT_BUS.register(new PlayerInfo());
        MinecraftForge.EVENT_BUS.register(new TrollSounds());
        MinecraftForge.EVENT_BUS.register(new ItemPickupNotify());
        MinecraftForge.EVENT_BUS.register(new PvpMine());
        MinecraftForge.EVENT_BUS.register(new AutoMine());
        MinecraftForge.EVENT_BUS.register(new AutoFish());
        MinecraftForge.EVENT_BUS.register(new VkStealer());
        MinecraftForge.EVENT_BUS.register(new CelleBuyer());
        MinecraftForge.EVENT_BUS.register(new AutoCrate());
        MinecraftForge.EVENT_BUS.register(AutoArmor.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new FastMine());
        MinecraftForge.EVENT_BUS.register(new ChestOrganizer());
        MinecraftForge.EVENT_BUS.register(new IronDoorSounds());
        MinecraftForge.EVENT_BUS.register(PlayerLogger.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new FarmBot());
        MinecraftForge.EVENT_BUS.register(new PathWalker());
        MinecraftForge.EVENT_BUS.register(new AutoFollow());
        MinecraftForge.EVENT_BUS.register(new FlipDebug());
        MinecraftForge.EVENT_BUS.register(new ArmorHud());
    }
}
