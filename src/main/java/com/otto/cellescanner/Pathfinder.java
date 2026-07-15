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
 * A* pathfinding over standable positions (feet blocks). Steps up 1, drops down
 * up to 3, and climbs ladders - the moves the walker can actually follow. Shared
 * by the Auto Mine bot and the "Walk to celle" feature, and it can draw the
 * planned route as a line on the floor.
 */
public final class Pathfinder {

    private static final EnumFacing[] MOVE_DIRS =
            {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST};

    private Pathfinder() {
    }

    /** A route from start to a standable spot within {@code reach} of goal, or null. */
    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach) {
        return findPath(w, start, goal, reach, 6000, 4);
    }

    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach, int maxNodes) {
        return findPath(w, start, goal, reach, maxNodes, 4);
    }

    /**
     * {@code maxFall} = how many blocks it may drop down in one move. Bigger values
     * let it take a straight drop instead of a giant detour; the caller sizes it off
     * current health so a fall never kills.
     */
    public static List<BlockPos> findPath(World w, BlockPos start, BlockPos goal, double reach, int maxNodes, int maxFall) {
        PriorityQueue<Node> open = new PriorityQueue<Node>();
        HashMap<BlockPos, Double> gScore = new HashMap<BlockPos, Double>();
        HashSet<BlockPos> closed = new HashSet<BlockPos>();
        open.add(new Node(start, null, 0, heur(start, goal)));
        gScore.put(start, 0.0);
        int expanded = 0;
        double startH = dist(start, goal);
        Node best = null;         // closest node to the goal we've reached
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
                return reconstruct(cur); // reached the goal
            }
            if (h < bestH) {
                bestH = h;
                best = cur;
            }
            for (BlockPos nb : neighbors(w, cur.pos, maxFall)) {
                if (closed.contains(nb)) {
                    continue;
                }
                double g = cur.g + stepCost(cur.pos, nb);
                Double old = gScore.get(nb);
                if (old == null || g < old) {
                    gScore.put(nb, g);
                    open.add(new Node(nb, cur, g, g + heur(nb, goal)));
                }
            }
        }
        // Couldn't reach the goal (too far / not loaded / blocked). Head toward the
        // closest point we found so we make progress and re-plan as chunks load.
        if (best != null && bestH < startH - 0.5) {
            return reconstruct(best);
        }
        return null;
    }

    private static List<BlockPos> neighbors(World w, BlockPos p, int maxFall) {
        List<BlockPos> out = new ArrayList<BlockPos>(8);
        boolean headClear = passable(w, p.up().up()); // room to hop
        if (isLadder(w, p)) {
            if (passable(w, p.up())) {
                out.add(p.up()); // climb straight up
            }
            if (canStand(w, p.down())) {
                out.add(p.down()); // climb/slide straight down
            }
        }
        boolean srcHeadClear = passable(w, p.up()) && headClear; // room to jump from here
        int fall = Math.max(1, maxFall);
        for (EnumFacing d : MOVE_DIRS) {
            BlockPos fwd = p.offset(d);
            if (canStand(w, fwd)) {
                out.add(fwd); // walk level (or step onto a ladder)
            } else if (srcHeadClear && canAscend(w, fwd)) {
                out.add(fwd.up()); // jump up 1 onto a full block (Baritone-style checks)
            } else if (passable(w, fwd) && passable(w, fwd.up())) {
                for (int k = 1; k <= fall; k++) { // drop down up to maxFall (survivable)
                    BlockPos dn = fwd.down(k);
                    if (canStand(w, dn)) {
                        out.add(dn);
                        break;
                    }
                    if (!passable(w, dn)) {
                        break; // hit ground we can't stand on
                    }
                }
            }
        }
        // Diagonal level moves, so on open ground it cuts straight across ("skaevt")
        // instead of zig-zagging. Only if both orthogonal cells are clear (no corner
        // cutting through a block).
        EnumFacing[] ns = {EnumFacing.NORTH, EnumFacing.SOUTH};
        EnumFacing[] ew = {EnumFacing.EAST, EnumFacing.WEST};
        for (EnumFacing a : ns) {
            for (EnumFacing b : ew) {
                BlockPos oa = p.offset(a), ob = p.offset(b);
                BlockPos diag = oa.offset(b);
                if (canStand(w, diag)
                        && passable(w, oa) && passable(w, oa.up())
                        && passable(w, ob) && passable(w, ob.up())) {
                    out.add(diag);
                }
            }
        }
        return out;
    }

    /** A feet position you can be in: a ladder holds you, else body+head clear on a full block. */
    public static boolean canStand(World w, BlockPos p) {
        if (isLadder(w, p)) {
            return passable(w, p.up());
        }
        return passable(w, p) && passable(w, p.up()) && canWalkOn(w, p.down());
    }

    /**
     * Can we jump up 1 to stand on {@code fwd}? (fwd is a full block to land on, feet
     * and head clear above it.) The source-side jump headroom is checked by the caller.
     */
    private static boolean canAscend(World w, BlockPos fwd) {
        return canWalkOn(w, fwd)
                && passable(w, fwd.up())        // dest feet
                && passable(w, fwd.up().up());   // dest head (no bonk landing)
    }

    /** True if you can move through this block (no collision box). */
    public static boolean passable(World w, BlockPos p) {
        IBlockState st = w.getBlockState(p);
        return st.getBlock().getCollisionBoundingBox(w, p, st) == null;
    }

    /**
     * How far the player may safely drop, from current health. Fall damage is
     * (blocks - 3) half-hearts, so we allow blocks up to (health - 3) to keep a
     * ~3-heart buffer and never die. Always allows small drops, capped at 20.
     */
    public static int safeFall(EntityPlayer p) {
        int maxFall = (int) Math.floor(p.getHealth()) - 3; // damage(=fall-3) <= health-6
        if (maxFall < 3) {
            maxFall = 3;
        }
        if (maxFall > 20) {
            maxFall = 20;
        }
        return maxFall;
    }

    /** True if this is a full-height solid block you can actually stand on top of. */
    public static boolean canWalkOn(World w, BlockPos p) {
        IBlockState st = w.getBlockState(p);
        AxisAlignedBB box = st.getBlock().getCollisionBoundingBox(w, p, st);
        // Full-height top (maxY at the block's top), so we don't try to "stand on"
        // slabs, fences, walls or air and think we can step onto them.
        return box != null && (box.maxY - p.getY()) >= 0.99;
    }

    public static boolean isLadder(World w, BlockPos p) {
        return w.getBlockState(p).getBlock() == Blocks.ladder;
    }

    /** The direction to push (walk) to climb this ladder: toward the solid wall it's on. */
    public static EnumFacing ladderInto(World w, BlockPos p) {
        IBlockState st = w.getBlockState(p);
        if (st.getBlock() == Blocks.ladder) {
            EnumFacing wall = ((EnumFacing) st.getValue(BlockLadder.FACING)).getOpposite();
            if (!passable(w, p.offset(wall))) {
                return wall;
            }
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

    private static double stepCost(BlockPos a, BlockPos b) {
        int dx = Math.abs(b.getX() - a.getX());
        int dz = Math.abs(b.getZ() - a.getZ());
        int dy = b.getY() - a.getY();
        double horiz = (dx != 0 && dz != 0) ? 1.414 : 1.0; // diagonals cost their real length
        // A drop costs a bit per block so it prefers small drops (and flat) but will
        // still take a big drop over a much longer detour. Cheaper than walking (1.0
        // per block) so a straight drop beats a long way around.
        double vert = dy > 0 ? dy * 1.2 : (dy < 0 ? (-dy) * 0.45 : 0.0);
        return horiz + vert;
    }

    /**
     * Waypoint bookkeeping for the followers: consume every waypoint the player is
     * standing on, looking a few ahead so slightly overshooting a corner recaptures
     * the path instead of turning around to walk back to a waypoint behind us.
     * Returns the first index not yet reached (may equal {@code index}).
     */
    public static int consumeWaypoints(List<BlockPos> path, int index, double px, double py, double pz) {
        int next = index;
        int limit = Math.min(path.size(), index + 4);
        for (int j = index; j < limit; j++) {
            BlockPos wp = path.get(j);
            double dx = (wp.getX() + 0.5) - px;
            double dz = (wp.getZ() + 0.5) - pz;
            if (dx * dx + dz * dz < 0.45 && Math.abs(wp.getY() - py) < 1.3) {
                next = j + 1;
                limit = Math.min(path.size(), j + 4); // extend the window past a hit
            }
        }
        return next;
    }

    /**
     * True if the path from index {@code i} runs straight (same horizontal direction,
     * flat) for at least {@code minSteps} - a stretch worth sprinting.
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
                return false; // keep it flat for a clean sprint-jump
            }
        }
        return true;
    }

    private static double heur(BlockPos a, BlockPos b) {
        return dist(a, b);
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
}
