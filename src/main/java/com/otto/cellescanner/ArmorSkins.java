package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Installs {@link CustomArmorLayer} into player renderers and bakes custom armor models.
 */
public class ArmorSkins {

    /** Texture packs that get their own set of baked inventory models. */
    static final String[] PACKS = {"hypixel", "mesterholm"};

    public static void registerVariants() {
        try {
            register(net.minecraft.init.Items.diamond_helmet, "diamond", 1, 4, "helmet");
            register(net.minecraft.init.Items.diamond_chestplate, "diamond", 1, 4, "chestplate");
            register(net.minecraft.init.Items.diamond_leggings, "diamond", 1, 4, "leggings");
            register(net.minecraft.init.Items.diamond_boots, "diamond", 1, 4, "boots");

            register(net.minecraft.init.Items.iron_helmet, "iron", 2, 4, "helmet");
            register(net.minecraft.init.Items.iron_chestplate, "iron", 2, 4, "chestplate");
            register(net.minecraft.init.Items.iron_leggings, "iron", 2, 4, "leggings");
            register(net.minecraft.init.Items.iron_boots, "iron", 2, 4, "boots");

            register(net.minecraft.init.Items.golden_helmet, "diamond", 4, 4, "helmet");
        } catch (Throwable t) {
            System.err.println("[CelleScanner] Failed to register item variants: " + t);
        }
    }

    private static void register(net.minecraft.item.Item item, String material, int minLevel, int maxLevel, String type) {
        try {
            net.minecraft.util.ResourceLocation vanillaLoc = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
            if (vanillaLoc == null) return;
            int count = (maxLevel - minLevel + 1) * PACKS.length + 1;
            net.minecraft.util.ResourceLocation[] locs = new net.minecraft.util.ResourceLocation[count];
            locs[0] = vanillaLoc;
            int idx = 1;
            for (String pack : PACKS) {
                for (int lvl = minLevel; lvl <= maxLevel; lvl++) {
                    locs[idx++] = new net.minecraft.util.ResourceLocation(
                            "cellescanner", pack + "_" + material + "_p" + lvl + "_" + type);
                }
            }
            net.minecraft.client.resources.model.ModelBakery.registerItemVariants(item, locs);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        try {
            CustomArmorItemModel.MODELS.clear();

            String[] items = {"helmet", "chestplate", "leggings", "boots"};
            String[] materials = {"diamond", "iron"};

            int found = 0;
            for (String pack : PACKS) {
                for (String material : materials) {
                    int minLevel = "iron".equals(material) ? 2 : 1;
                    int maxLevel = 4;
                    for (int lvl = minLevel; lvl <= maxLevel; lvl++) {
                        for (String type : items) {
                            String key = "cellescanner:" + pack + "_" + material + "_p" + lvl + "_" + type;
                            ModelResourceLocation loc = new ModelResourceLocation(key, "inventory");
                            IBakedModel model = event.modelRegistry.getObject(loc);
                            if (model != null) {
                                CustomArmorItemModel.MODELS.put(key, model);
                                found++;
                            }
                        }
                    }
                }
            }
            System.out.println("[CelleScanner] ModelBake: found " + found + " custom armor item models");

            wrap(event.modelRegistry, net.minecraft.init.Items.diamond_helmet);
            wrap(event.modelRegistry, net.minecraft.init.Items.diamond_chestplate);
            wrap(event.modelRegistry, net.minecraft.init.Items.diamond_leggings);
            wrap(event.modelRegistry, net.minecraft.init.Items.diamond_boots);

            wrap(event.modelRegistry, net.minecraft.init.Items.iron_helmet);
            wrap(event.modelRegistry, net.minecraft.init.Items.iron_chestplate);
            wrap(event.modelRegistry, net.minecraft.init.Items.iron_leggings);
            wrap(event.modelRegistry, net.minecraft.init.Items.iron_boots);

            wrap(event.modelRegistry, net.minecraft.init.Items.golden_helmet);

        } catch (Throwable t) {
            System.err.println("[CelleScanner] Failed to wrap item models: " + t);
            t.printStackTrace();
        }
    }

    private static void wrap(net.minecraft.util.IRegistry<ModelResourceLocation, IBakedModel> registry, net.minecraft.item.Item item) {
        net.minecraft.util.ResourceLocation regName = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
        if (regName != null) {
            ModelResourceLocation loc = new ModelResourceLocation(regName, "inventory");
            IBakedModel base = registry.getObject(loc);
            if (base != null) {
                registry.putObject(loc, new CustomArmorItemModel(base, item));
            }
        }
    }

    private int checkCounter = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++checkCounter < 40) {
            return;
        }
        checkCounter = 0;

        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        if (rm == null) {
            return;
        }
        Map<String, RenderPlayer> skinMap = rm.getSkinMap();
        if (skinMap == null) {
            return;
        }
        for (RenderPlayer renderer : skinMap.values()) {
            ensureInstalled(renderer);
        }
    }

    private void ensureInstalled(RenderPlayer renderer) {
        List layers = getLayers(renderer);
        if (layers == null) {
            return;
        }
        int vanillaIndex = -1;
        for (int i = 0; i < layers.size(); i++) {
            Object layer = layers.get(i);
            if (layer instanceof CustomArmorLayer) {
                return;
            }
            if (layer instanceof LayerBipedArmor) {
                vanillaIndex = i;
            }
        }
        CustomArmorLayer custom = new CustomArmorLayer(renderer);
        if (vanillaIndex >= 0) {
            layers.set(vanillaIndex, custom);
        } else {
            layers.add(custom);
        }
    }

    private static List getLayers(RendererLivingEntity<?> renderer) {
        for (Field f : RendererLivingEntity.class.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (List) f.get(renderer);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}
