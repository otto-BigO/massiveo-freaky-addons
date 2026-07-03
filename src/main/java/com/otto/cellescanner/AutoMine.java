package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * Auto Mine addon (automation - off by default, use only where the server
 * allows it). Mines a fixed mine box block by block, walks to a deposit point
 * when the inventory is full, then comes back. When the box is mined out it just
 * idles until the mine resets and blocks reappear, so it resumes on its own.
 *
 * Movement and aiming are deliberately smooth and slightly varied (eased turns,
 * jitter, small pauses) rather than snappy, so it doesn't twitch like a bot.
 * v1: expect to tune the pattern in-game.
 */
public class AutoMine {

    // The auto-mine box (Otto's two corners) and the deposit point.
    private static final int MIN_X = 37, MAX_X = 52, MIN_Y = 42, MAX_Y = 60, MIN_Z = -692, MAX_Z = -677;
    private static final BlockPos RETURN_POINT = new BlockPos(20, 60, -684);

    private static final double REACH = 4.3;
    private static final int SCAN_R = 6;
    private static final double COLLECT_R = 6.0;  // walk over to drops within this
    private static final double IGNORE_NEAR = 1.3; // drops this close auto-collect, don't walk

    private final Random rng = new Random();

    private boolean holding = false;   // whether we currently hold any keys
    private BlockPos mining = null;    // block being broken
    private BlockPos target = null;    // block we're heading for (sticky until gone)
    private float tYaw, tPitch;        // smoothed rotation targets
    private long pauseUntil = 0;
    private long chaseSince = 0;       // when we started walking to the current drop
    private long skipDropsUntil = 0;   // ignore drops until this (breaks a stuck chase)

    // Only walk once we're roughly facing where we want to go. This is the single
    // most important anti-wander rule (MineBot/Baritone do the same): if we walk
    // while still turning, we drift off toward wherever we're half-facing and
    // circle forever instead of settling on the block.
    private static final float WALK_ANGLE = 30f;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!CelleScannerMod.config.autoMineEnabled) {
            if (holding) {
                stopAll(mc);
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            releaseKeys(mc);
            return;
        }
        holding = true;

        applyRotation(mc); // every tick, for smooth turning

        if (System.currentTimeMillis() < pauseUntil) {
            releaseKeys(mc);
            return;
        }

        if (inventoryFull(mc)) {
            doReturn(mc);
        } else {
            doMine(mc);
        }
    }

    private void doMine(Minecraft mc) {
        if (!nearBox(mc, 3)) {
            // Walk back to the box; only move once we're facing it.
            stopMining(mc);
            approach(mc, (MIN_X + MAX_X) / 2.0 + 0.5, (MIN_Z + MAX_Z) / 2.0 + 0.5);
            return;
        }

        // Sweep up dropped items before mining more, so nothing is left behind.
        // The facing gate keeps this from wandering, and we aim down at the item
        // (it's on the ground) so the head never levels/looks up like it used to.
        EntityItem drop = System.currentTimeMillis() < skipDropsUntil ? null : findItem(mc);
        if (drop != null) {
            if (chaseSince == 0) {
                chaseSince = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - chaseSince > 5000) {
                // Chasing too long - probably can't reach it. Skip drops a moment
                // and go mine, so we don't get stuck walking at it forever.
                skipDropsUntil = System.currentTimeMillis() + 3000;
                chaseSince = 0;
            } else {
                stopMining(mc);
                aimAtEntity(mc, drop);
                approach(mc, drop.posX, drop.posZ);
                return;
            }
        } else {
            chaseSince = 0;
        }

        // Stick to one target until it's mined out, then pick the nearest block.
        // Re-picking the nearest every tick made the aim flip between blocks and
        // the bot wander; keeping a target settles it.
        if (target == null || mc.theWorld.isAirBlock(target) || !inBox(target)) {
            target = findTarget(mc);
        }
        if (target == null) {
            // Box mined out - idle until it resets and blocks reappear.
            stopMining(mc);
            stopWalk(mc);
            return;
        }

        aimAt(mc, target);
        double dist = eyeDist(mc, target);

        if (dist <= REACH + 0.3) {
            // In reach: never walk (this is what stops the endless walking). Mine
            // whatever in-box block the crosshair is actually on, so the server
            // accepts it and it doesn't ghost-revert. As blocks clear, the next
            // target ends up out of reach and we step forward to it below.
            stopWalk(mc);
            MovingObjectPosition mop = mc.objectMouseOver;
            BlockPos looked = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    ? mop.getBlockPos() : null;
            if (looked != null && inBox(looked) && !mc.theWorld.isAirBlock(looked) && eyeDist(mc, looked) <= REACH + 0.5) {
                mineLookedAt(mc, looked, mop.sideHit);
            } else {
                // Crosshair hasn't settled on the block yet - wait, don't walk.
                stopMining(mc);
            }
        } else {
            // Too far to reach: step toward it, but only while roughly facing it.
            stopMining(mc);
            approach(mc, target.getX() + 0.5, target.getZ() + 0.5);
        }
    }

    /**
     * Walk toward (x,z) but only once we're roughly facing that direction, and
     * auto-jump when we bump a wall. Rotating-while-walking is what makes a bot
     * drift and circle, so we gate movement on the yaw being close first.
     */
    private void approach(Minecraft mc, double x, double z) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(wantYaw - mc.thePlayer.rotationYaw));
        if (diff < WALK_ANGLE) {
            walkForward(mc);
        } else {
            stopWalk(mc);
        }
    }

    private void mineLookedAt(Minecraft mc, BlockPos pos, EnumFacing side) {
        if (side == null) {
            side = EnumFacing.UP;
        }
        mining = pos;
        // onPlayerDamageBlock handles both starting a new block and continuing the
        // current one, matching vanilla's per-tick mining call.
        mc.playerController.onPlayerDamageBlock(pos, side);
        mc.thePlayer.swingItem();
    }

    private boolean inBox(BlockPos p) {
        return p.getX() >= MIN_X && p.getX() <= MAX_X && p.getY() >= MIN_Y && p.getY() <= MAX_Y
                && p.getZ() >= MIN_Z && p.getZ() <= MAX_Z;
    }

    private void doReturn(Minecraft mc) {
        stopMining(mc);
        double dx = (RETURN_POINT.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (RETURN_POINT.getZ() + 0.5) - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.4) {
            stopAll(mc);
            pauseUntil = System.currentTimeMillis() + 1500 + rng.nextInt(2000); // wait while you deposit/sell
            return;
        }
        aimYaw(mc, dx, dz);
        approach(mc, RETURN_POINT.getX() + 0.5, RETURN_POINT.getZ() + 0.5);
    }

    private BlockPos findTarget(Minecraft mc) {
        double ex = mc.thePlayer.posX;
        double ey = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double ez = mc.thePlayer.posZ;
        int px = MathHelper.floor_double(ex), py = MathHelper.floor_double(ey), pz = MathHelper.floor_double(ez);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = Math.min(MAX_Y, py + SCAN_R); y >= Math.max(MIN_Y, py - SCAN_R); y--) {
            for (int x = Math.max(MIN_X, px - SCAN_R); x <= Math.min(MAX_X, px + SCAN_R); x++) {
                for (int z = Math.max(MIN_Z, pz - SCAN_R); z <= Math.min(MAX_Z, pz + SCAN_R); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (mc.theWorld.isAirBlock(p)) {
                        continue;
                    }
                    double dx = (x + 0.5) - ex, dy = (y + 0.5) - ey, dz = (z + 0.5) - ez;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    // Nearest block first; reachable ones win over unreachable.
                    double score = dist;
                    if (dist <= REACH) {
                        score -= 100.0;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** Nearest dropped item inside the box (with margin) worth walking to, or null. */
    private EntityItem findItem(Minecraft mc) {
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        EntityItem best = null;
        double bestD = COLLECT_R * COLLECT_R;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityItem)) {
                continue;
            }
            EntityItem it = (EntityItem) o;
            if (it.posX < MIN_X - 2 || it.posX > MAX_X + 3 || it.posZ < MIN_Z - 2 || it.posZ > MAX_Z + 3
                    || it.posY < MIN_Y - 3 || it.posY > MAX_Y + 3) {
                continue;
            }
            double dx = it.posX - px, dz = it.posZ - pz;
            // Drops right next to us get picked up automatically - don't walk for them.
            if (dx * dx + dz * dz < IGNORE_NEAR * IGNORE_NEAR) {
                continue;
            }
            double dy = it.posY - py;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = it;
            }
        }
        return best;
    }

    private void aimAtEntity(Minecraft mc, EntityItem e) {
        double dx = e.posX - mc.thePlayer.posX;
        double dy = e.posY - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = e.posZ - mc.thePlayer.posZ;
        double dh = Math.sqrt(dx * dx + dz * dz);
        tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        tPitch = (float) (-Math.toDegrees(Math.atan2(dy, dh))); // item is below eye, so this looks down
    }

    private void stopMining(Minecraft mc) {
        if (mining != null) {
            mc.playerController.resetBlockRemoving();
            mining = null;
        }
    }

    private void aimAt(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (pos.getY() + 0.5) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = (pos.getZ() + 0.5) - mc.thePlayer.posZ;
        double dh = Math.sqrt(dx * dx + dz * dz);
        tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        tPitch = (float) (-Math.toDegrees(Math.atan2(dy, dh)));
    }

    private void aimYaw(Minecraft mc, double dx, double dz) {
        tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        tPitch = 2f;
    }

    /** Ease the player's look toward the target with a capped step + jitter, so it turns smoothly. */
    private void applyRotation(Minecraft mc) {
        float dy = MathHelper.wrapAngleTo180_float(tYaw - mc.thePlayer.rotationYaw);
        float dp = tPitch - mc.thePlayer.rotationPitch;
        float stepY = dy * (0.28f + rng.nextFloat() * 0.12f);
        float stepP = dp * (0.28f + rng.nextFloat() * 0.12f);
        float cap = 11f + rng.nextFloat() * 5f;
        stepY = clamp(stepY, -cap, cap);
        stepP = clamp(stepP, -cap, cap);
        if (Math.abs(dy) < 6f) {
            stepY += (rng.nextFloat() - 0.5f) * 1.2f;
        }
        if (Math.abs(dp) < 6f) {
            stepP += (rng.nextFloat() - 0.5f) * 0.8f;
        }
        mc.thePlayer.rotationYaw += stepY;
        mc.thePlayer.rotationPitch = clamp(mc.thePlayer.rotationPitch + stepP, -90f, 90f);
    }

    private double eyeDist(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (pos.getY() + 0.5) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = (pos.getZ() + 0.5) - mc.thePlayer.posZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private boolean nearBox(Minecraft mc, double m) {
        double x = mc.thePlayer.posX, y = mc.thePlayer.posY, z = mc.thePlayer.posZ;
        return x >= MIN_X - m && x <= MAX_X + 1 + m && z >= MIN_Z - m && z <= MAX_Z + 1 + m
                && y >= MIN_Y - 3 && y <= MAX_Y + 3;
    }

    private boolean inventoryFull(Minecraft mc) {
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) {
                return false;
            }
        }
        return true;
    }

    private void walkForward(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        // Auto-jump: if walking into something and on the ground, hop it (1.8.9 has
        // no auto-step). Walking down is just gravity once it can move off a ledge.
        boolean jump = mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround;
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), jump);
    }

    private void stopWalk(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }

    private void releaseKeys(Minecraft mc) {
        stopWalk(mc);
        stopMining(mc);
    }

    private void stopAll(Minecraft mc) {
        releaseKeys(mc);
        target = null;
        chaseSince = 0;
        holding = false;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
