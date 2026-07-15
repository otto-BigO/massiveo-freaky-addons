package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IWorldAccess;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerLogger implements IWorldAccess {

    public static final PlayerLogger INSTANCE = new PlayerLogger();

    private static final long EXPIRATION_TIME = 15 * 60 * 1000; // 15 minutes
    private static final double LABEL_SCALE = 0.02666667F;

    public static class LogoutMarker {
        public final String name;
        public final UUID uuid;
        public final double x, y, z;
        public final AxisAlignedBB boundingBox;
        public final long timestamp;

        public LogoutMarker(String name, UUID uuid, double x, double y, double z, AxisAlignedBB boundingBox) {
            this.name = name;
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.boundingBox = boundingBox;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class PendingCheck {
        public final String name;
        public final UUID uuid;
        public final double x, y, z;
        public final AxisAlignedBB boundingBox;
        public final long timestamp;

        public PendingCheck(String name, UUID uuid, double x, double y, double z, AxisAlignedBB boundingBox) {
            this.name = name;
            this.uuid = uuid;
            this.x = x;
            this.y = y;
            this.z = z;
            this.boundingBox = boundingBox;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final List<LogoutMarker> markers = new ArrayList<LogoutMarker>();
    private final List<PendingCheck> pendingChecks = new ArrayList<PendingCheck>();
    private final Set<UUID> realPlayers = new HashSet<UUID>();

    public static void clearLogouts() {
        synchronized (INSTANCE.markers) {
            INSTANCE.markers.clear();
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            event.world.addWorldAccess(this);
            synchronized (markers) {
                markers.clear();
            }
            synchronized (pendingChecks) {
                pendingChecks.clear();
            }
            synchronized (realPlayers) {
                realPlayers.clear();
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            synchronized (markers) {
                markers.clear();
            }
            synchronized (pendingChecks) {
                pendingChecks.clear();
            }
            synchronized (realPlayers) {
                realPlayers.clear();
            }
        }
    }

    @Override
    public void onEntityAdded(Entity entity) {
        if (!CelleScannerMod.config.playerLoggerEnabled) {
            return;
        }
        if (entity instanceof net.minecraft.client.entity.EntityOtherPlayerMP) {
            UUID uuid = entity.getUniqueID();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() != null && mc.getNetHandler().getPlayerInfo(uuid) != null) {
                synchronized (realPlayers) {
                    realPlayers.add(uuid);
                }
            }

            synchronized (markers) {
                Iterator<LogoutMarker> it = markers.iterator();
                while (it.hasNext()) {
                    LogoutMarker marker = it.next();
                    if (marker.uuid.equals(uuid)) {
                        CelleActions.message(net.minecraft.util.EnumChatFormatting.GREEN + marker.name + " loggede ind igen!");
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onEntityRemoved(Entity entity) {
        if (!CelleScannerMod.config.playerLoggerEnabled) {
            return;
        }
        if (entity instanceof net.minecraft.client.entity.EntityOtherPlayerMP) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.thePlayer.equals(entity)) {
                return;
            }

            UUID uuid = entity.getUniqueID();
            
            // Filter out NPCs / fake players not verified to be real
            boolean isReal;
            synchronized (realPlayers) {
                isReal = realPlayers.contains(uuid);
            }
            if (!isReal) {
                return;
            }

            String name = entity.getName();
            double x = entity.posX;
            double y = entity.posY;
            double z = entity.posZ;
            AxisAlignedBB box = entity.getEntityBoundingBox();

            // All player removals must go through the 500ms tab list verification queue
            // to prevent false logging when players teleport or walk out of server render distance.
            synchronized (pendingChecks) {
                pendingChecks.add(new PendingCheck(name, uuid, x, y, z, box));
            }
        }
    }

    private void addMarker(String name, UUID uuid, double x, double y, double z, AxisAlignedBB box) {
        synchronized (markers) {
            for (LogoutMarker marker : markers) {
                if (marker.uuid.equals(uuid)) {
                    return;
                }
            }
            markers.add(new LogoutMarker(name, uuid, x, y, z, box));
        }
        // Remove from realPlayers list as they have officially logged out
        synchronized (realPlayers) {
            realPlayers.remove(uuid);
        }
        CelleActions.message(net.minecraft.util.EnumChatFormatting.RED + name + " loggede ud ved " 
                + String.format("%.1f, %.1f, %.1f", x, y, z) 
                + " (Afstand: " + String.format("%.1fm", Minecraft.getMinecraft().thePlayer.getDistance(x, y, z)) + ")");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CelleScannerMod.config.playerLoggerEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getNetHandler() == null) {
            return;
        }

        // Periodically verify and cache active players from the tab list to ensure
        // they are marked as real players (helps cover cases where the tab list entry
        // is received shortly after the player entity is loaded).
        for (Object obj : mc.theWorld.playerEntities) {
            if (obj instanceof EntityPlayer && obj != mc.thePlayer) {
                EntityPlayer p = (EntityPlayer) obj;
                if (mc.getNetHandler().getPlayerInfo(p.getUniqueID()) != null) {
                    synchronized (realPlayers) {
                        realPlayers.add(p.getUniqueID());
                    }
                }
            }
        }

        long now = System.currentTimeMillis();

        synchronized (pendingChecks) {
            Iterator<PendingCheck> it = pendingChecks.iterator();
            while (it.hasNext()) {
                PendingCheck check = it.next();
                if (now - check.timestamp > 500) {
                    if (mc.getNetHandler().getPlayerInfo(check.uuid) == null) {
                        addMarker(check.name, check.uuid, check.x, check.y, check.z, check.boundingBox);
                    }
                    it.remove();
                }
            }
        }

        synchronized (markers) {
            Iterator<LogoutMarker> it = markers.iterator();
            while (it.hasNext()) {
                LogoutMarker marker = it.next();
                if (now - marker.timestamp > EXPIRATION_TIME) {
                    it.remove();
                }
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!CelleScannerMod.config.playerLoggerEnabled) {
            return;
        }

        List<LogoutMarker> activeMarkers;
        synchronized (markers) {
            if (markers.isEmpty()) return;
            activeMarkers = new ArrayList<LogoutMarker>(markers);
        }

        Minecraft mc = Minecraft.getMinecraft();
        Entity viewer = mc.thePlayer;
        if (viewer == null) return;

        float pt = event.partialTicks;
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        FontRenderer fr = mc.fontRendererObj;
        RenderManager rm = mc.getRenderManager();
        long now = System.currentTimeMillis();

        for (LogoutMarker marker : activeMarkers) {
            AxisAlignedBB box = marker.boundingBox;
            if (box != null) {
                drawOutlinedBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 1.0F, 0.2F, 0.2F, 0.7F);
            }

            long elapsedMin = (now - marker.timestamp) / 60000;
            String elapsedStr = elapsedMin == 0 ? "lige nu" : elapsedMin + "m siden";
            String label = marker.name + " (" + elapsedStr + ")";
            
            drawLabel(fr, rm, label, marker.x, (box != null ? box.maxY : marker.y + 1.8) + 0.4, marker.z, 0xFF5555);
        }

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void drawOutlinedBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b, float a) {
        GL11.glLineWidth(2.5F);
        GlStateManager.color(r, g, b, a);

        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
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

    private void drawLabel(FontRenderer fr, RenderManager rm, String text, double x, double y, double z, int textColor) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);

        int halfWidth = fr.getStringWidth(text) / 2;
        Gui.drawRect(-halfWidth - 2, -2, halfWidth + 2, 10, 0x88000000);
        
        fr.drawString(text, -halfWidth, 0, textColor, true);
        GlStateManager.popMatrix();
    }

    @Override public void markBlockForUpdate(BlockPos pos) {}
    @Override public void notifyLightSet(BlockPos pos) {}
    @Override public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}
    @Override public void playSound(String soundName, double x, double y, double z, float volume, float pitch) {}
    @Override public void playSoundToNearExcept(net.minecraft.entity.player.EntityPlayer player, String soundName, double x, double y, double z, float volume, float pitch) {}
    @Override public void spawnParticle(int particleID, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {}
    @Override public void playRecord(String recordName, BlockPos pos) {}
    @Override public void playAuxSFX(net.minecraft.entity.player.EntityPlayer player, int sfxType, BlockPos blockPosIn, int p_180440_4_) {}
    @Override public void broadcastSound(int soundID, BlockPos pos, int data) {}
    @Override public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {}
}
