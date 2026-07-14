package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Anti-AFK addon: while enabled, performs one tiny action every
 * antiAfkIntervalSeconds so the server's idle timer never trips.
 *
 * The actions are deliberately minimal and each is individually toggleable:
 *  - swing: a hand swing (looks like a click, moves nothing).
 *  - rotate: nudges the view a few degrees, alternating direction so the
 *    player never slowly spins away from where they were pointed.
 *  - jump: an actual hop, only when on the ground and no screen is open -
 *    this one changes position, which stricter idle checks require, but it's
 *    off by default since it's the most visible.
 *  - strafe: steps a little to the right and straight back to where it
 *    started, by briefly simulating the strafe keys. Also changes position
 *    (good against stricter idle checks) but ends up back on the same spot.
 *
 * Purely client-side inputs, the same ones normal play produces. Some servers
 * forbid AFK machines/macros though, so this is off by default and left to the
 * user to enable at their own discretion.
 */
public class AntiAfk {

    // How many ticks to hold each leg of the strafe (right, then left). 4 ticks
    // (~0.2s) each is a small, obvious step out and back without wandering off.
    private static final int STRAFE_LEG_TICKS = 4;

    private int tickCounter = 0;
    private boolean flip = false;

    // Strafe maneuver state: 0 = idle, 1 = stepping right, 2 = stepping back left.
    private int strafePhase = 0;
    private int strafeTimer = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (!CelleScannerMod.config.antiAfkEnabled) {
            tickCounter = 0;
            if (strafePhase != 0) {
                cancelStrafe(mc);
            }
            return;
        }

        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        // A strafe maneuver, once started, runs to completion on consecutive
        // ticks (independent of the interval gate below) so the step out and
        // the step back are one smooth motion.
        if (strafePhase != 0) {
            tickStrafe(mc);
            return;
        }

        tickCounter++;
        int intervalTicks = Math.max(1, CelleScannerMod.config.antiAfkIntervalSeconds) * 20;
        if (tickCounter < intervalTicks) {
            return;
        }
        tickCounter = 0;

        if (CelleScannerMod.config.antiAfkSwing) {
            mc.thePlayer.swingItem();
        }
        if (CelleScannerMod.config.antiAfkRotate) {
            // Alternate direction each time so repeated nudges cancel out rather
            // than accumulating into a full spin.
            mc.thePlayer.rotationYaw += flip ? 12.0F : -12.0F;
            flip = !flip;
        }
        if (CelleScannerMod.config.antiAfkJump && mc.currentScreen == null && mc.thePlayer.onGround) {
            mc.thePlayer.jump();
        }
        if (CelleScannerMod.config.antiAfkStrafe && mc.currentScreen == null && mc.thePlayer.onGround) {
            startStrafe(mc);
        }
    }

    private void startStrafe(Minecraft mc) {
        strafePhase = 1;
        strafeTimer = STRAFE_LEG_TICKS;
        setPressed(mc.gameSettings.keyBindRight, true);
    }

    private void tickStrafe(Minecraft mc) {
        strafeTimer--;
        if (strafePhase == 1) {
            if (strafeTimer <= 0) {
                // Done stepping right - release right, hold left to come back.
                setPressed(mc.gameSettings.keyBindRight, false);
                setPressed(mc.gameSettings.keyBindLeft, true);
                strafePhase = 2;
                strafeTimer = STRAFE_LEG_TICKS;
            }
        } else if (strafePhase == 2) {
            if (strafeTimer <= 0) {
                setPressed(mc.gameSettings.keyBindLeft, false);
                strafePhase = 0;
            }
        }
    }

    /** Releases both strafe keys and aborts any maneuver - never leave a key stuck down. */
    private void cancelStrafe(Minecraft mc) {
        setPressed(mc.gameSettings.keyBindRight, false);
        setPressed(mc.gameSettings.keyBindLeft, false);
        strafePhase = 0;
        strafeTimer = 0;
    }

    private static void setPressed(KeyBinding bind, boolean pressed) {
        KeyBinding.setKeyBindState(bind.getKeyCode(), pressed);
    }
}
