package com.otto.cellescanner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;

/**
 * Celle Buyer. Claims a celle the moment it becomes buyable.
 *
 * The timing is not guesswork. Celle signs tick on a fixed grid: every countdown
 * value is an exact multiple of 1200 seconds, and the sign steps down one notch
 * every {@link #GRID_PERIOD_MS}. Measured over twelve consecutive ticks that
 * period is 1199.6s rather than a round 1200, which is small enough to ignore for
 * one step and large enough to miss by half a second if you extrapolate a day out.
 * So a celle that expires on schedule can be predicted from an anchor taken twenty
 * minutes earlier, and the addon arms itself before the sign visibly changes.
 *
 * Two things this deliberately does not try to beat. A celle can be dumped from
 * days out when an owner sells up, which nothing predicts, so the addon also fires
 * reactively the moment a sign flips. And an owner can renew a celle one tick
 * before it expires, so an armed target that suddenly gains time is dropped rather
 * than clicked at.
 *
 * Off unless switched on in the menu.
 */
public class CelleBuyer {

    /**
     * Measured sign period. Twelve clean samples between two real ticks on the
     * same celle all landed here, not on a round 1200000.
     */
    private static final long GRID_PERIOD_MS = 1199600L;

    /** Every countdown value observed was an exact multiple of this. */
    private static final long STEP_SECONDS = 1200L;

    /** Vanilla block reach. Anything past this is the extended reach setting. */
    public static final float VANILLA_REACH = 4.5f;

    /** The server drops block use past six blocks, so further is only wasted packets. */
    public static final float MAX_REACH = 6.0f;

    private long nextClickAllowedMs = 0L;
    private int boughtCount = 0;

    /** What the settings screen shows. Null id means nothing is armed. */
    public static String currentTargetId = null;
    public static long currentTargetFreeInMs = -1L;
    public static boolean currentTargetBuyable = false;
    public static double currentTargetDistance = -1.0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CelleConfig cfg = MassiveOsFreakyAddons.config;
        if (cfg == null || !cfg.celleBuyerEnabled) {
            clear();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) {
            clear();
            return;
        }
        if (!mc.thePlayer.isEntityAlive()) {
            clear();
            return;
        }
        // A screen being open means the purchase dialog is probably up. Clicking
        // through it would be the addon fighting its own first click.
        if (mc.currentScreen != null) {
            return;
        }
        CelleScanner scanner = MassiveOsFreakyAddons.scanner;
        if (scanner == null) {
            clear();
            return;
        }

        long now = System.currentTimeMillis();
        float reach = effectiveReach(cfg);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);

        BlockPos bestPos = null;
        Celle best = null;
        double bestDist = Double.MAX_VALUE;
        long bestFreeIn = Long.MAX_VALUE;
        boolean bestBuyable = false;

        for (Map.Entry<BlockPos, Celle> entry : scanner.getCache().entrySet()) {
            BlockPos pos = entry.getKey();
            Celle celle = entry.getValue();
            if (celle == null) {
                continue;
            }
            Vec3 face = faceCenter(mc, pos);
            double dist = eyes.distanceTo(face);
            if (dist > reach) {
                continue;
            }

            if (!isWhitelisted(cfg, celle.celleId)) {
                continue;
            }

            boolean buyable = celle.status == CelleStatus.TIL_SALG;
            long freeIn;
            if (buyable) {
                freeIn = 0L;
            } else {
                long predicted = predictedFreeAt(celle, cfg);
                if (predicted < 0L) {
                    continue;
                }
                freeIn = predicted - now;
                if (freeIn > cfg.celleBuyerArmSeconds * 1000L) {
                    continue;
                }
            }

            // Buyable beats armed, then soonest, then nearest.
            boolean better;
            if (buyable != bestBuyable) {
                better = buyable;
            } else if (freeIn != bestFreeIn) {
                better = freeIn < bestFreeIn;
            } else {
                better = dist < bestDist;
            }
            if (better) {
                bestPos = pos;
                best = celle;
                bestDist = dist;
                bestFreeIn = freeIn;
                bestBuyable = buyable;
            }
        }

        if (best == null) {
            clear();
            return;
        }

        currentTargetId = best.celleId;
        currentTargetFreeInMs = bestFreeIn;
        currentTargetBuyable = bestBuyable;
        currentTargetDistance = bestDist;

        // Aim early so the rotation is already correct when the click goes out,
        // rather than sharing a tick with it.
        aimAt(mc, cfg, faceCenter(mc, bestPos));

        // Fire once it is actually buyable, or slightly before the predicted flip
        // so the click is already in flight when the server releases it. Clicking
        // a sold sign costs nothing, which is what makes the lead safe to spend.
        boolean fire = bestBuyable || bestFreeIn <= cfg.celleBuyerPreClickMs;
        if (!fire || now < nextClickAllowedMs) {
            return;
        }

        clickSign(mc, bestPos);
        nextClickAllowedMs = now + Math.max(50, cfg.celleBuyerClickIntervalMs);
    }

    private void clear() {
        currentTargetId = null;
        currentTargetFreeInMs = -1L;
        currentTargetBuyable = false;
        currentTargetDistance = -1.0;
    }

    /**
     * Celle ids are stored and compared uppercase. The same celle has turned up
     * as both "c1289" and "C1289" depending on whether it was owned or free, so
     * matching on the sign's own casing would silently split one celle into two.
     */
    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Whether this celle is one to buy. With the list off everything passes; with
     * it on an empty list passes nothing, which is the safe way for it to fail.
     */
    public static boolean isWhitelisted(CelleConfig cfg, String celleId) {
        if (cfg == null) {
            return false;
        }
        if (!cfg.celleBuyerUseWhitelist) {
            return true;
        }
        if (cfg.celleBuyerWhitelist == null || cfg.celleBuyerWhitelist.isEmpty()) {
            return false;
        }
        String want = normalizeId(celleId);
        if (want.isEmpty()) {
            return false;
        }
        for (int i = 0; i < cfg.celleBuyerWhitelist.size(); i++) {
            if (want.equals(normalizeId(cfg.celleBuyerWhitelist.get(i)))) {
                return true;
            }
        }
        return false;
    }

    /** Adds a celle to the list. False when it is blank or already there. */
    public static boolean addToWhitelist(CelleConfig cfg, String celleId) {
        if (cfg == null) {
            return false;
        }
        String id = normalizeId(celleId);
        if (id.isEmpty()) {
            return false;
        }
        if (cfg.celleBuyerWhitelist == null) {
            cfg.celleBuyerWhitelist = new java.util.ArrayList<String>();
        }
        for (int i = 0; i < cfg.celleBuyerWhitelist.size(); i++) {
            if (id.equals(normalizeId(cfg.celleBuyerWhitelist.get(i)))) {
                return false;
            }
        }
        cfg.celleBuyerWhitelist.add(id);
        cfg.save();
        return true;
    }

    public static void removeFromWhitelist(CelleConfig cfg, String celleId) {
        if (cfg == null || cfg.celleBuyerWhitelist == null) {
            return;
        }
        String id = normalizeId(celleId);
        java.util.Iterator<String> it = cfg.celleBuyerWhitelist.iterator();
        while (it.hasNext()) {
            if (id.equals(normalizeId(it.next()))) {
                it.remove();
            }
        }
        cfg.save();
    }

    /** Reach is held at vanilla unless extended reach is explicitly switched on. */
    public static float effectiveReach(CelleConfig cfg) {
        if (cfg == null) {
            return VANILLA_REACH;
        }
        if (!cfg.celleBuyerExtendedReach) {
            return VANILLA_REACH;
        }
        return Math.max(VANILLA_REACH, Math.min(MAX_REACH, cfg.celleBuyerReach));
    }

    /**
     * When this celle should hit zero, in wall-clock millis, or -1 when there is
     * nothing worth predicting from.
     *
     * remainingSeconds is the raw value read off the sign and valueUpdatedAt is
     * when it changed, so the pair is a real anchor rather than an extrapolation.
     * timerConfirmed means this client witnessed the change itself; without it the
     * sign could have been read partway through a step and the anchor is optimistic
     * by up to a full twenty minutes.
     */
    public static long predictedFreeAt(Celle celle, CelleConfig cfg) {
        if (celle == null || celle.valueUpdatedAt <= 0L) {
            return -1L;
        }
        if (cfg != null && cfg.celleBuyerOnlyConfirmed && !celle.timerConfirmed) {
            return -1L;
        }
        if (celle.remainingSeconds <= 0L) {
            return -1L;
        }
        long steps = celle.remainingSeconds / STEP_SECONDS;
        return celle.valueUpdatedAt + steps * GRID_PERIOD_MS;
    }

    /**
     * The middle of the sign's front face. Wall signs carry the direction they
     * face; a standing sign is approached from above.
     */
    private static Vec3 faceCenter(Minecraft mc, BlockPos pos) {
        EnumFacing side = frontFace(mc, pos);
        return new Vec3(
                pos.getX() + 0.5 + side.getFrontOffsetX() * 0.5,
                pos.getY() + 0.5 + side.getFrontOffsetY() * 0.5,
                pos.getZ() + 0.5 + side.getFrontOffsetZ() * 0.5);
    }

    private static EnumFacing frontFace(Minecraft mc, BlockPos pos) {
        try {
            IBlockState state = mc.theWorld.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockWallSign) {
                Object facing = state.getValue(BlockWallSign.FACING);
                if (facing instanceof EnumFacing) {
                    return (EnumFacing) facing;
                }
            }
        } catch (Exception ignored) {
            // Chunk unloaded under us, or a state without the property. Fall through.
        }
        return EnumFacing.UP;
    }

    /**
     * Points at the sign. Silent aim puts the rotation in the packet only, so the
     * camera never swings and the player keeps whatever view they were holding.
     */
    private void aimAt(Minecraft mc, CelleConfig cfg, Vec3 point) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        double dx = point.xCoord - eyes.xCoord;
        double dy = point.yCoord - eyes.yCoord;
        double dz = point.zCoord - eyes.zCoord;
        double flat = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)));

        if (cfg.celleBuyerSilentAim) {
            if (mc.getNetHandler() != null) {
                mc.getNetHandler().addToSendQueue(
                        new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, mc.thePlayer.onGround));
            }
            return;
        }
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
    }

    /**
     * Uses the sign directly by position rather than through whatever the crosshair
     * happens to be over. That is the part that survives a crowd: a player standing
     * between you and the sign blocks the client raytrace but not this.
     */
    private void clickSign(Minecraft mc, BlockPos pos) {
        if (mc.playerController == null) {
            return;
        }
        EnumFacing side = frontFace(mc, pos);
        Vec3 hit = faceCenter(mc, pos);
        mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.getCurrentEquippedItem(), pos, side, hit);
        mc.thePlayer.swingItem();
    }

    /** Called by the scanner when a celle we were clicking at turns up as ours. */
    public void noteBought() {
        boughtCount++;
    }

    public int getBoughtCount() {
        return boughtCount;
    }
}
