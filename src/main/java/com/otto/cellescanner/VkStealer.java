package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * VK Stealer, short for vagt kill.
 *
 * Watches for staff from {@link VagtRoster} in reach, and lands the finishing hit.
 * The point is the last hit rather than the whole fight, because a crowd is usually
 * beating on the same guard, so by default it holds off until the target drops below
 * a health threshold and only then commits.
 *
 * Everything is off unless the addon is enabled in the menu.
 */
public class VkStealer {

    private static final Random RANDOM = new Random();

    /** Vanilla survival attack reach. Going past this only produces rejected packets. */
    private static final float MAX_REACH = 3.0f;

    private long nextAttackAllowedMs = 0L;
    private EntityPlayer lockedTarget = null;
    private long lastKillMs = 0L;
    private int killCount = 0;

    /** The current target, for the HUD. Null when nothing is locked. */
    public static EntityPlayer currentTarget = null;
    public static float currentTargetHealth = -1f;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.vkStealerEnabled) {
            clearLock();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            clearLock();
            return;
        }
        if (!mc.thePlayer.isEntityAlive()) {
            clearLock();
            return;
        }

        EntityPlayer target = pickTarget(mc, cfg);
        lockedTarget = target;
        currentTarget = target;
        currentTargetHealth = target != null ? healthOf(target) : -1f;

        if (target == null) {
            return;
        }

        // Steal mode holds fire until the guard is nearly down, so the crowd does the
        // work and the finishing hit is the one that lands.
        if (cfg.vkStealOnly) {
            float frac = healthFraction(target);
            if (frac >= 0f && frac > cfg.vkHealthThreshold) {
                return;
            }
            if (frac < 0f && !cfg.vkAttackWhenHealthUnknown) {
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now < nextAttackAllowedMs) {
            return;
        }

        aimAt(mc, target, cfg);
        attack(mc, target);

        // A little jitter so the swing rate is not a perfectly flat interval.
        int base = Math.max(50, cfg.vkAttackDelayMs);
        nextAttackAllowedMs = now + base + RANDOM.nextInt(Math.max(1, base / 2));
    }

    private void clearLock() {
        lockedTarget = null;
        currentTarget = null;
        currentTargetHealth = -1f;
    }

    /**
     * Best guard to finish. Anything out of reach or not visible is skipped, then the
     * lowest health wins, because that is the one about to die. Distance breaks ties.
     */
    private EntityPlayer pickTarget(Minecraft mc, CelleConfig cfg) {
        EntityPlayer self = mc.thePlayer;
        float reach = Math.min(MAX_REACH, Math.max(1.0f, cfg.vkReach));

        EntityPlayer best = null;
        float bestHealth = Float.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;

        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            if (p == self || !p.isEntityAlive() || p.isDead) {
                continue;
            }
            if (p.isInvisible() && !cfg.vkTargetInvisible) {
                continue;
            }
            if (!VagtRoster.contains(p.getName())) {
                continue;
            }
            double dist = self.getDistanceToEntity(p);
            if (dist > reach) {
                continue;
            }
            if (cfg.vkRequireLineOfSight && !self.canEntityBeSeen(p)) {
                continue;
            }

            float hp = healthOf(p);
            float score = hp >= 0f ? hp : Float.MAX_VALUE - 1f;
            if (score < bestHealth || (score == bestHealth && dist < bestDist)) {
                bestHealth = score;
                bestDist = dist;
                best = p;
            }
        }
        return best;
    }

    /** Absolute health, or -1 when the server does not sync it. */
    private static float healthOf(EntityPlayer p) {
        float tag = VagtTargets.rawTagHealth(p);
        if (tag >= 0f) {
            return tag;
        }
        float hp = p.getHealth();
        return hp >= 0f ? hp : -1f;
    }

    /** Health as 0 to 1, or -1 when unknown. */
    private static float healthFraction(EntityPlayer p) {
        float frac = VagtTargets.healthFraction(p);
        if (frac >= 0f) {
            return frac;
        }
        float max = p.getMaxHealth();
        if (max > 0f) {
            return p.getHealth() / max;
        }
        return -1f;
    }

    /**
     * Points at the target. Silent aim sends the rotation with the packet only, so the
     * camera never moves; otherwise the player is actually turned.
     */
    private void aimAt(Minecraft mc, EntityPlayer target, CelleConfig cfg) {
        double dx = target.posX - mc.thePlayer.posX;
        double dz = target.posZ - mc.thePlayer.posZ;
        double dy = (target.posY + target.getEyeHeight()) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double flat = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));

        if (cfg.vkSilentAim) {
            // Server sees the aim, the local camera does not move.
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(
                        new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
            }
            return;
        }

        if (cfg.vkSmoothAim) {
            float curYaw = mc.thePlayer.rotationYaw;
            float curPitch = mc.thePlayer.rotationPitch;
            float dYaw = wrapDegrees(yaw - curYaw);
            float dPitch = pitch - curPitch;
            float step = 0.35f;
            mc.thePlayer.rotationYaw = curYaw + dYaw * step;
            mc.thePlayer.rotationPitch = curPitch + dPitch * step;
        } else {
            mc.thePlayer.rotationYaw = yaw;
            mc.thePlayer.rotationPitch = pitch;
        }
    }

    private void attack(Minecraft mc, EntityPlayer target) {
        if (mc.playerController == null) {
            return;
        }
        mc.playerController.attackEntity(mc.thePlayer, target);
        mc.thePlayer.swingItem();
        if (!target.isEntityAlive()) {
            killCount++;
            lastKillMs = System.currentTimeMillis();
        }
    }

    private static float wrapDegrees(float deg) {
        deg = deg % 360f;
        if (deg >= 180f) deg -= 360f;
        if (deg < -180f) deg += 360f;
        return deg;
    }

    public int getKillCount() {
        return killCount;
    }

    public long getLastKillMs() {
        return lastKillMs;
    }
}
