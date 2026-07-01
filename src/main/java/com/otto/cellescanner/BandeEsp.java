package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Bande ESP addon: draws a green wireframe box, visible through walls, around
 * every loaded player who is in your bande. Membership comes from the manual
 * name list in CelleConfig.bandeMembers (reliable on any server), optionally
 * plus anyone on your own scoreboard team when bandeAutoTeam is on.
 */
public class BandeEsp {

    private static final double PAD = 0.06;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.bandeEspEnabled) {
            return;
        }
        if (cfg.bandeMembers.isEmpty() && !cfg.bandeAutoTeam) {
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
            if (p == mc.thePlayer || !isBande(mc, p)) {
                continue;
            }
            drawBox(p, partialTicks);
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private boolean isBande(Minecraft mc, EntityPlayer p) {
        if (CelleScannerMod.config.isBandeMember(p.getName())) {
            return true;
        }
        if (CelleScannerMod.config.bandeAutoTeam) {
            Team mine = mc.thePlayer.getTeam();
            Team theirs = p.getTeam();
            if (mine != null && theirs != null && mine.isSameTeam(theirs)) {
                return true;
            }
        }
        return false;
    }

    private void drawBox(EntityPlayer p, float partialTicks) {
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

        GL11.glColor4f(0.2f, 1.0f, 0.2f, 0.9f);

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
}
