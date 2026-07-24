package com.otto.cellescanner;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.UUID;

/**
 * Freecam Addon for CelleScanner.
 * Detaches the camera view from the player entity to fly through blocks with WASD + Space/Shift and mouse look,
 * while AutoMine, PathWalker, or pathfinding continue to run uninterrupted on the real player in the background!
 */
public class Freecam {

    public static final Freecam INSTANCE = new Freecam();

    private boolean active = false;
    private EntityOtherPlayerMP freecamEntity = null;

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

        // Spawn fake spectator camera entity
        GameProfile profile = new GameProfile(UUID.randomUUID(), "[Freecam]");
        freecamEntity = new EntityOtherPlayerMP(mc.theWorld, profile);
        freecamEntity.copyLocationAndAnglesFrom(mc.thePlayer);
        freecamEntity.rotationYawHead = mc.thePlayer.rotationYawHead;
        freecamEntity.noClip = true;
        freecamEntity.capabilities.isFlying = true;

        mc.theWorld.addEntityToWorld(-9999, freecamEntity);
        mc.setRenderViewEntity(freecamEntity);

        mc.thePlayer.addChatMessage(new ChatComponentText("§a[CelleScanner] Freecam §eAKTIVERET§a. Styr med WASD + Space/Shift. Mine-bot kører i baggrunden!"));
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
            mc.thePlayer.addChatMessage(new ChatComponentText("§c[CelleScanner] Freecam §7DEAKTIVERET."));
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || freecamEntity == null) {
            return;
        }

        // Keep view entity attached to freecamEntity
        if (mc.getRenderViewEntity() != freecamEntity) {
            mc.setRenderViewEntity(freecamEntity);
        }

        // Handle mouse look for freecam camera
        if (mc.currentScreen == null && Mouse.isGrabbed()) {
            float mouseSensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
            float factor = mouseSensitivity * mouseSensitivity * mouseSensitivity * 8.0F;

            float dx = Mouse.getDX() * factor * 0.15F;
            float dy = Mouse.getDY() * factor * 0.15F;

            freecamEntity.rotationYaw += dx;
            freecamEntity.rotationPitch = Math.max(-90.0F, Math.min(90.0F, freecamEntity.rotationPitch - dy));
            freecamEntity.rotationYawHead = freecamEntity.rotationYaw;
            freecamEntity.prevRotationYaw = freecamEntity.rotationYaw;
            freecamEntity.prevRotationPitch = freecamEntity.rotationPitch;
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

        // Handle flight motion
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

            double radYaw = Math.toRadians(freecamEntity.rotationYaw);
            double speedVal = moveSpeed * 0.35;

            double sinY = Math.sin(radYaw);
            double cosY = Math.cos(radYaw);

            double dx = (forward * -sinY + strafe * cosY) * speedVal;
            double dy = up * speedVal;
            double dz = (forward * cosY + strafe * sinY) * speedVal;

            freecamEntity.setPosition(
                    freecamEntity.posX + dx,
                    freecamEntity.posY + dy,
                    freecamEntity.posZ + dz
            );

            // Unpress WASD/Jump/Sneak for the real player so manual keyboard inputs steer the camera, not the player!
            KeyBinding.setKeyBindState(gs.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(gs.keyBindBack.getKeyCode(), false);
            KeyBinding.setKeyBindState(gs.keyBindLeft.getKeyCode(), false);
            KeyBinding.setKeyBindState(gs.keyBindRight.getKeyCode(), false);
            KeyBinding.setKeyBindState(gs.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(gs.keyBindSneak.getKeyCode(), false);
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
            mc.getRenderManager().renderEntitySimple(mc.thePlayer, event.partialTicks);
        } catch (Throwable ignored) {
        }
    }
}
