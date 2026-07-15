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
 * Installs {@link CustomArmorLayer} into the player renderers so worn armor is
 * drawn with the protection-level textures. Re-checks periodically because the
 * renderers are rebuilt whenever resources reload (F3+T, resource-pack change),
 * which would otherwise silently drop the layer.
 *
 * The layer itself is always installed; the actual texture swap is gated by
 * CelleConfig.armorSkinsEnabled inside the layer, so toggling the addon takes
 * effect immediately without reinstalling anything.
 */
public class ArmorSkins {

    // NOT WIRED UP: nothing calls this yet. Without it the custom item-icon
    // models (models/item/*_p*_*.json) are never baked, so onModelBake below
    // finds 0 of them and the CustomArmorItemModel wrappers just pass the
    // vanilla model through (harmless). To activate the P-level inventory
    // icons, call this from CelleScannerMod during init (before models bake);
    // to drop the idea, delete this, CustomArmorItemModel and the item jsons.
    public static void registerVariants() {
        try {
            register(net.minecraft.init.Items.diamond_helmet, "diamond", 1, 4, "helmet");
            register(net.minecraft.init.Items.diamond_chestplate, "diamond", 1, 4, "chestplate");
            register(net.minecraft.init.Items.diamond_leggings, "diamond", 1, 4, "leggings");
            register(net.minecraft.init.Items.diamond_boots, "diamond", 1, 4, "boots");

            register(net.minecraft.init.Items.iron_helmet, "iron", 3, 4, "helmet");
            register(net.minecraft.init.Items.iron_chestplate, "iron", 3, 4, "chestplate");
            register(net.minecraft.init.Items.iron_leggings, "iron", 3, 4, "leggings");
            register(net.minecraft.init.Items.iron_boots, "iron", 3, 4, "boots");
        } catch (Throwable t) {
            System.err.println("[CelleScanner] Failed to register item variants: " + t);
        }
    }

    private static void register(net.minecraft.item.Item item, String material, int minLevel, int maxLevel, String type) {
        try {
            net.minecraft.util.ResourceLocation vanillaLoc = net.minecraft.item.Item.itemRegistry.getNameForObject(item);
            if (vanillaLoc == null) return;
            int count = maxLevel - minLevel + 2;
            net.minecraft.util.ResourceLocation[] locs = new net.minecraft.util.ResourceLocation[count];
            locs[0] = vanillaLoc;
            int idx = 1;
            for (int lvl = minLevel; lvl <= maxLevel; lvl++) {
                locs[idx++] = new net.minecraft.util.ResourceLocation("cellescanner", material + "_p" + lvl + "_" + type);
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
            for (String material : materials) {
                int minLevel = "iron".equals(material) ? 3 : 1;
                int maxLevel = 4;
                for (int lvl = minLevel; lvl <= maxLevel; lvl++) {
                    for (String type : items) {
                        String key = "cellescanner:" + material + "_p" + lvl + "_" + type;
                        ModelResourceLocation loc = new ModelResourceLocation(key, "inventory");
                        IBakedModel model = event.modelRegistry.getObject(loc);
                        if (model != null) {
                            CustomArmorItemModel.MODELS.put(key, model);
                            found++;
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
        // ~ every 2 seconds is plenty; installing is cheap and idempotent.
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
                return; // already installed
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

    /**
     * The protected {@code layerRenderers} list on RendererLivingEntity. Found
     * by type (it's the only List field) so it resolves under both the dev MCP
     * names and the obfuscated SRG names at runtime.
     */
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
