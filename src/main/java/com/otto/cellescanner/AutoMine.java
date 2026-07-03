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
    private static final double COLLECT_R = 5.0; // walk to dropped items within this

    private final Random rng = new Random();

    private boolean holding = false;   // whether we currently hold any keys
    private BlockPos mining = null;    // block being broken
    private float tYaw, tPitch;        // smoothed rotation targets
    private long pauseUntil = 0;

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
            walkTo(mc, new BlockPos((MIN_X + MAX_X) / 2, MAX_Y, (MIN_Z + MAX_Z) / 2));
            stopMining(mc);
            return;
        }
        // Collect a nearby dropped item first (walk onto it), then mine on.
        EntityItem item = findItem(mc);
        if (item != null) {
            double dx = item.posX - mc.thePlayer.posX;
            double dz = item.posZ - mc.thePlayer.posZ;
            if (Math.sqrt(dx * dx + dz * dz) > 0.9) {
                stopMining(mc);
                aimYaw(mc, dx, dz);
                walkForward(mc);
                return;
            }
            // Close enough: the pickup happens automatically, fall through to mining.
        }
        BlockPos target = findTarget(mc);
        if (target == null) {
            // Box empty (mined out) - idle until it resets and blocks reappear.
            stopMining(mc);
            stopWalk(mc);
            return;
        }
        aimAt(mc, target);
        double d = eyeDist(mc, target);
        if (d <= REACH && lookingAt(mc, target)) {
            stopWalk(mc);
            breakBlock(mc, target);
        } else if (d > REACH) {
            walkForward(mc);
            stopMining(mc);
        } else {
            // In reach, still turning onto it - hold position.
            stopWalk(mc);
            stopMining(mc);
        }
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
        walkForward(mc);
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
                    double score = dist - (y - MIN_Y) * 0.15; // top-down bias
                    if (dist <= REACH) {
                        score -= 100.0; // strongly prefer what we can reach
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

    /** Nearest dropped item within the box (loosely) and COLLECT_R of the player, or null. */
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
            double dx = it.posX - px, dy = it.posY - py, dz = it.posZ - pz;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = it;
            }
        }
        return best;
    }

    private void breakBlock(Minecraft mc, BlockPos pos) {
        EnumFacing side = mc.objectMouseOver != null && mc.objectMouseOver.sideHit != null
                ? mc.objectMouseOver.sideHit : EnumFacing.UP;
        if (!pos.equals(mining)) {
            mc.playerController.clickBlock(pos, side);
            mining = pos;
        }
        mc.playerController.onPlayerDamageBlock(pos, side);
        mc.thePlayer.swingItem();
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

    private boolean lookingAt(Minecraft mc, BlockPos pos) {
        MovingObjectPosition m = mc.objectMouseOver;
        return m != null && m.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(m.getBlockPos());
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
    }

    private void walkTo(Minecraft mc, BlockPos p) {
        aimYaw(mc, (p.getX() + 0.5) - mc.thePlayer.posX, (p.getZ() + 0.5) - mc.thePlayer.posZ);
        walkForward(mc);
    }

    private void stopWalk(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
    }

    private void releaseKeys(Minecraft mc) {
        stopWalk(mc);
        stopMining(mc);
    }

    private void stopAll(Minecraft mc) {
        releaseKeys(mc);
        holding = false;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
