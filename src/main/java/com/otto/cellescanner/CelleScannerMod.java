package com.otto.cellescanner;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.lwjgl.input.Keyboard;

@Mod(modid = CelleScannerMod.MODID, name = CelleScannerMod.NAME, version = CelleScannerMod.VERSION, clientSideOnly = true)
public class CelleScannerMod {

    // Mod id stays "cellescanner" so existing config/save files keep loading;
    // the display name is the new hub brand. See MassiveoAddons.
    public static final String MODID = "cellescanner";
    public static final String NAME = "Massiveo's Freaky Addons";
    public static final String VERSION = "1.1.4-t3";

    public static CelleConfig config;
    public static CelleScanner scanner;
    public static CelleHud hud;
    public static CelleEsp esp;
    public static KeyBinding openMenuKey;
    public static KeyBinding autoMineKey;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new CelleConfig(event.getSuggestedConfigurationFile());
        config.load();
        CellePositions.init(event.getSuggestedConfigurationFile().getParentFile());
        ItemValues.init(event.getSuggestedConfigurationFile().getParentFile());
        // Gange feature shelved - GangRanges.init(...) left out for now.
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        scanner = new CelleScanner();
        hud = new CelleHud();
        esp = new CelleEsp();

        MinecraftForge.EVENT_BUS.register(scanner);
        MinecraftForge.EVENT_BUS.register(hud);
        MinecraftForge.EVENT_BUS.register(esp);
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
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
        MinecraftForge.EVENT_BUS.register(new ModUserIcon());
        MinecraftForge.EVENT_BUS.register(new ItemValues());
        MinecraftForge.EVENT_BUS.register(new ArmorHud());
        // The update check is NOT started here. Doing network + class loading
        // during startup can contend with (Laby)Mod's own startup on the shared
        // classloader and trip its render-thread watchdog. AutoUpdater kicks the
        // check off a few seconds into the first client ticks instead.
        MinecraftForge.EVENT_BUS.register(new AutoUpdater());

        ClientCommandHandler.instance.registerCommand(new CommandCeller());

        // Addons are registered lazily (AddonList.ensureRegistered, called
        // when the hub first opens) to keep startup class loading minimal.

        openMenuKey = new KeyBinding("key.cellescanner.menu", Keyboard.KEY_B, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(openMenuKey);
        autoMineKey = new KeyBinding("key.cellescanner.automine", Keyboard.KEY_NONE, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(autoMineKey);
    }
}
