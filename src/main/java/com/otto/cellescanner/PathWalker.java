package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Walks the player to a target block using {@link Pathfinder} (routes around
 * walls, climbs ladders), and draws the route on the floor. Used by "Walk to
 * celle". One walk at a time; it stops on arrival, on timeout, or when cancelled.
 */
public class PathWalker {

    private static final double REACH = 2.0;
    // Celle walks can be long, so let the search work harder than the miner's short hops.
    private static final int SEARCH_BUDGET = 20000;
    private static final long SEARCH_EVERY = 1000; // ms between (re)searches while walking

    private static BlockPos goal;
    private static List<BlockPos> path;
    private static int index;
    private static long progressAt;
    private static long startAt;
    private static long lastSearch;
    private static boolean active;

    /** Start walking to a block (in the current world). */
    public static void walkTo(BlockPos g) {
        if (g == null) {
            return;
        }
        goal = g;
        path = null;
        index = 0;
        progressAt = System.currentTimeMillis();
        startAt = progressAt;
        lastSearch = 0; // search on the very first tick
        active = true;
    }

    public static void stop() {
        active = false;
        path = null;
        goal = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        // Don't fight the Auto Mine bot for the controls.
        if (CelleScannerMod.config.autoMineEnabled) {
            stop();
            return;
        }
        // Pause (hands off the keys) while any screen is open.
        if (mc.currentScreen != null) {
            releaseKeys(mc);
            return;
        }
        tickWalk(mc);
    }

    private void tickWalk(Minecraft mc) {
        // Arrived?
        double gdx = (goal.getX() + 0.5) - mc.thePlayer.posX;
        double gdz = (goal.getZ() + 0.5) - mc.thePlayer.posZ;
        double gdy = goal.getY() - mc.thePlayer.posY;
        if (gdx * gdx + gdz * gdz < REACH * REACH && Math.abs(gdy) < 3.0) {
            message(mc, "§aWalk to celle: ankommet.");
            CelleFinder.clearTarget(); // stop showing the sign outline once we're there
            stop();
            return;
        }
        // Give up if it's taking too long (blocked, unreachable, wrong dimension).
        if (System.currentTimeMillis() - startAt > 60000) {
            message(mc, path == null
                    ? "§cWalk to celle: fandt ingen sti til cellen (for langt væk eller ikke indlæst?)."
                    : "§cWalk to celle: kunne ikke nå cellen.");
            stop();
            return;
        }

        World w = mc.theWorld;
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        long now = System.currentTimeMillis();
        boolean stale = path == null || now - progressAt > 3000; // no path yet, or stuck
        if (stale && now - lastSearch >= SEARCH_EVERY) {
            path = Pathfinder.findPath(w, feet, goal, REACH, SEARCH_BUDGET);
            lastSearch = now;
            index = 0;
            if (path != null) {
                progressAt = now;
            }
        }
        if (path == null || path.isEmpty()) {
            // No route found yet - stand still and keep searching (no blind walking).
            releaseKeys(mc);
            return;
        }

        while (index < path.size()) {
            BlockPos wp = path.get(index);
            double dx = (wp.getX() + 0.5) - mc.thePlayer.posX;
            double dz = (wp.getZ() + 0.5) - mc.thePlayer.posZ;
            if (dx * dx + dz * dz < 0.45 && Math.abs(wp.getY() - mc.thePlayer.posY) < 1.3) {
                index++;
                progressAt = System.currentTimeMillis();
            } else {
                break;
            }
        }
        BlockPos step = index < path.size() ? path.get(index) : goal;

        // Climb a ladder: face into its wall and push forward every tick (no jump).
        boolean up = step.getY() > MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos ladderPos = Pathfinder.isLadder(w, feet) ? feet
                : (Pathfinder.isLadder(w, feet.up()) ? feet.up() : (Pathfinder.isLadder(w, step) ? step : null));
        if (up && ladderPos != null) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            if (into != null) {
                mc.thePlayer.rotationYaw = Pathfinder.yawOf(into);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                return;
            }
        }
        boolean sprint = Pathfinder.straightRun(path, index, 4);
        faceAndWalk(mc, step.getX() + 0.5, step.getZ() + 0.5, sprint);
    }

    /**
     * Ease the look toward (x,z) and walk once roughly facing it. On a long straight
     * stretch it sprints and sprint-jumps (bunny-hops) to cover ground faster; near
     * turns it drops to a walk and auto-jumps 1-block steps.
     */
    private void faceAndWalk(Minecraft mc, double x, double z, boolean sprint) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float want = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float diff = MathHelper.wrapAngleTo180_float(want - mc.thePlayer.rotationYaw);
        float step = diff * 0.35f;
        step = step < -18f ? -18f : (step > 18f ? 18f : step);
        mc.thePlayer.rotationYaw += step;

        float adiff = Math.abs(diff);
        if (adiff < 35f) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            boolean sprinting = sprint && adiff < 12f; // only sprint when well-lined-up
            boolean autoJump = mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprinting);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                    sprinting ? mc.thePlayer.onGround : autoJump);
        } else {
            releaseKeys(mc);
        }
    }

    private void releaseKeys(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }

    private void message(Minecraft mc, String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!active || path == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            Pathfinder.renderPath(path, mc.thePlayer, event.partialTicks, 0.4f, 1.0f, 0.45f);
        }
    }
}
