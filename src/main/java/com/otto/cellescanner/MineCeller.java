package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mine Celler addon: runs "/ce find &lt;you&gt;" on demand, captures the celle ids
 * out of the server's chat reply (owned, co-owned and invited all show up
 * there), and remembers them. Your celler then get a distinct gold ESP box at
 * their scanned position so you can pick them out, and clicking one in the GUI
 * points the Celle Finder compass at it.
 *
 * The reply is parsed by pulling celle-id-shaped tokens (a letter or two then
 * digits, e.g. B351) out of each line, so it doesn't depend on the exact
 * wording of the server's message.
 */
public class MineCeller {

    private static final Pattern CELLE_ID = Pattern.compile("\\b[A-Za-z]{1,2}[0-9]{2,5}\\b");
    private static final double PAD = 0.03;
    private static final long CAPTURE_MS = 4000L;

    private static long capturingUntil = 0L;

    /** Sends "/ce find &lt;you&gt;" and opens a short window to capture the reply. */
    public static void fetch() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        String name = mc.thePlayer.getName();
        capturingUntil = System.currentTimeMillis() + CAPTURE_MS;
        CelleActions.message("Henter dine celler via /ce find " + name + " ...");
        mc.thePlayer.sendChatMessage("/ce find " + name);
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (System.currentTimeMillis() > capturingUntil || event.message == null) {
            return;
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (text == null) {
            return;
        }
        boolean added = false;
        Matcher m = CELLE_ID.matcher(text);
        while (m.find()) {
            String id = m.group();
            if (!CelleScannerMod.config.isMyCelle(id)) {
                CelleScannerMod.config.myCelleIds.add(id);
                added = true;
            }
        }
        if (added) {
            CelleScannerMod.config.save();
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        CelleConfig cfg = CelleScannerMod.config;
        if (!cfg.mineCellerEspEnabled || cfg.myCelleIds.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        int dim = mc.theWorld.provider.getDimensionId();

        Entity viewer = mc.thePlayer;
        float pt = event.partialTicks;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);

        for (String id : cfg.myCelleIds) {
            CellePositions.Entry p = CellePositions.get(id);
            if (p == null || p.dimension != dim) {
                continue;
            }
            drawBox(new BlockPos(p.x, p.y, p.z));
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawBox(BlockPos pos) {
        double minX = pos.getX() - PAD;
        double minY = pos.getY() - PAD;
        double minZ = pos.getZ() - PAD;
        double maxX = pos.getX() + 1 + PAD;
        double maxY = pos.getY() + 1 + PAD;
        double maxZ = pos.getZ() + 1 + PAD;

        GlStateManager.color(1.0F, 0.85F, 0.1F, 0.9F);

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
