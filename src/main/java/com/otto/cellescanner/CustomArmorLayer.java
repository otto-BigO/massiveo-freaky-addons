package com.otto.cellescanner;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Drop-in replacement for the vanilla biped armor layer.
 * Swaps worn armor textures based on protection enchantment level & item type:
 * - Golden Helmet (enchanted) -> Divan helmet on head.
 * - Iron P2 -> Mineral armor (helmet from the SkyBlock Mineral Helmet skull).
 * - Iron P3 -> Unstable Dragon armor (helmet from the Unstable Dragon Helmet skull).
 * - Iron P4 -> Tank Wither armor.
 * - Diamond P1 -> Vanguard armor.
 * - Diamond P2 -> Rampart armor.
 * - Diamond P3 -> Speed Wither armor.
 * - Diamond P4 -> Shadow Assassin armor.
 */
public class CustomArmorLayer extends LayerBipedArmor {

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
        if (!MassiveOsFreakyAddons.config.armorSkinsEnabled || type != null || stack == null) {
            return null;
        }

        Item item = stack.getItem();

        // 1. Golden Helmet with ANY enchantment -> Divan helmet (only for head slot)
        if (item == Items.golden_helmet) {
            if (slot == 4 && ArmorProtection.isEnchanted(stack)) {
                return getCachedResource(activePack() + "/gold_helmet_layer_1");
            }
            return null;
        }

        String material = materialKey(item);
        if (material == null) {
            return null;
        }

        int level = mappedLevel(material, ArmorProtection.level(stack));
        if (level == 0) {
            return null;
        }

        // Layer 2 for leggings (slot 2), Layer 1 for helmet/chestplate/boots.
        int layer = (slot == 2) ? 2 : 1;

        String key = activePack() + "/" + material + "_p" + level + "_layer_" + layer;
        return getCachedResource(key);
    }

    private static String activePack() {
        if (previewPackOverride != null) {
            return previewPackOverride;
        }
        return "hypixel".equals(MassiveOsFreakyAddons.config.armorSkinPack) ? "hypixel" : "mesterholm";
    }

    private static ResourceLocation getCachedResource(String key) {
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

    private static int mappedLevel(String material, int protection) {
        if ("iron".equals(material)) {
            if (protection >= 4) return 4;
            if (protection == 3) return 3;
            if (protection == 2) return 2;
            return 0;
        }
        if (protection >= 4) {
            return 4;
        }
        return protection;
    }
}
