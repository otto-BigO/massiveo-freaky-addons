package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Draws a wireframe box around the sign of every "upcoming" celle (same
 * set as the HUD - see CelleFilter), with depth testing disabled so it's
 * visible through walls. Optionally also floats the celle ID above each
 * box as a nametag-style label (billboarded to face the camera, dark
 * translucent backing - same technique vanilla uses for player/mob
 * nametags) so multiple nearby boxes can be told apart at a glance.
 *
 * Green box/text  = TIL_SALG, already available now.
 * Orange box/text = SOLGT, about to become available.
 * Cyan box/text   = the active Celle Finder target (see CelleFinder) -
 *                    drawn regardless of the hour window or espMaxDistance,
 *                    since the whole point is helping you physically
 *                    relocate a specific celle you already know the id of.
 */
public class CelleEsp {

    private static final double PAD = 0.02;
    private static final float LABEL_SCALE = 0.035F;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!CelleScannerMod.config.enabled || !CelleScannerMod.config.espEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        List<Celle> entries = CelleFilter.collectUpcoming();

        // Resolve the finder target's position (if any, and if it's in this
        // dimension) up front - this can be non-null even when "entries" is
        // empty, since a finder target isn't limited to the hour window.
        BlockPos finderPos = null;
        if (CelleFinder.hasTarget()) {
            CellePositions.Entry p = CelleFinder.getTargetPosition();
            if (p != null && mc.theWorld.provider.getDimensionId() == p.dimension) {
                finderPos = new BlockPos(p.x, p.y, p.z);
            }
        }

        if (entries.isEmpty() && finderPos == null) {
            return;
        }

        Entity viewer = mc.thePlayer;
        float partialTicks = event.partialTicks;
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

        // Cap how far out we bother drawing at all. With everything rendered
        // through walls and no depth sorting, a screen full of distant boxes
        // and labels stacked on top of each other is what makes it "unclear"
        // when several celler sit in the same area - keeping only the
        // genuinely nearby ones legible matters more than seeing all of them.
        double maxDist = CelleScannerMod.config.espMaxDistance;
        boolean limitDistance = maxDist > 0;

        // pass 1: box outlines (no texture needed)
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);
        for (Celle c : entries) {
            if (limitDistance && distanceTo(px, py, pz, c) > maxDist) {
                continue;
            }
            float[] col = colorFor(c);
            drawBoxOutline(c.position, col[0], col[1], col[2], 0.9f);
        }
        if (finderPos != null) {
            // Thicker line + fully opaque so it stands out from the normal
            // upcoming-list boxes, and deliberately ignores maxDist/limitDistance.
            GL11.glLineWidth(4.0f);
            drawBoxOutline(finderPos, 0.2f, 1.0f, 1.0f, 1.0f);
            GL11.glLineWidth(2.5f);
        }
        GlStateManager.enableTexture2D();

        // pass 2: floating celle-id labels (need texture for the font)
        if (CelleScannerMod.config.espLabels) {
            FontRenderer fr = mc.fontRendererObj;
            RenderManager rm = mc.getRenderManager();
            for (Celle c : entries) {
                if (limitDistance && distanceTo(px, py, pz, c) > maxDist) {
                    continue;
                }
                float[] col = colorFor(c);
                int color = ((int) (col[0] * 255) << 16) | ((int) (col[1] * 255) << 8) | (int) (col[2] * 255);
                String label = c.timerConfirmed ? c.celleId : "~" + c.celleId;
                drawLabel(fr, rm, label, c.position.getX() + 0.5, c.position.getY() + 1.4, c.position.getZ() + 0.5, color);
            }
            if (finderPos != null) {
                String label = "-> " + CelleFinder.getTarget();
                drawLabel(fr, rm, label, finderPos.getX() + 0.5, finderPos.getY() + 1.6, finderPos.getZ() + 0.5, 0x33FFFF);
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private static double distanceTo(double px, double py, double pz, Celle c) {
        double dx = px - (c.position.getX() + 0.5);
        double dy = py - (c.position.getY() + 0.5);
        double dz = pz - (c.position.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // Shared, read-only - returned from colorFor so the per-celle render loop
    // doesn't allocate a fresh array for every box, every frame.
    private static final float[] COLOR_TIL_SALG = {0.2f, 1.0f, 0.2f};
    private static final float[] COLOR_SOLGT = {1.0f, 0.6f, 0.0f};

    private static float[] colorFor(Celle c) {
        return c.status == CelleStatus.TIL_SALG ? COLOR_TIL_SALG : COLOR_SOLGT;
    }

    private void drawBoxOutline(BlockPos pos, float r, float g, float b, float a) {
        double minX = pos.getX() - PAD;
        double minY = pos.getY() - PAD;
        double minZ = pos.getZ() - PAD;
        double maxX = pos.getX() + 1 + PAD;
        double maxY = pos.getY() + 1 + PAD;
        double maxZ = pos.getZ() + 1 + PAD;

        GlStateManager.color(r, g, b, a);

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

    /**
     * Same approach as vanilla's Render.renderLivingLabel: translate to the
     * world-space anchor point, rotate to face the camera (yaw then pitch),
     * scale down into "pixel" space, then draw a flat background quad plus
     * the text - all with depth disabled so it reads through walls too.
     */
    private void drawLabel(FontRenderer fr, RenderManager rm, String text, double x, double y, double z, int textColor) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        // Re-assert depth-disabled right at the draw call rather than only
        // once at the top of onRenderWorldLast - cheap insurance in case
        // anything between the box pass and here ever touches it, and the
        // most likely reason the labels were reading as "unclear"/blocked
        // by walls while the plain GL11 line boxes worked fine.
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        int halfWidth = fr.getStringWidth(text) / 2;

        // Bigger, more opaque backing card (was 0.4 alpha) - through two or
        // three layers of wall texture a faint card plus small text was
        // genuinely hard to pick out, especially with several celler
        // overlapping in the same area.
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.0F, 0.0F, 0.0F, 0.65F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2d(-halfWidth - 3, -2);
        GL11.glVertex2d(-halfWidth - 3, 10);
        GL11.glVertex2d(halfWidth + 3, 10);
        GL11.glVertex2d(halfWidth + 3, -2);
        GL11.glEnd();
        GlStateManager.enableTexture2D();

        // Reset tint to opaque white before drawing text - drawString sets
        // its own color from textColor, but doing this explicitly avoids
        // any chance of the black backing-card color bleeding through.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        fr.drawString(text, -halfWidth, 0, textColor, true);

        GlStateManager.popMatrix();
    }
}
