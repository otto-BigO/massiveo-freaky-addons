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
    public static final String VERSION = "1.0.1";

    public static CelleConfig config;
    public static CelleScanner scanner;
    public static CelleHud hud;
    public static CelleEsp esp;
    public static KeyBinding openMenuKey;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = new CelleConfig(event.getSuggestedConfigurationFile());
        config.load();
        CellePositions.init(event.getSuggestedConfigurationFile().getParentFile());
        ItemValues.init(event.getSuggestedConfigurationFile().getParentFile());
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
        MinecraftForge.EVENT_BUS.register(new ItemValues());
        MinecraftForge.EVENT_BUS.register(new AutoUpdater());
        AutoUpdater.checkAsync();

        ClientCommandHandler.instance.registerCommand(new CommandCeller());

        // Register the addons shown in the hub, grouped by category. Adding
        // another addon later is just another register() call plus its screen.
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

        openMenuKey = new KeyBinding("key.cellescanner.menu", Keyboard.KEY_B, "key.categories.cellescanner");
        ClientRegistry.registerKeyBinding(openMenuKey);
    }
}
