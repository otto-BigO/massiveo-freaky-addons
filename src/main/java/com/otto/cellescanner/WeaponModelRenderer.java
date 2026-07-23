package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.resources.model.IBakedModel;
import org.lwjgl.opengl.GL11;

/**
 * Renders a 3D weapon model inside a GUI rect with smooth spinning (Y-axis rotation).
 */
public final class WeaponModelRenderer {

    private WeaponModelRenderer() {
    }

    /**
     * Renders a spinning 3D weapon model centered at (x, y) with specified scale.
     */
    public static void renderSpinningWeapon(Minecraft mc, MajesticaWeapons.Weapon weapon, int x, int y, float scale, float rotSpeed) {
        if (weapon == null) {
            return;
        }
        IBakedModel model = MajesticaWeapons.INSTANCE.getBakedModel(weapon);
        if (model == null) {
            return;
        }

        long now = System.currentTimeMillis();
        float yaw = (now % 3600L) / 3600.0f * 360.0f * rotSpeed; // smooth continuous 360 rotation

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 150.0F);
        GlStateManager.scale(1.0F, -1.0F, 1.0F);
        GlStateManager.scale(scale, scale, scale);

        // Standard 3D GUI item lighting
        RenderHelper.enableGUIStandardItemLighting();

        GlStateManager.rotate(15.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        // Bind texture atlas
        mc.getTextureManager().bindTexture(net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture);

        // Render the baked 3D item model with GUI camera transforms
        try {
            mc.getRenderItem().renderItem(new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond_sword), model);
        } catch (Throwable ignored) {
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
    }
}
