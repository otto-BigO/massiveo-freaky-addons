package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
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
 *
 * Purely client-side inputs, the same ones normal play produces. Some servers
 * forbid AFK machines/macros though, so this is off by default and left to the
 * user to enable at their own discretion.
 */
public class AntiAfk {

    private int tickCounter = 0;
    private boolean flip = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!CelleScannerMod.config.antiAfkEnabled) {
            tickCounter = 0;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
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
    }
}
