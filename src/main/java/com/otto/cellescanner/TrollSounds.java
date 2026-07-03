package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Troll Sounds addon: plays goofy sound effects (bundled OGGs, registered in
 * sounds.json) reacting to your own gameplay. They're client-side, so only you
 * hear them - a personal soundboard, not something other players hear.
 *
 * Events: your death, killing a player, landing the first hit of a fight,
 * jumping (a random chance), and being AFK. Each is gated by its own toggle.
 */
public class TrollSounds {

    private static final Random RNG = new Random();

    private static final float JUMP_CHANCE = 0.25f;      // chance to play on a jump
    private static final long COMBAT_GAP_MS = 8000L;     // gap that counts as a new fight (first hit)
    private static final long KILL_WINDOW_MS = 6000L;    // credit a player's death to you within this
    private static final long AFK_AFTER_MS = 60000L;     // idle this long before AFK sounds start
    private static final long AFK_EVERY_MS = 45000L;     // gap between AFK sounds

    private boolean prevOnGround = true;
    private boolean deathHandled = false;
    private long lastPlayerHitAt = 0L;
    private long lastActivityAt = 0L;
    private long lastAfkPlayAt = 0L;
    private double lastX, lastY, lastZ, lastYaw, lastPitch;

    // Players you've hit recently -> when, so their death can be credited to you.
    private final Map<EntityPlayer, Long> recentlyHit = new HashMap<EntityPlayer, Long>();

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (!CelleScannerMod.config.trollEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer || !(event.target instanceof EntityPlayer)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (CelleScannerMod.config.trollFirstHit && now - lastPlayerHitAt > COMBAT_GAP_MS) {
            play("cellescanner:troll.firsthit");
        }
        lastPlayerHitAt = now;
        recentlyHit.put((EntityPlayer) event.target, now);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CelleScannerMod.config.trollEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return;
        }
        long now = System.currentTimeMillis();

        // Your death.
        if (p.getHealth() <= 0.0F || p.isDead) {
            if (!deathHandled) {
                deathHandled = true;
                if (CelleScannerMod.config.trollDeath) {
                    play("cellescanner:troll.death");
                }
            }
        } else {
            deathHandled = false;
        }

        // Jump (a random chance).
        boolean onGround = p.onGround;
        if (CelleScannerMod.config.trollJump && prevOnGround && !onGround && p.motionY > 0.0
                && RNG.nextFloat() < JUMP_CHANCE) {
            play("cellescanner:troll.jump");
        }
        prevOnGround = onGround;

        // Kill: a recently-hit player whose health has just hit zero. Uses health
        // (not isDead) so a player merely walking out of render range - which also
        // marks the entity dead client-side - isn't mistaken for a kill.
        Iterator<Map.Entry<EntityPlayer, Long>> it = recentlyHit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<EntityPlayer, Long> e = it.next();
            if (now - e.getValue() > KILL_WINDOW_MS) {
                it.remove();
                continue;
            }
            if (e.getKey().getHealth() <= 0.0F) {
                if (CelleScannerMod.config.trollKill) {
                    play("cellescanner:troll.kill");
                }
                it.remove();
            }
        }

        // AFK: no movement/look for a while -> play occasionally.
        boolean moved = Math.abs(p.posX - lastX) > 0.01 || Math.abs(p.posY - lastY) > 0.01
                || Math.abs(p.posZ - lastZ) > 0.01 || Math.abs(p.rotationYaw - lastYaw) > 0.5
                || Math.abs(p.rotationPitch - lastPitch) > 0.5;
        lastX = p.posX;
        lastY = p.posY;
        lastZ = p.posZ;
        lastYaw = p.rotationYaw;
        lastPitch = p.rotationPitch;
        if (moved) {
            lastActivityAt = now;
        }
        if (CelleScannerMod.config.trollAfk && lastActivityAt > 0
                && now - lastActivityAt > AFK_AFTER_MS && now - lastAfkPlayAt > AFK_EVERY_MS) {
            lastAfkPlayAt = now;
            play("cellescanner:troll.afk");
        }
    }

    /** Plays a registered troll sound event (Minecraft picks a random variant). */
    static void play(String event) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.playSound(event, 1.0F, 1.0F);
            }
        } catch (Throwable ignored) {
        }
    }
}
