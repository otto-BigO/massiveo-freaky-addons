package com.otto.cellescanner;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Drop-in replacement for the vanilla biped armor layer that swaps the worn
 * armor texture based on the Protection enchantment level - the exact same
 * signal the MesterHolm CIT pack keys off (enchant id 0, levels 1-4). Iron
 * P2-P4 and diamond P1-P4 get the bundled MesterHolm textures; anything else
 * (other materials, unenchanted, leather overlays) falls through to vanilla.
 */
public class CustomArmorLayer extends LayerBipedArmor {

    // Protection is enchantment id 0 in 1.8.
    private static final int PROTECTION_ID = 0;

    public static String previewPackOverride = null;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<String, ResourceLocation>();

    public CustomArmorLayer(RendererLivingEntity<?> renderer) {
        super(renderer);
    }

    @Override
    public ResourceLocation getArmorResource(Entity entity, ItemStack stack, int slot, String type) {
        ResourceLocation custom = customFor(stack, slot, type);
        return custom != null ? custom : super.getArmorResource(entity, stack, slot, type);
    }

    private ResourceLocation customFor(ItemStack stack, int slot, String type) {
        if (!CelleScannerMod.config.armorSkinsEnabled || type != null || stack == null) {
            return null;
        }
        String material = materialKey(stack.getItem());
        if (material == null) {
            return null;
        }
        int level = mappedLevel(material, EnchantmentHelper.getEnchantmentLevel(PROTECTION_ID, stack));
        if (level == 0) {
            return null;
        }
        // Vanilla uses layer_2 for leggings (slot 2), layer_1 for the rest.
        int layer = (slot == 2) ? 2 : 1;
        String pack = previewPackOverride != null ? previewPackOverride 
                : ("hypixel".equals(CelleScannerMod.config.armorSkinPack) ? "hypixel" : "mesterholm");
        
        String targetPack = pack;
        String targetMaterial = material;
        int targetLevel = level;

        if ("hypixel".equals(pack)) {
            if ("iron".equals(material)) {
                targetPack = "mesterholm";
            } else if ("diamond".equals(material)) {
                if (level == 1) {
                    targetPack = "mesterholm";
                } else if (level == 2) {
                    targetMaterial = "diamond";
                    targetLevel = 4;
                } else if (level == 3) {
                    targetMaterial = "iron";
                    targetLevel = 4;
                } else if (level == 4) {
                    targetMaterial = "diamond";
                    targetLevel = 2;
                }
            }
        }

        String key = targetPack + "/" + targetMaterial + "_p" + targetLevel + "_layer_" + layer;
        ResourceLocation res = CACHE.get(key);
        if (res == null) {
            res = new ResourceLocation("cellescanner", "textures/models/armor/" + key + ".png");
            CACHE.put(key, res);
        }
        return res;
    }

    private static String materialKey(Item item) {
        if (item == Items.iron_helmet || item == Items.iron_chestplate
                || item == Items.iron_leggings || item == Items.iron_boots) {
            return "iron";
        }
        if (item == Items.diamond_helmet || item == Items.diamond_chestplate
                || item == Items.diamond_leggings || item == Items.diamond_boots) {
            return "diamond";
        }
        return null;
    }

    /** Which bundled skin level applies (0 = none, keep vanilla). */
    private static int mappedLevel(String material, int protection) {
        if ("iron".equals(material)) {
            // The pack only skins iron P2-P4; P0/P1 stay vanilla iron.
            if (protection >= 4) {
                return 4;
            }
            if (protection == 3) {
                return 3;
            }
            if (protection == 2) {
                return 2;
            }
            return 0;
        }
        // diamond: P1-P3 are distinct, P4+ uses the P4 (Vagt) skin.
        if (protection >= 4) {
            return 4;
        }
        return protection; // 1, 2, 3, or 0
    }
}
