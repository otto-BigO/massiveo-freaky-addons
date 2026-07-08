package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Bande ESP addon: draws solid/transparent Chams through walls around players.
 *
 * HOW THIS WORKS:
 * Minecraft's internal RendererLivingEntity.doRender() calls GlStateManager.enableDepth()
 * at the very start of its own rendering logic. This means if we only disable depth in
 * RenderLivingEvent.Pre, Minecraft immediately re-enables it and our Chams state is reset.
 *
 * The correct approach: use RenderWorldLastEvent (fires AFTER all entities are drawn) and
 * render each target player a SECOND TIME ourselves via RenderManager, but with depth testing
 * disabled. This second draw call is not blocked by walls, producing the Chams effect.
 *
 * Green silhouette = bande members.
 * Red silhouette   = other players (when bandeEspAll is enabled).
 */
public class BandeEsp {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.bandeEspEnabled) {
            return;
        }
        if (cfg.bandeMembers.isEmpty() && !cfg.bandeAutoTeam && !cfg.bandeEspAll) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        float partialTicks = event.partialTicks;

        // Interpolated camera position for translating the world matrix
        Entity viewer = mc.getRenderViewEntity();
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        // ---- Set up Chams GL state ----
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GlStateManager.disableDepth();       // Draw through walls
        GlStateManager.depthMask(false);     // Don't write to depth buffer
        GlStateManager.disableLighting();    // Flat color, no light shading
        GlStateManager.disableTexture2D();   // Solid silhouette, no skin texture
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        // ---- Re-render each target player with Chams color ----
        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) continue;

            boolean bande = isBande(mc, p);
            if (!bande && !cfg.bandeEspAll) continue;

            // Interpolated entity position
            double ex = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
            double ey = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
            double ez = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;

            // Apply Chams color (bande = green, others = red)
            if (bande) {
                GlStateManager.color(0.2f, 1.0f, 0.2f, 0.6f);
            } else {
                GlStateManager.color(1.0f, 0.25f, 0.25f, 0.6f);
            }

            // Re-render the player entity at their interpolated position.
            // RenderManager handles the matrix setup + model drawing internally.
            mc.getRenderManager().renderEntityWithPosYaw(
                    p,
                    ex - camX,
                    ey - camY,
                    ez - camZ,
                    p.rotationYaw,
                    partialTicks
            );
        }

        // ---- Restore full GL state ----
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GL11.glPopAttrib();
    }

    private boolean isBande(Minecraft mc, EntityPlayer p) {
        // Auto bande detection is shelved (the hologram read below is unreliable);
        // only the manual member list decides bande membership for now. The
        // bandeTag/bandeName helpers are kept for when we remake the detection.
        return CelleScannerMod.config.isBandeMember(p.getName());
    }

    /**
     * The full bande tag on the hologram line under a player's name (e.g.
     * "Quintero - [24]"), or null. On this server that line is an invisible
     * armor stand with a custom name at the player's position, so we read the
     * closest such armor stand.
     */
    public static String bandeTag(EntityPlayer target) {
        try {
            net.minecraft.world.World w = target.worldObj;
            if (w == null) {
                return null;
            }
            String best = null;
            double bestH = 1.3 * 1.3;
            for (Object o : w.loadedEntityList) {
                if (!(o instanceof EntityArmorStand)) {
                    continue;
                }
                Entity e = (Entity) o;
                if (!e.hasCustomName()) {
                    continue;
                }
                double dx = e.posX - target.posX;
                double dz = e.posZ - target.posZ;
                double h = dx * dx + dz * dz;
                if (h > bestH || Math.abs(e.posY - target.posY) > 3.0) {
                    continue;
                }
                String raw = EnumChatFormatting.getTextWithoutFormattingCodes(e.getCustomNameTag()).trim();
                if (!raw.isEmpty()) {
                    bestH = h;
                    best = raw;
                }
            }
            return best;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Just the bande name from the tag (before " - " or "["), for matching members. */
    public static String bandeName(EntityPlayer target) {
        String tag = bandeTag(target);
        if (tag == null) {
            return null;
        }
        int dash = tag.indexOf(" - ");
        String b = dash >= 0 ? tag.substring(0, dash) : tag;
        int br = b.indexOf('[');
        if (br > 0) {
            b = b.substring(0, br);
        }
        b = b.trim();
        return b.isEmpty() ? null : b;
    }
}
