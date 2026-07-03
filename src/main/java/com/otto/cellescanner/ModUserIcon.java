package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Draws a small badge just before a player's floating nametag, marking other
 * mod users (like Lunar/LabyMod). Testing stage: it draws a purple circle before
 * every other player's name so the rendering can be checked. Real "who is
 * running the mod" detection (a shared registry) comes next; when it does, only
 * {@link #isModUser} needs to change.
 */
public class ModUserIcon {

    private static final float NAME_SCALE = 0.025F;
    private static final double MAX_DIST = 48.0;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!CelleScannerMod.config.modIconEnabled) {
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
        FontRenderer fr = mc.fontRendererObj;

        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || p.isInvisible() || !isModUser(p)) {
                continue;
            }
            if (p.getDistanceSqToEntity(viewer) > MAX_DIST * MAX_DIST) {
                continue;
            }
            double x = (p.lastTickPosX + (p.posX - p.lastTickPosX) * pt) - camX;
            double y = (p.lastTickPosY + (p.posY - p.lastTickPosY) * pt) - camY;
            double z = (p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt) - camZ;
            drawBadge(rm, fr, p, x, y, z);
        }
    }

    /** Testing: everyone counts. Later this checks the registry of real mod users. */
    private boolean isModUser(EntityPlayer p) {
        return true;
    }

    private void drawBadge(RenderManager rm, FontRenderer fr, EntityPlayer p, double x, double y, double z) {
        String name = p.getDisplayName() != null ? p.getDisplayName().getFormattedText() : p.getName();
        int halfWidth = fr.getStringWidth(name) / 2;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) (y + p.height + 0.5), (float) z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-NAME_SCALE, -NAME_SCALE, NAME_SCALE);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();

        float cx = -halfWidth - 6f; // just left of the name
        float cy = 4f;              // roughly vertically centred on the text
        float r = 4f;
        GlStateManager.color(0.62f, 0.20f, 0.92f, 1.0f); // purple
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(cx, cy);
        for (int a = 0; a <= 360; a += 24) {
            double rad = Math.toRadians(a);
            GL11.glVertex2f(cx + (float) Math.cos(rad) * r, cy + (float) Math.sin(rad) * r);
        }
        GL11.glEnd();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}
