package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
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
