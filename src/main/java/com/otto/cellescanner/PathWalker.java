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
 *
 * NOTE: AutoMine.navigate() has a very similar path-follow loop (waypoint
 * advance, ladder climb, sprint gating). The two are duplicated on purpose:
 * each is tuned separately (turn easing, walk-angle gates, re-plan policy at
 * the end of a partial path) and both are known-good. If they ever need the
 * same fix twice, hoist the shared follow logic into a helper then.
 */
public class PathWalker {

    private static final double REACH = 2.0;
    // Celle walks can be long, so let the search work harder than the miner's short hops.
    private static final int SEARCH_BUDGET = 20000;
    private static final long SEARCH_EVERY = 1000; // ms between (re)searches while walking

    // Facing gate with hysteresis: start walking once roughly lined up, but once
    // moving keep walking through the small 45-degree jogs between diagonal
    // waypoints instead of stuttering to a stop at each one. Only a sharp turn
    // (roughly a right angle) still stops to swing around first.
    private static final float WALK_START = 35f;
    private static final float WALK_KEEP = 60f;

    private static BlockPos goal;
    private static List<BlockPos> path;
    private static int index;
    private static long progressAt;
    private static long lastSearch;
    private static double bestGoalDist;   // closest we've been to the goal
    private static long bestGoalDistAt;   // when that improved (for the stuck check)
    private static boolean active;
    private static boolean walking;       // forward key held (for the hysteresis gate)
    private static boolean sprinting;     // sprint key held (so sprint doesn't flicker)

    /** Start walking to a block (in the current world). */
    public static void walkTo(BlockPos g) {
        if (g == null) {
            return;
        }
        goal = g;
        path = null;
        index = 0;
        progressAt = System.currentTimeMillis();
        lastSearch = 0; // search on the very first tick
        bestGoalDist = Double.MAX_VALUE;
        bestGoalDistAt = progressAt;
        active = true;
    }

    public static void stop() {
        active = false;
        path = null;
        goal = null;
        walking = false;
        sprinting = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            AutoEat.stop(mc);
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
        // Pause (hands off the keys) while any screen is open, except inventory or chat.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory) && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            releaseKeys(mc);
            AutoEat.stop(mc);
            return;
        }
        // Auto-eat so we don't starve on a long walk (sprinting burns hunger).
        if (AutoEat.tick(mc)) {
            releaseKeys(mc);
            return; // eating - hold still
        }
        tickWalk(mc);
    }

    private void tickWalk(Minecraft mc) {
        long now = System.currentTimeMillis();

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

        // Track progress toward the goal; give up only if we stop getting closer for
        // a while (so a far but reachable celle keeps going, segment by segment).
        double goalDist = Math.sqrt(gdx * gdx + gdy * gdy + gdz * gdz);
        if (goalDist < bestGoalDist - 0.5) {
            bestGoalDist = goalDist;
            bestGoalDistAt = now;
        }
        if (now - bestGoalDistAt > 20000) {
            message(mc, "§cWalk to celle: sidder fast / kan ikke komme tættere på cellen.");
            stop();
            return;
        }

        BlockPos currentGoal = goal;
        BlockPos portalEntrance = null;
        String targetCelleId = CelleFinder.getTarget();
        CellePositions.Entry targetCelle = targetCelleId != null ? CellePositions.get(targetCelleId) : null;
        String targetGang = targetCelle != null ? targetCelle.gang : null;

        if (targetGang != null) {
            PortalRouting.Portal portal = PortalRouting.getPortalForGang(targetGang);
            if (portal != null) {
                String playerGang = null;
                CellePositions.Entry closest = null;
                double closestDist = Double.MAX_VALUE;
                for (CellePositions.Entry pe : CellePositions.snapshot().values()) {
                    if (pe.x == 0 && pe.y == 0 && pe.z == 0) continue;
                    double dx = pe.x - mc.thePlayer.posX;
                    double dy = pe.y - mc.thePlayer.posY;
                    double dz = pe.z - mc.thePlayer.posZ;
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = pe;
                    }
                }
                if (closest != null && closestDist < 1600.0) { // within 40 blocks
                    playerGang = closest.gang;
                }
                if (playerGang == null || !playerGang.equalsIgnoreCase(targetGang)) {
                    portalEntrance = portal.getEntrance();
                }
            }
        }

        if (portalEntrance == null) {
            portalEntrance = PortalRouting.getPortalEntranceFor(
                    mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ,
                    goal.getX() + 0.5, goal.getY() + 0.5, goal.getZ() + 0.5
            );
        }

        if (portalEntrance != null) {
            currentGoal = portalEntrance;
        }

        World w = mc.theWorld;
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        boolean stale = path == null || now - progressAt > 3000; // no path yet, or stuck
        if (stale && now - lastSearch >= SEARCH_EVERY) {
            path = Pathfinder.findPath(w, feet, currentGoal, REACH, SEARCH_BUDGET, Pathfinder.safeFall(mc.thePlayer));
            lastSearch = now;
            index = 0;
            if (path != null) {
                progressAt = now;
            }
        }
        if (path == null || path.isEmpty()) {
            // Not even a partial toward the goal - stand still and keep searching.
            releaseKeys(mc);
            return;
        }

        int reached = Pathfinder.consumeWaypoints(path, index,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (reached != index) {
            index = reached;
            progressAt = now;
        }
        if (index >= path.size()) {
            // Reached the end of a partial path - re-plan from here now (new chunks
            // have loaded as we walked, so the next segment goes further).
            path = Pathfinder.findPath(w, feet, currentGoal, REACH, SEARCH_BUDGET, Pathfinder.safeFall(mc.thePlayer));
            lastSearch = now;
            index = 0;
            progressAt = now;
            if (path == null || path.isEmpty()) {
                releaseKeys(mc);
                return;
            }
        }
        BlockPos step = path.get(index);

        // Climb a ladder: face into its wall and push forward every tick (no jump).
        BlockPos ladderPos = Pathfinder.isLadder(w, feet) ? feet
                : (Pathfinder.isLadder(w, feet.up()) ? feet.up() : (Pathfinder.isLadder(w, step) ? step : null));
        boolean up = false;
        if (ladderPos != null) {
            for (int i = index; i < path.size(); i++) {
                if (path.get(i).getY() > MathHelper.floor_double(mc.thePlayer.posY)) {
                    up = true;
                    break;
                }
            }
        }
        if (up) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            if (into != null) {
                mc.thePlayer.rotationYaw = Pathfinder.yawOf(into);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                return;
            }
        }
        // Sprint hysteresis: start on a 4-step straight, but once sprinting keep it
        // across short 2-step jogs so the FOV doesn't pump on and off constantly.
        boolean sprint = Pathfinder.straightRun(path, index, sprinting ? 2 : 4);
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
        float gate = walking ? WALK_KEEP : WALK_START;
        if (adiff < gate) {
            walking = true;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            boolean sprintNow = sprint && adiff < 12f; // only sprint when well-lined-up
            boolean autoJump = mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprintNow);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                    sprintNow ? mc.thePlayer.onGround : autoJump);
            sprinting = sprintNow;
        } else {
            releaseKeys(mc);
        }
    }

    private void releaseKeys(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        walking = false;
        sprinting = false;
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
