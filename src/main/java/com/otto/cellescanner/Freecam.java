package com.otto.cellescanner;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.UUID;

/**
 * Freecam Addon for CelleScanner.
 * Detaches the view camera from the player entity to fly through blocks with WASD + Space/Shift,
 * while AutoMine, PathWalker, or pathfinding continue to run uninterrupted on the real player in the background!
 */
public class Freecam {

    public static final Freecam INSTANCE = new Freecam();

    private boolean active = false;
    private EntityOtherPlayerMP freecamEntity = null;
    private double originalX, originalY, originalZ;
    private float originalYaw, originalPitch;

    // Default flight speed
    private float speed = 0.5f;

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        if (active) {
            disable();
        } else {
            enable();
        }
    }

    public void enable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        active = true;

        // Save original position & rotation
        originalX = mc.thePlayer.posX;
        originalY = mc.thePlayer.posY;
        originalZ = mc.thePlayer.posZ;
        originalYaw = mc.thePlayer.rotationYaw;
        originalPitch = mc.thePlayer.rotationPitch;

        // Spawn fake spectator camera entity
        GameProfile profile = new GameProfile(UUID.randomUUID(), "[Freecam]");
        freecamEntity = new EntityOtherPlayerMP(mc.theWorld, profile);
        freecamEntity.copyLocationAndAnglesFrom(mc.thePlayer);
        freecamEntity.rotationYawHead = mc.thePlayer.rotationYawHead;
        freecamEntity.noClip = true;
        freecamEntity.capabilities.isFlying = true;

        mc.theWorld.addEntityToWorld(-9999, freecamEntity);
        mc.setRenderViewEntity(freecamEntity);

        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§a[CelleScanner] Freecam §eAKTIVERET§a. Styr med WASD + Space/Shift. Mine-bot kører i baggrunden!"));
        }
    }

    public void disable() {
        Minecraft mc = Minecraft.getMinecraft();
        active = false;

        if (mc.theWorld != null && freecamEntity != null) {
            mc.theWorld.removeEntityFromWorld(-9999);
            freecamEntity = null;
        }

        if (mc.thePlayer != null) {
            mc.setRenderViewEntity(mc.thePlayer);
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText("§c[CelleScanner] Freecam §7DEAKTIVERET."));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || freecamEntity == null) {
            disable();
            return;
        }

        // Keep camera entity in sync with view entity
        if (mc.getRenderViewEntity() != freecamEntity) {
            mc.setRenderViewEntity(freecamEntity);
        }

        // Handle camera motion inputs (only when no GUI screen is open)
        if (mc.currentScreen == null) {
            GameSettings gs = mc.gameSettings;
            float moveSpeed = (float) (CelleScannerMod.config != null ? CelleScannerMod.config.freecamSpeed : 1.5);
            if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                moveSpeed *= 2.5f; // Fast sprint speed
            }

            double forward = 0;
            double strafe = 0;
            double up = 0;

            if (gs.keyBindForward.isKeyDown()) forward += 1;
            if (gs.keyBindBack.isKeyDown()) forward -= 1;
            if (gs.keyBindLeft.isKeyDown()) strafe += 1;
            if (gs.keyBindRight.isKeyDown()) strafe -= 1;
            if (gs.keyBindJump.isKeyDown()) up += 1;
            if (gs.keyBindSneak.isKeyDown()) up -= 1;

            // Compute 3D direction vector from camera rotation
            float yaw = freecamEntity.rotationYaw;
            float pitch = freecamEntity.rotationPitch;

            double radYaw = Math.toRadians(yaw);
            double radPitch = Math.toRadians(pitch);

            double dx = 0;
            double dy = up * moveSpeed * 0.4;
            double dz = 0;

            if (forward != 0 || strafe != 0) {
                double speedVal = moveSpeed * 0.4;
                double sinY = Math.sin(radYaw);
                double cosY = Math.cos(radYaw);

                dx = (forward * -sinY + strafe * cosY) * speedVal;
                dz = (forward * cosY + strafe * sinY) * speedVal;
            }

            freecamEntity.setPosition(
                    freecamEntity.posX + dx,
                    freecamEntity.posY + dy,
                    freecamEntity.posZ + dz
            );

            // Cancel manual player movement keys so manual WASD doesn't fight AutoMine
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindBack.getKeyCode(), false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindLeft.getKeyCode(), false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindRight.getKeyCode(), false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), false);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), false);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getRenderManager() == null) {
            return;
        }

        // Render player body shell at real physical position so player can spectate their bot working
        try {
            double renderX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks - mc.getRenderManager().viewerPosX;
            double renderY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks - mc.getRenderManager().viewerPosY;
            double renderZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks - mc.getRenderManager().viewerPosZ;

            mc.getRenderManager().renderEntitySimple(mc.thePlayer, event.partialTicks);
        } catch (Throwable ignored) {
        }
    }
}
