package com.otto.cellescanner;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A* pathfinding over standable feet positions, plus the helpers the walking
 * bots (AutoMine, PathWalker, AutoFollow) use to follow the planned route.
 *
 * THE RULES - every one of these was earned from a real in-game bug. Change
 * them only with a debug log (cellescanner-debug.txt) proving the new behavior.
 *
 *  1. A cell is PASSABLE if it has no collision box, a short one (carpets,
 *     plates, <= 0.15 high), or is a LADDER/VINE. Ladders have a thin but
 *     FULL-HEIGHT box; without the explicit exception the search could only
 *     ever board a ladder at its TOP rung (the 66-node floor-lock bug).
 *  2. You can STAND on a block whose collision top is 0.5..1.0 high (slab..
 *     full). WATER delegates to the block under it (the water-logged pit floor).
 *  3. You can STAND IN a ladder cell whenever the cell above is passable.
 *  4. Moves: walk level (incl. onto ladders), jump up 1 (needs head clearance
 *     at source AND destination), drop up to maxFall blocks (survivable, see
 *     safeFall), climb ladders straight up/down, and cut level diagonals only
 *     when BOTH orthogonal cells are clear (no corner clipping).
 *  5. Costs: 1.0 per step (1.414 diagonal), +1.2/block up, +0.45/block down
 *     (a straight drop beats a long detour), +15.0 per block that must be
 *     MINED (allowMining) - so digging is a last resort, never a shortcut.
 *  6. maxFall comes from health: fall damage is (blocks - 3), keep a ~3-heart
 *     buffer, always allow 3, never above 20.
 *  7. If the goal is unreachable, return the path to the closest point found
 *     (if it is real progress, > 0.5 closer) so the bot advances and re-plans
 *     as chunks load; otherwise null - and log the failure with node counts.
 *  8. Waypoint consumption is lenient around ladders: a ladder waypoint counts
 *     as reached from ~1.118 blocks away horizontally and up to 3 blocks below
 *     (standing above a shaft), a normal one from ~0.81. NOTE: both radii are
 *     compared as SQUARED distance against 1.25/0.65 - a long-standing quirk
 *     the ladder-mount handoff is tuned around. Do not "fix" it.
 *  9. Never skip PAST a waypoint whose segment changes Y or sits on a ladder -
 *     shortcuts are for flat ground only.
 * 10. Path smoothing (line-of-sight) only on the same Y level, never while
 *     standing on a ladder (climbing needs exact targets).
 * 11. Climb decision (shared by all three bots via climbDecision, so the
 *     clones can never drift apart again):
 *       UP   when the step is above the player and posY is below the exit
 *            threshold - top rung exits at rung + 0.5 (the player physically
 *            cannot climb past ~rung + 0.12, so a higher bar locks the bot
 *            staring at the wall); mid-column exits at step - 0.25.
 *       DOWN when the step is below - unconditionally mid-column (a threshold
 *            there causes bouncing at block boundaries), but at the TOP rung
 *            only once posY < rung + 0.9 (so the bot walks onto the shaft
 *            instead of freezing at the pit edge).
 * 12. HAZARDS are never passable, even when mining: lava, fire and cobwebs
 *     have no (or no useful) collision box, so without this rule the search
 *     saw them as free air and routed straight through. Cactus is never a
 *     floor (contact damage). Checked BEFORE the mining exception - we do
 *     not dig into lava.
 * 13. WATER is a vertical corridor, like a ladder: from a water cell the
 *     search may swim UP one (and sink down one). Execution side (1.8.9):
 *     while in water and not standing on its floor, HOLD SPACE - the player
 *     floats to the surface and bobs out over the bank (Baritone-style,
 *     shouldSwimUp). Standing in 1-deep water (the water-logged pit floor,
 *     onGround true) does NOT swim, so mining there stays undisturbed.
 * 14. Terrain drag is costed: a step into water costs +0.5, onto soul sand
 *     +0.3, so dry fast routes win when they exist without forbidding the
 *     slow ones.
 * 15. Checkpoints may be SKIPPED, never walked back to. When the player ends
 *     up clearly closer (a full block+, within 3) to a LATER waypoint - fell
 *     past ladder rungs, got bumped, overshot - the index fast-forwards to it
 *     (consumeWaypoints' recovery pass) instead of steering backward.
 */
public final class Pathfinder {

    // ---- tuning constants (see THE RULES above) -----------------------------

    private static final int DEFAULT_MAX_NODES = 6000;
    private static final int DEFAULT_MAX_FALL = 4;

    private static final double PASSABLE_MAX_HEIGHT = 0.15; // carpets, plates
    private static final double WALK_ON_MIN_HEIGHT = 0.5;   // slab
    private static final double WALK_ON_MAX_HEIGHT = 1.0;   // full block (fences are 1.5)

    private static final double COST_DIAGONAL = 1.414;
    private static final double COST_UP_PER_BLOCK = 1.2;
    private static final double COST_DOWN_PER_BLOCK = 0.45;
    private static final double COST_MINE_PER_BLOCK = 15.0;
    private static final double COST_WATER = 0.5;     // rule 14: swimming is slow
    private static final double COST_SOUL_SAND = 0.3; // rule 14: walking drag

    private static final double PARTIAL_MIN_PROGRESS = 0.5;

    private static final int WAYPOINT_LOOKAHEAD = 16;
    // Rule 8: these two are compared against SQUARED horizontal distance.
    private static final double WP_RADIUS_SQ = 0.65;
    private static final double WP_LADDER_RADIUS_SQ = 1.25;
    private static final double WP_VERT_UP = 1.3;
    private static final double WP_VERT_DOWN = -1.3;
    private static final double WP_LADDER_VERT_DOWN = -3.0;

    private static final int LOS_LOOKAHEAD = 6;
    private static final double LOS_STEP = 0.25;

    private static final double EXIT_TOP_RUNG = 0.5;   // leave climb mode at rung + 0.5
    private static final double EXIT_MID_RUNG = 0.25;  // leave climb mode at step - 0.25
    private static final double ENTER_TOP_RUNG = 0.9;  // start sliding below rung + 0.9

    private static final int SAFE_FALL_MIN = 3;
    private static final int SAFE_FALL_MAX = 20;
    private static final int SAFE_FALL_HEART_BUFFER = 3;

    private static final String LOG_FILE = "cellescanner-debug.txt";

    private static final EnumFacing[] MOVE_DIRS =
            {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};

    private Pathfinder() {
    }

    // ---- block rules: what the world lets a player do -----------------------

    /** Rule 1: can a player's body occupy this cell? */
    public static boolean passable(World w, BlockPos p) {
        return passable(w, p, false);
    }

    public static boolean passable(World w, BlockPos p, boolean allowMining) {
        // Single block lookup - this runs on thousands of cells per search.
        IBlockState st = w.getBlockState(p);
        net.minecraft.block.Block block = st.getBlock();
        if (block == Blocks.lava || block == Blocks.flowing_lava
                || block == Blocks.fire || block == Blocks.web) {
            return false; // rule 12: hazards - never, not even when mining
        }
        if (allowMining && AutoMine.isMinableBlock(w, p)) {
            return true; // we may dig through it (costed in the search, rule 5)
        }
        if (block == Blocks.ladder || block == Blocks.vine) {
            return true; // rule 1: thin-but-full-height box, but enterable
        }
        AxisAlignedBB box = block.getCollisionBoundingBox(w, p, st);
        if (box == null) {
            return true;
        }
        return (box.maxY - p.getY()) <= PASSABLE_MAX_HEIGHT;
    }

    /** Rule 12: blocks that hurt or trap - the search must never enter them. */
    public static boolean isHazard(World w, BlockPos p) {
        net.minecraft.block.Block block = w.getBlockState(p).getBlock();
        return block == Blocks.lava || block == Blocks.flowing_lava
                || block == Blocks.fire || block == Blocks.web;
    }

    /** Rule 13: still or flowing water. */
    public static boolean isWater(World w, BlockPos p) {
        net.minecraft.block.Block block = w.getBlockState(p).getBlock();
        return block == Blocks.water || block == Blocks.flowing_water;
    }

    /** Rule 2: is this block a floor the player can stand on top of? */
    public static boolean canWalkOn(World w, BlockPos p) {
        IBlockState st = w.getBlockState(p);
        net.minecraft.block.Block block = st.getBlock();
        if (block == Blocks.cactus) {
            return false; // rule 12: contact damage - never a floor
        }
        if (block == Blocks.water || block == Blocks.flowing_water) {
            return canWalkOn(w, p.down()); // rule 2: water floors delegate down
        }
        AxisAlignedBB box = block.getCollisionBoundingBox(w, p, st);
        if (box == null) {
            return false;
        }
        double height = box.maxY - p.getY();
        return height >= WALK_ON_MIN_HEIGHT && height <= WALK_ON_MAX_HEIGHT;
    }

    /** Rules 2+3: can the player's feet BE at p (floor below, room above)? */
    public static boolean canStand(World w, BlockPos p) {
        return canStand(w, p, false);
    }

    public static boolean canStand(World w, BlockPos p, boolean allowMining) {
        if (isLadder(w, p)) {
            return passable(w, p.up(), allowMining); // rule 3
        }
        return passable(w, p, allowMining)
                && passable(w, p.up(), allowMining)
                && canWalkOn(w, p.down());
    }

    /** Rule 4 (jump up 1): landing spot valid? Hanging ladders skip the floor check. */
    private static boolean canAscend(World w, BlockPos fwd, boolean allowMining) {
        if (isLadder(w, fwd.up())) {
            // A ladder column whose lowest rung hangs above the floor - the hop
            // into it doesn't need a solid landing block.
            return passable(w, fwd, allowMining)
                    && passable(w, fwd.up(), allowMining)
                    && passable(w, fwd.up().up(), allowMining);
        }
        return canWalkOn(w, fwd)
                && passable(w, fwd.up(), allowMining)       // destination feet
                && passable(w, fwd.up().up(), allowMining);  // destination head
    }

    public static boolean isLadder(World w, BlockPos p) {
        net.minecraft.block.Block block = w.getBlockState(p).getBlock();
        return block == Blocks.ladder || block == Blocks.vine;
    }

    /** Rule 6: survivable drop height for this player, from current health. */
    public static int safeFall(EntityPlayer p) {
        int maxFall = (int) Math.floor(p.getHealth()) - SAFE_FALL_HEART_BUFFER;
        if (maxFall < SAFE_FALL_MIN) {
            maxFall = SAFE_FALL_MIN;
        }
        if (maxFall > SAFE_FALL_MAX) {
            maxFall = SAFE_FALL_MAX;
        }
        return maxFall;
    }

    // ---- the search ---------------------------------------------------------

    /** A route from start to a standable spot within {@code reach} of goal, or null. */
    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach) {
        return findPath(w, start, goal, reach, DEFAULT_MAX_NODES, DEFAULT_MAX_FALL, false);
    }

    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach, int maxNodes) {
        return findPath(w, start, goal, reach, maxNodes, DEFAULT_MAX_FALL, false);
    }

    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach, int maxNodes, int maxFall) {
        return findPath(w, start, goal, reach, maxNodes, maxFall, false);
    }

    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach,
                                          int maxNodes, int maxFall, boolean allowMining) {
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        HashMap<BlockPos, Double> gScore = new HashMap<BlockPos, Double>();
        HashSet<BlockPos> closed = new HashSet<BlockPos>();
        open.add(new Node(start, null, 0, dist(start, goal)));
        gScore.put(start, 0.0);

        int expanded = 0;
        double startH = dist(start, goal);
        Node best = null; // rule 7: closest node reached, for partial paths
        double bestH = startH;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            if (closed.contains(cur.pos)) {
                continue;
            }
            closed.add(cur.pos);
            expanded++;

            double h = dist(cur.pos, goal);
            if (h <= reach) {
                return reconstruct(cur); // arrived
            }
            if (h < bestH) {
                bestH = h;
                best = cur;
            }

            for (BlockPos nb : neighbors(w, cur.pos, maxFall, allowMining)) {
                if (closed.contains(nb)) {
                    continue;
                }
                double g = cur.g + stepCost(cur.pos, nb) + miningCost(w, nb, allowMining) + terrainCost(w, nb);
                Double old = gScore.get(nb);
                if (old == null || g < old) {
                    gScore.put(nb, g);
                    open.add(new Node(nb, cur, g, g + dist(nb, goal)));
                }
            }
        }

        // Rule 7: no full route - take real partial progress, else fail loudly.
        if (best != null && bestH < startH - PARTIAL_MIN_PROGRESS) {
            return reconstruct(best);
        }
        log("[Pathfinder-Debug] failed. start=" + start + " goal=" + goal
                + " expanded=" + expanded + " maxNodes=" + maxNodes
                + " bestH=" + bestH + " startH=" + startH + " allowMining=" + allowMining);
        if (expanded < 200) {
            log("[Pathfinder-Debug] closed nodes: " + closed.toString());
        }
        return null;
    }

    /** Rule 4: every position reachable in one move from p. */
    private static List<BlockPos> neighbors(World w, BlockPos p, int maxFall, boolean allowMining) {
        List<BlockPos> out = new ArrayList<BlockPos>(8);

        // Ladder verticals.
        if (isLadder(w, p)) {
            if (passable(w, p.up(), allowMining)) {
                out.add(p.up()); // climb up
            }
            if (canStand(w, p.down(), allowMining)) {
                out.add(p.down()); // climb/slide down
            }
        } else {
            // Rule 13: a water column is a vertical corridor too - swim up
            // (execution holds space, shouldSwimUp) or sink down one.
            if (isWater(w, p)) {
                if (passable(w, p.up(), allowMining)) {
                    out.add(p.up());
                }
                if (canStand(w, p.down(), allowMining)) {
                    out.add(p.down());
                }
            }
            if (isLadder(w, p.up()) && passable(w, p.up(), allowMining)) {
                out.add(p.up()); // step up into a ladder directly above
            }
            if (isLadder(w, p.down()) && passable(w, p.down(), allowMining)) {
                out.add(p.down()); // step down onto a ladder directly below
            }
        }

        // Level walks, jump-ups, drops.
        boolean srcHeadClear = passable(w, p.up(), allowMining)
                && passable(w, p.up().up(), allowMining); // room to hop from here
        int fall = Math.max(1, maxFall);
        for (EnumFacing d : MOVE_DIRS) {
            BlockPos fwd = p.offset(d);
            if (canStand(w, fwd, allowMining)) {
                out.add(fwd); // walk level (or onto a ladder)
            } else if (srcHeadClear && canAscend(w, fwd, allowMining)) {
                out.add(fwd.up()); // jump up 1
            } else if (passable(w, fwd, allowMining) && passable(w, fwd.up(), allowMining)) {
                for (int k = 1; k <= fall; k++) { // drop, if survivable (rule 6)
                    BlockPos dn = fwd.down(k);
                    if (canStand(w, dn, allowMining)) {
                        out.add(dn);
                        break;
                    }
                    if (!passable(w, dn, allowMining)) {
                        break; // hit a floor we can't stand on
                    }
                }
            }
        }

        // Diagonals, level only, both orthogonals clear (no corner clipping).
        EnumFacing[] ns = {EnumFacing.NORTH, EnumFacing.SOUTH};
        EnumFacing[] ew = {EnumFacing.EAST, EnumFacing.WEST};
        for (EnumFacing a : ns) {
            for (EnumFacing b : ew) {
                BlockPos oa = p.offset(a), ob = p.offset(b);
                BlockPos diag = oa.offset(b);
                if (canStand(w, diag, allowMining)
                        && passable(w, oa, allowMining) && passable(w, oa.up(), allowMining)
                        && passable(w, ob, allowMining) && passable(w, ob.up(), allowMining)) {
                    out.add(diag);
                }
            }
        }
        return out;
    }

    /** Rule 5: base movement cost of one step. */
    private static double stepCost(BlockPos a, BlockPos b) {
        int dx = Math.abs(b.getX() - a.getX());
        int dz = Math.abs(b.getZ() - a.getZ());
        int dy = b.getY() - a.getY();
        double horiz = (dx != 0 && dz != 0) ? COST_DIAGONAL : 1.0;
        double vert = dy > 0 ? dy * COST_UP_PER_BLOCK
                : (dy < 0 ? (-dy) * COST_DOWN_PER_BLOCK : 0.0);
        return horiz + vert;
    }

    /** Rule 14: drag surcharge - prefer dry, firm routes when they exist. */
    private static double terrainCost(World w, BlockPos nb) {
        double cost = 0.0;
        if (isWater(w, nb)) {
            cost += COST_WATER;
        }
        if (w.getBlockState(nb.down()).getBlock() == Blocks.soul_sand) {
            cost += COST_SOUL_SAND;
        }
        return cost;
    }

    /** Rule 5: surcharge when entering this cell means digging (feet and head). */
    private static double miningCost(World w, BlockPos nb, boolean allowMining) {
        if (!allowMining) {
            return 0.0;
        }
        double cost = 0.0;
        if (AutoMine.isMinableBlock(w, nb) && !passable(w, nb)) {
            cost += COST_MINE_PER_BLOCK;
        }
        if (AutoMine.isMinableBlock(w, nb.up()) && !passable(w, nb.up())) {
            cost += COST_MINE_PER_BLOCK;
        }
        return cost;
    }

    private static double dist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static List<BlockPos> reconstruct(Node end) {
        ArrayList<BlockPos> list = new ArrayList<BlockPos>();
        for (Node n = end; n != null; n = n.parent) {
            list.add(n.pos);
        }
        Collections.reverse(list);
        return list;
    }

    private static final class Node implements Comparable<Node> {
        final BlockPos pos;
        final Node parent;
        final double g, f;

        Node(BlockPos pos, Node parent, double g, double f) {
            this.pos = pos;
            this.parent = parent;
            this.g = g;
            this.f = f;
        }

        public int compareTo(Node o) {
            return Double.compare(this.f, o.f);
        }
    }

    // ---- following the path -------------------------------------------------

    /**
     * Advance the waypoint index past everything already reached, scanning up to
     * WAYPOINT_LOOKAHEAD ahead (backward from the furthest, so shortcuts
     * fast-forward). Tolerances per rule 8; skipping gated by rule 9.
     */
    public static int consumeWaypoints(World w, List<BlockPos> path, int index, double px, double py, double pz) {
        if (path == null || path.isEmpty() || index >= path.size()) {
            return index;
        }
        int limit = Math.min(path.size(), index + WAYPOINT_LOOKAHEAD);
        for (int j = limit - 1; j >= index; j--) {
            BlockPos wp = path.get(j);
            double dx = (wp.getX() + 0.5) - px;
            double dz = (wp.getZ() + 0.5) - pz;
            double dy = wp.getY() - py;

            boolean isL = isLadder(w, wp);
            double radiusSq = isL ? WP_LADDER_RADIUS_SQ : WP_RADIUS_SQ; // rule 8 (squared!)
            double vertDown = isL ? WP_LADDER_VERT_DOWN : WP_VERT_DOWN;

            if (dx * dx + dz * dz < radiusSq && dy >= vertDown && dy <= WP_VERT_UP) {
                if (flatSkippable(w, path, index, j)) {
                    return j + 1;
                }
            }
        }

        // Rule 15: overshoot recovery - checkpoints may be SKIPPED, never walked
        // back to. If the player ended up clearly closer to a LATER waypoint than
        // the current one (fell past ladder rungs, got bumped, took a shortcut
        // the flat-skip gate refused), fast-forward the index to that waypoint
        // instead of steering backward to checkpoints that no longer matter.
        BlockPos curWp = path.get(index);
        double curD = dist3(curWp, px, py, pz);
        int bestJ = -1;
        double bestD = curD - 1.0; // must be a full block closer - no jitter skips
        for (int j = index + 1; j < limit; j++) {
            double d = dist3(path.get(j), px, py, pz);
            if (d < bestD && d <= 3.0) { // and genuinely nearby
                bestD = d;
                bestJ = j;
            }
        }
        if (bestJ > index) {
            log("[Pathfinder-Skip] overshoot recovery: index " + index + " -> " + bestJ
                    + " (curD=" + String.format("%.2f", curD) + " newD=" + String.format("%.2f", bestD) + ")");
            return bestJ;
        }
        return index;
    }

    private static double dist3(BlockPos wp, double px, double py, double pz) {
        double dx = (wp.getX() + 0.5) - px;
        double dy = wp.getY() - py;
        double dz = (wp.getZ() + 0.5) - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Rule 9: may every waypoint in [index, j) be skipped over? Flat, no ladders. */
    private static boolean flatSkippable(World w, List<BlockPos> path, int index, int j) {
        for (int k = index; k < j; k++) {
            BlockPos stepK = path.get(k);
            BlockPos nextK = path.get(k + 1);
            if (stepK.getY() != nextK.getY() || isLadder(w, stepK)) {
                return false;
            }
        }
        return true;
    }

    /** Clear straight walk between two same-height cells (feet + head sampled every 0.25). */
    public static boolean hasLineOfSight(World w, BlockPos a, BlockPos b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 0.1) {
            return true;
        }
        int steps = (int) Math.ceil(d / LOS_STEP);
        for (int i = 1; i <= steps; i++) {
            double pct = (double) i / steps;
            BlockPos check = new BlockPos(a.getX() + 0.5 + dx * pct,
                    a.getY() + dy * pct,
                    a.getZ() + 0.5 + dz * pct);
            if (!passable(w, check) || !passable(w, check.up())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rule 10: the waypoint to steer at - the furthest same-level waypoint in
     * clear line of sight (so diagonal chains walk as one straight line), or the
     * exact current waypoint while on a ladder / when nothing is smoothable.
     */
    public static BlockPos getLineOfSightTarget(World w, BlockPos feet, List<BlockPos> path, int currentIndex) {
        if (path == null || path.isEmpty() || currentIndex >= path.size()) {
            return null;
        }
        if (isLadder(w, feet)) {
            return path.get(currentIndex); // climbing needs exact targets
        }
        int limit = Math.min(path.size(), currentIndex + LOS_LOOKAHEAD);
        for (int k = limit - 1; k > currentIndex; k--) {
            BlockPos target = path.get(k);
            if (target.getY() == feet.getY() && hasLineOfSight(w, feet, target)) {
                return target;
            }
        }
        return path.get(currentIndex);
    }

    /**
     * True if the path runs straight and flat for at least {@code minSteps} from
     * index {@code i} - a stretch worth sprinting (and sprint-jumping) on.
     */
    public static boolean straightRun(List<BlockPos> path, int i, int minSteps) {
        if (path == null || i < 0 || i + minSteps >= path.size()) {
            return false;
        }
        BlockPos a = path.get(i), b = path.get(i + 1);
        int dx = Integer.signum(b.getX() - a.getX());
        int dz = Integer.signum(b.getZ() - a.getZ());
        if (dx == 0 && dz == 0) {
            return false;
        }
        for (int k = i + 1; k <= i + minSteps && k + 1 < path.size(); k++) {
            BlockPos c = path.get(k), d = path.get(k + 1);
            if (Integer.signum(d.getX() - c.getX()) != dx || Integer.signum(d.getZ() - c.getZ()) != dz) {
                return false;
            }
            if (d.getY() != c.getY()) {
                return false; // flat only - clean sprint-jumps
            }
        }
        return true;
    }

    // ---- ladders: shared helpers for the three walking bots -----------------

    /** What a bot should do about a ladder this tick. */
    public enum Climb {
        UP, DOWN, NONE
    }

    /** The ladder cell relevant to this tick: at/above/below the feet, then the step. */
    public static BlockPos getClimbableLadder(World w, BlockPos feet, BlockPos step) {
        if (isLadder(w, feet)) {
            return feet;
        }
        if (isLadder(w, feet.up())) {
            return feet.up();
        }
        if (isLadder(w, feet.down())) {
            return feet.down();
        }
        if (step != null) {
            if (isLadder(w, step)) {
                return step;
            }
            if (isLadder(w, step.up())) {
                return step.up();
            }
            if (isLadder(w, step.down())) {
                return step.down();
            }
        }
        return null;
    }

    /**
     * Rule 11: the one shared climb decision. All three bots (AutoMine,
     * PathWalker, AutoFollow) call this so the thresholds can never drift
     * between them again. The ACTION (which keys to press) stays per-bot.
     */
    public static Climb climbDecision(World w, BlockPos feet, BlockPos step, BlockPos ladderPos, double posY) {
        if (ladderPos == null || step == null || feet == null) {
            return Climb.NONE;
        }
        boolean topRung = !isLadder(w, ladderPos.up());
        if (step.getY() > (int) Math.floor(posY)) {
            double threshold = topRung
                    ? ladderPos.getY() + EXIT_TOP_RUNG   // exit early: can't climb past the top
                    : step.getY() - EXIT_MID_RUNG;
            return posY < threshold ? Climb.UP : Climb.NONE;
        }
        if (step.getY() < feet.getY()) {
            // Mid-column: always slide. Top rung: only once we're low enough to
            // actually be entering the shaft (else keep walking toward it).
            if (!topRung || posY < ladderPos.getY() + ENTER_TOP_RUNG) {
                return Climb.DOWN;
            }
        }
        return Climb.NONE;
    }

    /**
     * Rule 13, execution side (1.8.9): hold SPACE while in water and not standing
     * on its floor - the player floats to the surface and bobs out over the bank,
     * Baritone-style. Standing in 1-deep water (onGround) does NOT trigger, so
     * mining on the water-logged pit floor is undisturbed.
     */
    public static boolean shouldSwimUp(EntityPlayer p) {
        return p.isInWater() && !p.onGround;
    }

    /** The direction to push to climb this ladder: toward the wall it hangs on. */
    public static EnumFacing ladderInto(World w, BlockPos p) {
        IBlockState st = w.getBlockState(p);
        if (st.getBlock() == Blocks.ladder) {
            return ((EnumFacing) st.getValue(BlockLadder.FACING)).getOpposite();
        }
        for (EnumFacing d : MOVE_DIRS) {
            if (!passable(w, p.offset(d))) {
                return d;
            }
        }
        return null;
    }

    public static float yawOf(EnumFacing f) {
        return (float) (Math.toDegrees(Math.atan2(f.getFrontOffsetZ(), f.getFrontOffsetX())) - 90.0);
    }

    // ---- rendering + debug --------------------------------------------------

    /** Draw the route as a line just above the floor, through each waypoint. */
    public static void renderPath(List<BlockPos> path, Entity viewer, float pt, float r, float g, float b) {
        if (path == null || path.size() < 2 || viewer == null) {
            return;
        }
        double px = viewer.lastTickPosX + (viewer.posX - viewer.lastTickPosX) * pt;
        double py = viewer.lastTickPosY + (viewer.posY - viewer.lastTickPosY) * pt;
        double pz = viewer.lastTickPosZ + (viewer.posZ - viewer.lastTickPosZ) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

        GL11.glLineWidth(4.0f);
        GlStateManager.color(r, g, b, 0.9f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (BlockPos p : path) {
            GL11.glVertex3d(p.getX() + 0.5, p.getY() + 0.08, p.getZ() + 0.5);
        }
        GL11.glEnd();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /** Append a line to cellescanner-debug.txt (the tool that caught every bug above). */
    public static void log(String msg) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(LOG_FILE), true);
            fw.write("[" + new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date()) + "] " + msg + "\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
