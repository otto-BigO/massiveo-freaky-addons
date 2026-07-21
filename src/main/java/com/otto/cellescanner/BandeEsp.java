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

    // Box hugs the player's hitbox tightly (was 0.06, which drew a noticeably
    // baggy box standing off the model). 0.0 = flush with the hitbox edges.
    private static final double PAD = 0.0;

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
        Entity viewer = mc.thePlayer;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) {
                continue;
            }

            boolean friend = isFriend(mc, p);
            boolean bande = isBande(mc, p);
            if (!friend && !bande && !cfg.bandeEspAll) {
                continue;
            }

            if (friend) {
                drawBox(p, partialTicks, 0.2f, 0.6f, 1.0f);   // friend = cyan/blue
            } else if (bande) {
                drawBox(p, partialTicks, 0.2f, 1.0f, 0.2f);   // bande = green
            } else {
                drawBox(p, partialTicks, 1.0f, 0.25f, 0.25f); // everyone else = red
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawBox(EntityPlayer p, float partialTicks, float r, float g, float b) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;

        double w = p.width / 2.0 + PAD;
        double minX = x - w;
        double maxX = x + w;
        double minY = y - PAD;
        double maxY = y + p.height + PAD;
        double minZ = z - w;
        double maxZ = z + w;

        GlStateManager.color(r, g, b, 0.9f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glEnd();
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

    private boolean isFriend(Minecraft mc, EntityPlayer p) {
        if (p == null || p.getName() == null) {
            return false;
        }
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.friendEspEnabled) {
            return false;
        }
        for (String friend : cfg.friendsList) {
            if (friend != null && friend.equalsIgnoreCase(p.getName())) {
                return true;
            }
        }
        return false;
    }
}
