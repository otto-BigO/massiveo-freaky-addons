package com.otto.cellescanner;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraftforge.client.model.ISmartItemModel;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.init.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smart Item Model wrapper for armor inventory icons.
 */
public class CustomArmorItemModel implements ISmartItemModel {

    private final IBakedModel baseModel;
    private final Item item;
    
    public static final Map<String, IBakedModel> MODELS = new HashMap<String, IBakedModel>();

    public CustomArmorItemModel(IBakedModel baseModel, Item item) {
        this.baseModel = baseModel;
        this.item = item;
    }

    @Override
    public IBakedModel handleItemState(ItemStack stack) {
        if (!MassiveOsFreakyAddons.config.armorSkinsEnabled || stack == null) {
            return baseModel;
        }

        if (item == Items.golden_helmet) {
            return baseModel;
        }

        String material = materialKey(item);
        if (material == null) {
            return baseModel;
        }

        int level = mappedLevel(material, ArmorProtection.level(stack));
        if (level == 0) {
            return baseModel;
        }

        String type = itemType(item);
        if (type == null) {
            return baseModel;
        }

        String key = "cellescanner:" + activePack() + "_" + material + "_p" + level + "_" + type;
        IBakedModel custom = MODELS.get(key);
        return custom != null ? custom : baseModel;
    }

    /** Inventory icons follow the same texture pack as the worn armor. */
    private static String activePack() {
        return "hypixel".equals(MassiveOsFreakyAddons.config.armorSkinPack) ? "hypixel" : "mesterholm";
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

    private static String itemType(Item item) {
        if (item == Items.iron_helmet || item == Items.diamond_helmet) return "helmet";
        if (item == Items.iron_chestplate || item == Items.diamond_chestplate) return "chestplate";
        if (item == Items.iron_leggings || item == Items.diamond_leggings) return "leggings";
        if (item == Items.iron_boots || item == Items.diamond_boots) return "boots";
        return null;
    }

    private static int mappedLevel(String material, int protection) {
        if ("iron".equals(material)) {
            if (protection >= 4) return 4;
            if (protection == 3) return 3;
            if (protection == 2) return 2;
            return 0;
        }
        if ("diamond".equals(material)) {
            if (protection >= 4) return 4;
            return protection;
        }
        return 0;
    }

    @Override public List<BakedQuad> getGeneralQuads() { return baseModel.getGeneralQuads(); }
    @Override public List<BakedQuad> getFaceQuads(EnumFacing facing) { return baseModel.getFaceQuads(facing); }
    @Override public boolean isAmbientOcclusion() { return baseModel.isAmbientOcclusion(); }
    @Override public boolean isGui3d() { return baseModel.isGui3d(); }
    @Override public boolean isBuiltInRenderer() { return baseModel.isBuiltInRenderer(); }
    @Override public TextureAtlasSprite getParticleTexture() { return baseModel.getParticleTexture(); }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return baseModel.getItemCameraTransforms(); }
}
