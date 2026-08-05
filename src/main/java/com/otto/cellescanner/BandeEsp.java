package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector4f;

import java.nio.FloatBuffer;

/**
 * Meteor-Style Player ESP addon.
 *
 * Supports 4 Modes:
 * 1. 2D Box (Clean 2D screen overlay box projected from entity bounds)
 * 2. Corners (Meteor-style bracket corners ┌ ┐ └ ┘)
 * 3. 3D Box (3D wireframe bounding box through walls)
 * 4. Outline (3D Player Model wireframe contour outline via RenderPlayerEvent)
 *
 * Custom ESP Colors from CelleConfig:
 * - Blue  : Bande Members & Friends
 * - Green : Vagter (Prison Guards)
 * - Red   : Other Players
 */
public class BandeEsp {

    private static final FloatBuffer MODEL_MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJ_MATRIX = BufferUtils.createFloatBuffer(16);
    private static final int[] VIEWPORT = new int[4];

    /**
     * Whether the Pre handler actually changed GL state for the player currently
     * being drawn.
     *
     * Pre bails out before pushing anything for a player who is not highlighted,
     * but Post used to pop regardless. Popping a matrix that was never pushed
     * underflows the stack, and from that point the whole world renders against
     * the wrong matrix. With outline mode on and one unhighlighted player in
     * sight, that was every frame.
     */
    private boolean outlineStatePushed = false;

    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.bandeEspEnabled) return;

        String mode = cfg.bandeEspMode != null ? cfg.bandeEspMode : "2D";
        if (!mode.equalsIgnoreCase("Outline")) return;

        EntityPlayer p = event.entityPlayer;
        Minecraft mc = Minecraft.getMinecraft();
        if (p == null || p == mc.thePlayer) return;

        boolean friend = isFriend(mc, p);
        boolean bande = isBande(mc, p);
        boolean vagt = isVagt(mc, p);

        if (!friend && !bande && !vagt && !cfg.bandeEspAll) return;

        float r, g, b;
        if (friend || bande) {
            r = ((cfg.espColorBande >> 16) & 0xFF) / 255f;
            g = ((cfg.espColorBande >> 8) & 0xFF) / 255f;
            b = (cfg.espColorBande & 0xFF) / 255f;
        } else if (vagt) {
            r = ((cfg.espColorVagt >> 16) & 0xFF) / 255f;
            g = ((cfg.espColorVagt >> 8) & 0xFF) / 255f;
            b = (cfg.espColorVagt & 0xFF) / 255f;
        } else {
            r = ((cfg.espColorOther >> 16) & 0xFF) / 255f;
            g = ((cfg.espColorOther >> 8) & 0xFF) / 255f;
            b = (cfg.espColorOther & 0xFF) / 255f;
        }

        GlStateManager.pushMatrix();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        GL11.glLineWidth(2.5f);
        GlStateManager.color(r, g, b, 1.0f);
        outlineStatePushed = true;
    }

    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.bandeEspEnabled) return;

        String mode = cfg.bandeEspMode != null ? cfg.bandeEspMode : "2D";
        if (!mode.equalsIgnoreCase("Outline")) return;

        EntityPlayer p = event.entityPlayer;
        Minecraft mc = Minecraft.getMinecraft();
        if (p == null || p == mc.thePlayer) return;

        // Only undo what Pre actually did. Restoring state that was never
        // changed pops a matrix nobody pushed, which corrupts every draw after it.
        if (!outlineStatePushed) return;
        outlineStatePushed = false;

        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.bandeEspEnabled) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        float partialTicks = event.partialTicks;
        Entity viewer = mc.thePlayer;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        String mode = cfg.bandeEspMode != null ? cfg.bandeEspMode : "2D";
        if (mode.equalsIgnoreCase("Outline")) return; // Rendered via RenderPlayerEvent above

        if (mode.equalsIgnoreCase("2D") || mode.equalsIgnoreCase("Corners")) {
            MODEL_MATRIX.rewind();
            PROJ_MATRIX.rewind();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_MATRIX);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ_MATRIX);
            MODEL_MATRIX.rewind();
            PROJ_MATRIX.rewind();
            VIEWPORT[0] = 0;
            VIEWPORT[1] = 0;
            VIEWPORT[2] = mc.displayWidth;
            VIEWPORT[3] = mc.displayHeight;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        for (Object obj : mc.theWorld.playerEntities) {
            if (!(obj instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) obj;
            if (p == mc.thePlayer) continue;

            boolean friend = isFriend(mc, p);
            boolean bande = isBande(mc, p);
            boolean vagt = isVagt(mc, p);

            if (!friend && !bande && !vagt && !cfg.bandeEspAll) continue;

            float r, g, b;
            if (friend || bande) {
                r = ((cfg.espColorBande >> 16) & 0xFF) / 255f;
                g = ((cfg.espColorBande >> 8) & 0xFF) / 255f;
                b = (cfg.espColorBande & 0xFF) / 255f;
            } else if (vagt) {
                r = ((cfg.espColorVagt >> 16) & 0xFF) / 255f;
                g = ((cfg.espColorVagt >> 8) & 0xFF) / 255f;
                b = (cfg.espColorVagt & 0xFF) / 255f;
            } else {
                r = ((cfg.espColorOther >> 16) & 0xFF) / 255f;
                g = ((cfg.espColorOther >> 8) & 0xFF) / 255f;
                b = (cfg.espColorOther & 0xFF) / 255f;
            }

            if (mode.equalsIgnoreCase("3D")) {
                draw3DBox(p, partialTicks, r, g, b);
            } else if (mode.equalsIgnoreCase("Corners")) {
                draw2DBrackets(mc, p, partialTicks, r, g, b);
            } else {
                draw2DBox(mc, p, partialTicks, r, g, b);
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

    private void draw3DBox(EntityPlayer p, float partialTicks, float r, float g, float b) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks;

        double w = p.width / 2.0;
        double minX = x - w;
        double maxX = x + w;
        double minY = y;
        double maxY = y + p.height;
        double minZ = z - w;
        double maxZ = z + w;

        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);
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

    private void draw2DBox(Minecraft mc, EntityPlayer p, float partialTicks, float r, float g, float b) {
        float[] screen = getScreenBounds(mc, p, partialTicks);
        if (screen == null) return;

        float minX = screen[0];
        float minY = screen[1];
        float maxX = screen[2];
        float maxY = screen[3];

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, mc.displayWidth, mc.displayHeight, 0, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.0f);
        GlStateManager.color(r, g, b, 0.95f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(minX, minY);
        GL11.glVertex2f(maxX, minY);
        GL11.glVertex2f(maxX, maxY);
        GL11.glVertex2f(minX, maxY);
        GL11.glEnd();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    private void draw2DBrackets(Minecraft mc, EntityPlayer p, float partialTicks, float r, float g, float b) {
        float[] screen = getScreenBounds(mc, p, partialTicks);
        if (screen == null) return;

        float minX = screen[0];
        float minY = screen[1];
        float maxX = screen[2];
        float maxY = screen[3];

        float w = (maxX - minX) * 0.25f;
        float h = (maxY - minY) * 0.25f;

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, mc.displayWidth, mc.displayHeight, 0, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);
        GlStateManager.color(r, g, b, 1.0f);

        GL11.glBegin(GL11.GL_LINES);
        // Top Left ┌
        GL11.glVertex2f(minX, minY); GL11.glVertex2f(minX + w, minY);
        GL11.glVertex2f(minX, minY); GL11.glVertex2f(minX, minY + h);

        // Top Right ┐
        GL11.glVertex2f(maxX, minY); GL11.glVertex2f(maxX - w, minY);
        GL11.glVertex2f(maxX, minY); GL11.glVertex2f(maxX, minY + h);

        // Bottom Left └
        GL11.glVertex2f(minX, maxY); GL11.glVertex2f(minX + w, maxY);
        GL11.glVertex2f(minX, maxY); GL11.glVertex2f(minX, maxY - h);

        // Bottom Right ┘
        GL11.glVertex2f(maxX, maxY); GL11.glVertex2f(maxX - w, maxY);
        GL11.glVertex2f(maxX, maxY); GL11.glVertex2f(maxX, maxY - h);
        GL11.glEnd();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    private float[] getScreenBounds(Minecraft mc, EntityPlayer p, float partialTicks) {
        Entity viewer = mc.thePlayer;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * partialTicks;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * partialTicks;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * partialTicks;

        double x = (p.lastTickPosX + (p.posX - p.lastTickPosX) * partialTicks) - px;
        double y = (p.lastTickPosY + (p.posY - p.lastTickPosY) * partialTicks) - py;
        double z = (p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partialTicks) - pz;
        double w = p.width / 2.0;

        AxisAlignedBB bb = new AxisAlignedBB(x - w, y, z - w, x + w, y + p.height, z + w);

        double[][] corners = new double[][]{
                {bb.minX, bb.minY, bb.minZ}, {bb.maxX, bb.minY, bb.minZ},
                {bb.maxX, bb.minY, bb.maxZ}, {bb.minX, bb.minY, bb.maxZ},
                {bb.minX, bb.maxY, bb.minZ}, {bb.maxX, bb.maxY, bb.minZ},
                {bb.maxX, bb.maxY, bb.maxZ}, {bb.minX, bb.maxY, bb.maxZ}
        };

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (double[] c : corners) {
            float[] screenPos = project(c[0], c[1], c[2]);
            if (screenPos != null && screenPos[2] >= 0.0f && screenPos[2] <= 1.0f) {
                float sx = screenPos[0];
                float sy = mc.displayHeight - screenPos[1];

                if (sx < minX) minX = sx;
                if (sy < minY) minY = sy;
                if (sx > maxX) maxX = sx;
                if (sy > maxY) maxY = sy;
            }
        }

        if (minX == Float.MAX_VALUE || maxX <= minX || maxY <= minY) return null;
        return new float[]{minX, minY, maxX, maxY};
    }

    private float[] project(double x, double y, double z) {
        MODEL_MATRIX.rewind();
        PROJ_MATRIX.rewind();

        Matrix4f model = new Matrix4f(); model.load(MODEL_MATRIX);
        Matrix4f proj = new Matrix4f(); proj.load(PROJ_MATRIX);

        MODEL_MATRIX.rewind();
        PROJ_MATRIX.rewind();

        Vector4f in = new Vector4f((float) x, (float) y, (float) z, 1.0f);
        Vector4f out = Matrix4f.transform(model, in, null);
        out = Matrix4f.transform(proj, out, null);

        if (out.w <= 0.0f) return null;

        out.x /= out.w;
        out.y /= out.w;
        out.z /= out.w;

        float windowX = VIEWPORT[0] + VIEWPORT[2] * (out.x + 1.0f) / 2.0f;
        float windowY = VIEWPORT[1] + VIEWPORT[3] * (out.y + 1.0f) / 2.0f;

        return new float[]{windowX, windowY, (out.z + 1.0f) / 2.0f};
    }

    /** Delegates to the shared detector so the ESP and the VK Stealer agree. */
    private boolean isVagt(Minecraft mc, EntityPlayer p) {
        return VagtTargets.isVagt(p);
    }

    private boolean isBande(Minecraft mc, EntityPlayer p) {
        return MassiveOsFreakyAddons.config.isBandeMember(p.getName());
    }

    public static String bandeTag(EntityPlayer target) {
        try {
            net.minecraft.world.World w = target.worldObj;
            if (w == null) return null;
            String best = null;
            double bestH = 1.3 * 1.3;
            for (Object o : w.loadedEntityList) {
                if (!(o instanceof EntityArmorStand)) continue;
                Entity e = (Entity) o;
                if (!e.hasCustomName()) continue;
                double dx = e.posX - target.posX;
                double dz = e.posZ - target.posZ;
                double h = dx * dx + dz * dz;
                if (h > bestH || Math.abs(e.posY - target.posY) > 3.0) continue;
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
        if (p == null || p.getName() == null) return false;
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (!cfg.friendEspEnabled) return false;
        for (String friend : cfg.friendsList) {
            if (friend != null && friend.equalsIgnoreCase(p.getName())) {
                return true;
            }
        }
        return false;
    }
}
