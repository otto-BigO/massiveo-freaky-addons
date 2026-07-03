package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Bande ESP addon: draws players in your bande as a "chams" - a green-tinted
 * copy of their own model, visible through walls, so you see their skin/shape
 * and can pick them out anywhere. Membership comes from the manual name list
 * (CelleConfig.bandeMembers), optionally plus anyone sharing your bande tag when
 * bandeAutoTeam is on.
 *
 * It re-renders each bande player's model in RenderWorldLast with depth off (the
 * same pass the box ESP used, which reliably renders through walls even under
 * LabyMod - unlike toggling depth inside the entity render itself).
 */
public class BandeEsp {

    // Green tint - clearly visible but light enough to read the skin under it.
    private static final float TINT_R = 0.5f;
    private static final float TINT_G = 1.0f;
    private static final float TINT_B = 0.6f;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.bandeEspEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        float pt = event.partialTicks;
        Entity viewer = mc.thePlayer;
        double camX = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double camY = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double camZ = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

        RenderManager rm = mc.getRenderManager();
        rm.setRenderShadow(false);

        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || !isBande(mc, p)) {
                continue;
            }
            double x = (p.lastTickPosX + (p.posX - p.lastTickPosX) * pt) - camX;
            double y = (p.lastTickPosY + (p.posY - p.lastTickPosY) * pt) - camY;
            double z = (p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt) - camZ;

            GlStateManager.pushMatrix();
            try {
                GlStateManager.disableDepth();       // through walls
                GlStateManager.disableLighting();    // so the tint applies (and it's full-bright)
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
                GlStateManager.color(TINT_R, TINT_G, TINT_B, 1.0F);
                rm.renderEntityWithPosYaw(p, x, y, z, p.rotationYaw, pt);
            } catch (Throwable ignored) {
                // A bad render must not take down the whole world-render pass.
            } finally {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableLighting();
                GlStateManager.enableDepth();
                GlStateManager.popMatrix();
            }
        }

        rm.setRenderShadow(true);
    }

    private boolean isBande(Minecraft mc, EntityPlayer p) {
        if (CelleScannerMod.config.isBandeMember(p.getName())) {
            return true;
        }
        if (CelleScannerMod.config.bandeAutoTeam) {
            String mine = bandeTag(mc.thePlayer);
            String theirs = bandeTag(p);
            if (mine != null && mine.equals(theirs)) {
                return true;
            }
        }
        return false;
    }

    private static String bandeTag(EntityPlayer player) {
        try {
            Team t = player.getTeam();
            if (t == null) {
                return null;
            }
            String tag = EnumChatFormatting.getTextWithoutFormattingCodes(t.formatString("")).trim();
            return tag.isEmpty() ? null : tag;
        } catch (Throwable e) {
            return null;
        }
    }
}
