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

import java.util.ArrayList;
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

    /**
     * Reused between frames. The visible set was previously recomputed inline in
     * both render passes, so every celle had its distance taken twice per frame,
     * each with a square root.
     */
    private final List<Celle> visible = new ArrayList<Celle>();

    /** Celler picked in the Celle Buyer, resolved to positions in this dimension. */
    private final List<BlockPos> picked = new ArrayList<BlockPos>();
    private final List<String> pickedIds = new ArrayList<String>();

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // The buyer's picks draw on their own terms. They are a short list the
        // player typed in by hand, so they are worth seeing whether or not the
        // general celle ESP happens to be switched on.
        collectPicked(mc, cfg);

        boolean espOn = cfg.enabled && cfg.espEnabled;
        if (!espOn && picked.isEmpty()) {
            return;
        }

        List<Celle> entries = espOn ? CelleFilter.collectUpcoming() : java.util.Collections.<Celle>emptyList();

        // Resolve the finder target's position (if any, and if it's in this
        // dimension) up front - this can be non-null even when "entries" is
        // empty, since a finder target isn't limited to the hour window.
        BlockPos finderPos = null;
        if (espOn && CelleFinder.hasTarget()) {
            CellePositions.Entry p = CelleFinder.getTargetPosition();
            if (p != null && mc.theWorld.provider.getDimensionId() == p.dimension) {
                finderPos = new BlockPos(p.x, p.y, p.z);
            }
        }

        if (entries.isEmpty() && finderPos == null && picked.isEmpty()) {
            return;
        }

        Entity viewer = MassiveOsFreakyAddons.renderViewer();
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
        double maxDist = MassiveOsFreakyAddons.config.espMaxDistance;
        boolean limitDistance = maxDist > 0;
        double maxDistSq = maxDist * maxDist;

        visible.clear();
        for (Celle c : entries) {
            if (limitDistance && distanceSqTo(px, py, pz, c) > maxDistSq) {
                continue;
            }
            visible.add(c);
        }

        // pass 1: box outlines (no texture needed)
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);
        for (int i = 0; i < visible.size(); i++) {
            Celle c = visible.get(i);
            float[] col = colorFor(c);
            drawBoxOutline(c.position, col[0], col[1], col[2], 0.9f);
        }
        if (finderPos != null) {
            // Thicker line + fully opaque so it stands out from the normal
            // upcoming-list boxes, and deliberately ignores maxDist/limitDistance.
            GL11.glLineWidth(4.0f);
            drawBoxOutline(finderPos, 0.95f, 0.95f, 0.95f, 1.0f);
            GL11.glLineWidth(2.5f);
        }
        // The buyer's picks, in a cycling rainbow so they are impossible to
        // confuse with the green/amber status boxes. Also ignores maxDist.
        if (!picked.isEmpty()) {
            GL11.glLineWidth(4.0f);
            for (int i = 0; i < picked.size(); i++) {
                drawRainbowBox(picked.get(i));
            }
            GL11.glLineWidth(2.5f);
        }
        GlStateManager.enableTexture2D();

        // pass 2: floating celle-id labels (need texture for the font)
        if (MassiveOsFreakyAddons.config.espLabels) {
            FontRenderer fr = mc.fontRendererObj;
            RenderManager rm = mc.getRenderManager();
            for (int i = 0; i < visible.size(); i++) {
                Celle c = visible.get(i);
                float[] col = colorFor(c);
                int color = ((int) (col[0] * 255) << 16) | ((int) (col[1] * 255) << 8) | (int) (col[2] * 255);
                String label = c.timerConfirmed ? c.celleId : "~" + c.celleId;
                drawLabel(fr, rm, label, c.position.getX() + 0.5, c.position.getY() + 1.4, c.position.getZ() + 0.5, color);
            }
            if (finderPos != null) {
                String label = "-> " + CelleFinder.getTarget();
                drawLabel(fr, rm, label, finderPos.getX() + 0.5, finderPos.getY() + 1.6, finderPos.getZ() + 0.5, 0xF0F0F0);
            }
        }
        // Picks get their label whether or not espLabels is on, since a rainbow
        // box with no id on it is not much use when several are picked.
        if (!picked.isEmpty()) {
            FontRenderer fr = mc.fontRendererObj;
            RenderManager rm = mc.getRenderManager();
            for (int i = 0; i < picked.size(); i++) {
                BlockPos p = picked.get(i);
                drawLabel(fr, rm, "* " + pickedIds.get(i),
                        p.getX() + 0.5, p.getY() + 1.6, p.getZ() + 0.5,
                        rainbowColor(hueFor(p, 0)));
            }
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Squared distance, so the range test avoids a square root per celle. */
    private static double distanceSqTo(double px, double py, double pz, Celle c) {
        double dx = px - (c.position.getX() + 0.5);
        double dy = py - (c.position.getY() + 0.5);
        double dz = pz - (c.position.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceTo(double px, double py, double pz, Celle c) {
        double dx = px - (c.position.getX() + 0.5);
        double dy = py - (c.position.getY() + 0.5);
        double dz = pz - (c.position.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // Shared, read-only - returned from colorFor so the per-celle render loop
    // doesn't allocate a fresh array for every box, every frame.
    // Harmonised ESP palette, distinct per meaning: aqua = available now,
    // amber = about to free up. (Bande = green, your celler = violet, finder =
    // white - kept different so the overlays don't all look alike.)
    private static final float[] COLOR_TIL_SALG = {0.20f, 1.00f, 0.30f};
    private static final float[] COLOR_SOLGT = {1.0f, 0.66f, 0.28f};

    private static float[] colorFor(Celle c) {
        return c.status == CelleStatus.TIL_SALG ? COLOR_TIL_SALG : COLOR_SOLGT;
    }

    /**
     * Resolves the buyer's picked ids to positions in this dimension. They come
     * from CellePositions rather than the live scan cache, so a pick stays boxed
     * once it has been seen even if you walk out of scan range of the sign.
     */
    private void collectPicked(Minecraft mc, CelleConfig cfg) {
        picked.clear();
        pickedIds.clear();
        if (!cfg.celleBuyerEnabled || cfg.celleBuyerWhitelist == null
                || cfg.celleBuyerWhitelist.isEmpty()) {
            return;
        }
        int dim = mc.theWorld.provider.getDimensionId();
        for (int i = 0; i < cfg.celleBuyerWhitelist.size(); i++) {
            String id = cfg.celleBuyerWhitelist.get(i);
            if (id == null || id.isEmpty()) {
                continue;
            }
            CellePositions.Entry e = CellePositions.get(id);
            if (e == null) {
                // Never scanned, so there is nothing to draw a box around yet.
                continue;
            }
            if (e.dimension != dim) {
                continue;
            }
            picked.add(new BlockPos(e.x, e.y, e.z));
            pickedIds.add(id);
        }
    }

    /**
     * Hue for a picked box, cycling once every few seconds. Offset by position so
     * two picks side by side are never the same colour at the same moment, and by
     * vertex index so the colour runs around the box rather than flashing it.
     */
    private static float hueFor(BlockPos pos, int vertex) {
        float t = (System.currentTimeMillis() % 3000L) / 3000.0f;
        float spatial = ((pos.getX() * 31 + pos.getZ() * 17) % 100) / 100.0f;
        float h = t + spatial + vertex * 0.07f;
        return h - (float) Math.floor(h);
    }

    /** Fully saturated HSV to RGB, avoiding a java.awt dependency for three lines. */
    private static float[] hsvToRgb(float h, float[] out) {
        float i = (float) Math.floor(h * 6.0f);
        float f = h * 6.0f - i;
        float q = 1.0f - f;
        switch ((int) i % 6) {
            case 0: out[0] = 1f; out[1] = f;  out[2] = 0f; break;
            case 1: out[0] = q;  out[1] = 1f; out[2] = 0f; break;
            case 2: out[0] = 0f; out[1] = 1f; out[2] = f;  break;
            case 3: out[0] = 0f; out[1] = q;  out[2] = 1f; break;
            case 4: out[0] = f;  out[1] = 0f; out[2] = 1f; break;
            default: out[0] = 1f; out[1] = 0f; out[2] = q; break;
        }
        return out;
    }

    private static final float[] RAINBOW_SCRATCH = new float[3];

    private static int rainbowColor(float hue) {
        float[] c = hsvToRgb(hue, RAINBOW_SCRATCH);
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    private static void rainbowVertex(BlockPos pos, int vertex, double x, double y, double z) {
        float[] c = hsvToRgb(hueFor(pos, vertex), RAINBOW_SCRATCH);
        GlStateManager.color(c[0], c[1], c[2], 1.0f);
        GL11.glVertex3d(x, y, z);
    }

    /**
     * Same wireframe as the status boxes, but coloured per vertex so the rainbow
     * runs around the edges and drifts over time.
     */
    private void drawRainbowBox(BlockPos pos) {
        double pad = PAD + 0.03;
        double minX = pos.getX() - pad;
        double minY = pos.getY() - pad;
        double minZ = pos.getZ() - pad;
        double maxX = pos.getX() + 1 + pad;
        double maxY = pos.getY() + 1 + pad;
        double maxZ = pos.getZ() + 1 + pad;

        GL11.glBegin(GL11.GL_LINE_LOOP);
        rainbowVertex(pos, 0, minX, minY, minZ);
        rainbowVertex(pos, 1, maxX, minY, minZ);
        rainbowVertex(pos, 2, maxX, minY, maxZ);
        rainbowVertex(pos, 3, minX, minY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        rainbowVertex(pos, 4, minX, maxY, minZ);
        rainbowVertex(pos, 5, maxX, maxY, minZ);
        rainbowVertex(pos, 6, maxX, maxY, maxZ);
        rainbowVertex(pos, 7, minX, maxY, maxZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        rainbowVertex(pos, 0, minX, minY, minZ);
        rainbowVertex(pos, 4, minX, maxY, minZ);
        rainbowVertex(pos, 1, maxX, minY, minZ);
        rainbowVertex(pos, 5, maxX, maxY, minZ);
        rainbowVertex(pos, 2, maxX, minY, maxZ);
        rainbowVertex(pos, 6, maxX, maxY, maxZ);
        rainbowVertex(pos, 3, minX, minY, maxZ);
        rainbowVertex(pos, 7, minX, maxY, maxZ);
        GL11.glEnd();
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
