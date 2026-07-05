package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    // The auto-mine box (Otto's two corners: 34 60 -683 and 55 42 -685). Ladders
    // at x=33 (33 41 -683 / 33 60 -685) let the bot climb out of the mine.
    private static final int MIN_X = 34, MAX_X = 55, MIN_Y = 42, MAX_Y = 60, MIN_Z = -685, MAX_Z = -683;
    // Where mining begins (top corner, first block of the plan). When we're outside
    // the box (e.g. the mine just reset) we walk here before mining.
    private static final BlockPos START = new BlockPos(MIN_X, MAX_Y, MIN_Z);

    private static final double REACH = 4.3;
    private static final double COLLECT_R = 6.0;  // walk over to drops within this
    private static final double IGNORE_NEAR = 1.3; // drops this close auto-collect, don't walk

    // We only detour to collect IRON ORE (the prize), and only iron that WE dropped.
    // Ownership: when an iron item first appears near a block we just broke, we claim
    // it by entity id and follow it by id after that - so other players' iron in the
    // same area is never claimed. Junk (cobble/sandstone/lapis) is not chased; what we
    // walk over is auto-picked and trashed at the Skraldespand later.
    private static final long BREAK_TTL = 5000;    // keep recent breaks this long (for claiming)
    private static final long CLAIM_WINDOW = 2500; // an iron item must appear within this of the break
    private static final double CLAIM_MATCH = 1.7;  // ...and this close to the broken block
    private final Set<Integer> seenItems = new HashSet<Integer>(); // item ids we've judged
    private final Set<Integer> ourIron = new HashSet<Integer>();   // iron item ids claimed as ours

    // If we can't clear a planned block for this long (unreachable/stuck), skip it.
    private static final long SKIP_STUCK = 8000;

    private final Random rng = new Random();

    private boolean holding = false;   // whether we currently hold any keys
    private BlockPos mining = null;    // block being broken
    private BlockPos target = null;    // block we're heading for
    private float tYaw, tPitch;        // smoothed rotation targets
    private long chaseSince = 0;       // when we started walking to the current drop
    private long skipDropsUntil = 0;   // ignore drops until this (breaks a stuck chase)
    private int tick = 0;              // tick counter, for throttling scans
    private EntityItem cachedDrop = null; // last found drop, re-scanned every few ticks

    // Fixed serpentine mining order (built once): forward along Z, step 1 in X,
    // back along Z, and down 1 in Y when a layer is done. Deterministic and
    // reliable, instead of scanning for the nearest block each tick.
    private List<BlockPos> plan = null;
    private int planIndex = 0;
    private long planIndexSince = 0;   // when planIndex last changed (stuck detection)
    private boolean finished = false;  // whole plan cleared - idle until the mine resets

    // Layer completeness: before descending to the next layer, make sure every block
    // in the current layer is actually broken (the serpentine can skip stragglers).
    private int currentLayerY = MAX_Y;
    private int cleanupLayer = Integer.MIN_VALUE; // layer we're currently cleaning up
    private long cleanupSince = 0;                // when cleanup of that layer started

    // Positions of blocks we've broken recently, so we only collect our own drops.
    private final List<long[]> broken = new ArrayList<long[]>(); // {x, y, z, timeMillis}

    // Pickaxe upkeep: keep one equipped; when it breaks and there's no spare, walk
    // to the shop sign and buy a new one.
    private static final BlockPos SHOP_SIGN = new BlockPos(24, 62, -689);
    private boolean buying = false;
    private long buyStart = 0;
    private long lastBuyClick = 0;

    // Inventory jobs done the manual way: actually open the inventory GUI and click,
    // so the server sees real inventory interactions (not slot pokes it might block).
    // Dropping = click the item (pick up), then click outside to drop it.
    // Pickaxe = click the spare pickaxe (pick up), then click a hotbar slot.
    private boolean toggleKeyWasDown = false; // edge-detect the toggle key while a GUI is open

    // The mine resets every ~10 min and teleports us out. A teleport shows up as the
    // position jumping far in a single tick - detect that, restart the pattern from
    // the top, and let the normal return loop walk us back into the mine.
    private static final double TELEPORT_DIST = 8.0;
    private double lastPosX, lastPosY, lastPosZ;
    private boolean lastPosSet = false;

    // The server warns "b will be resetting in 10 seconds." before it refills the
    // mine. We restart the pattern right after that reset lands, so we don't keep
    // grinding at spots that no longer exist and look like a dumb bot.
    private long restartPlanAt = 0;

    // A* path to a far target (shop/deposit/return). Straight-line walking can't get
    // over a 2-high wall (auto-jump only clears 1), so we plan a route that only
    // steps up 1 at a time and follow it waypoint by waypoint.
    private static final int PATH_BUDGET = 20000; // A* node budget for shop/deposit/start routes
    private List<BlockPos> path = null;
    private int pathIndex = 0;
    private BlockPos pathGoal = null;
    private long pathProgressAt = 0;
    private long pathSearchAt = 0;

    // Deposit: the bot never moves items itself (the server flags that). When full,
    // it walks to the Skraldespand, opens it and pings the player to shift-click the
    // junk in by hand, then resumes.
    private static final BlockPos DEPOSIT_SIGN = new BlockPos(55, 61, -691);
    private boolean depositing = false;
    private long lastDepositClick = 0;
    private boolean notifiedDeposit = false;
    private boolean notifiedPickaxe = false;

    // Iron storage: when the bag is full of iron (no junk left to trash), walk to the
    // old drop-off spot and ping the player to store it, then resume once there's room.
    private static final BlockPos IRON_DROP = new BlockPos(20, 60, -684);
    private boolean storingIron = false;
    private boolean notifiedIron = false;

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

        // Let the Auto Mine keybind toggle it on/off even while a GUI is open (e.g.
        // while the deposit chest is open). The normal keybind handler doesn't fire
        // when a screen is open, so poll the key here.
        if (mc.currentScreen != null) {
            pollToggleKey();
        } else {
            toggleKeyWasDown = false;
        }

        if (!CelleScannerMod.config.autoMineEnabled) {
            if (holding) {
                stopAll(mc);
            }
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            releaseKeys(mc);
            if (AutoEat.isEating()) {
                AutoEat.stop(mc);
            }
            return;
        }

        if (mc.currentScreen != null) {
            // A screen is open (the deposit/shop, or the user's own) - stand still
            // and let the player do their thing. We never touch the inventory.
            releaseKeys(mc);
            if (AutoEat.isEating()) {
                AutoEat.stop(mc);
            }
            return;
        }
        holding = true;
        tick++;

        // The mine reset just landed (timed off the chat warning) - restart the
        // pattern so we re-mine the refilled mine from the top.
        if (restartPlanAt > 0 && System.currentTimeMillis() >= restartPlanAt) {
            restartPlan(mc);
            restartPlanAt = 0;
        }

        // Teleported (mine reset also yanks us out)? Restart the pattern too; the
        // "outside the box" branch in doMine then walks us back to the start corner.
        if (lastPosSet) {
            double mdx = mc.thePlayer.posX - lastPosX;
            double mdy = mc.thePlayer.posY - lastPosY;
            double mdz = mc.thePlayer.posZ - lastPosZ;
            if (mdx * mdx + mdy * mdy + mdz * mdz > TELEPORT_DIST * TELEPORT_DIST) {
                restartPlan(mc);
            }
        }
        lastPosX = mc.thePlayer.posX;
        lastPosY = mc.thePlayer.posY;
        lastPosZ = mc.thePlayer.posZ;
        lastPosSet = true;

        applyRotation(mc); // every tick, for smooth turning

        if (AutoEat.tick(mc)) {
            stopMining(mc);
            stopWalk(mc);
            return; // eating - hold still until we're fed, then resume
        }

        if (buying) {
            doBuy(mc);
            return; // at the shop - hold off on mining
        }
        // Keep a pickaxe in hand. Select a hotbar one if we have it. We never move
        // items ourselves, so if the only spare is in the main inventory we ask the
        // player to slot it; if there's none at all we go to the shop.
        if (!holdingPickaxe(mc) && !selectHotbarPickaxe(mc)) {
            if (hasPickaxe(mc)) {
                stopMining(mc);
                stopWalk(mc);
                notifyPickaxe(mc);
                return;
            }
            buying = true;
            buyStart = System.currentTimeMillis();
            doBuy(mc);
            return;
        }
        notifiedPickaxe = false;

        // Full-inventory routing: trash junk at the Skraldespand first (frees space,
        // keeps iron); once it's pure iron and full, store it at the drop-off spot.
        boolean full = inventoryFull(mc);
        if (depositing) {
            if (full && hasJunk(mc)) {
                doDeposit(mc);
                return;
            }
            depositing = false;
            notifiedDeposit = false;
        }
        if (storingIron) {
            if (full && !hasJunk(mc)) {
                doStoreIron(mc);
                return;
            }
            storingIron = false;
            notifiedIron = false;
        }
        if (full && hasJunk(mc)) {
            depositing = true;
            doDeposit(mc);
        } else if (full) {
            storingIron = true;
            doStoreIron(mc);
        } else {
            doMine(mc);
        }
    }

    /** Draw the bot's planned route (to the shop/deposit/start) as a line on the floor. */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!CelleScannerMod.config.autoMineEnabled || path == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            Pathfinder.renderPath(path, mc.thePlayer, event.partialTicks, 0.35f, 0.9f, 1.0f);
        }
    }

    /** The mine resets every ~10 min; the server warns in chat first. Arm a restart. */
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!CelleScannerMod.config.autoMineEnabled || event.message == null) {
            return;
        }
        String msg = event.message.getUnformattedText();
        if (msg != null && msg.toLowerCase().contains("will be resetting")) {
            // Reset lands ~10s after the warning; restart just after so the mine has
            // refilled (add a little buffer).
            restartPlanAt = System.currentTimeMillis() + 10500;
        }
    }

    /** Restart the mining pattern from the top and drop any stale travel/target state. */
    private void restartPlan(Minecraft mc) {
        stopMining(mc);
        planIndex = 0;
        finished = false;
        planIndexSince = System.currentTimeMillis();
        currentLayerY = MAX_Y;
        target = null;
        cachedDrop = null;
        clearPath();
    }

    private void doMine(Minecraft mc) {
        recordBroken(mc); // note blocks we just finished breaking (for our-drop matching)
        claimDrops(mc);   // claim iron that came out of our breaks

        // Ladder recovery: if we've slid down a ladder and are stuck on it, pathfind
        // back to the start corner (that climbs us out via the ladder), then resume
        // mining where we left off - planIndex is preserved.
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));
        if (Pathfinder.isLadder(mc.theWorld, feet) || Pathfinder.isLadder(mc.theWorld, feet.up())) {
            stopMining(mc);
            navigate(mc, START, 2.0);
            return;
        }

        // Next block in the fixed serpentine order (computed first, so the reset
        // check below runs no matter where we're standing).
        target = planTarget(mc);
        if (target == null) {
            // Whole plan cleared - idle. Every second, if the mine reset (blocks are
            // back), start the pattern over from the top. Checked from anywhere, so
            // it resumes even when we're standing outside the box on a reset.
            stopMining(mc);
            stopWalk(mc);
            if (tick % 20 == 0 && mineIsFull(mc)) {
                planIndex = 0;
                finished = false;
                planIndexSince = System.currentTimeMillis();
                currentLayerY = MAX_Y;
            }
            return;
        }

        // Layer completeness: before descending to a lower layer, sweep the layer we
        // just finished for any straggler blocks (skipped ones, or left by a player)
        // and clear them first. Give up on a layer after a while so we can't get
        // wedged on an unreachable straggler.
        int ty = target.getY();
        if (ty < currentLayerY) {
            BlockPos leftover = leftoverInLayer(mc, currentLayerY);
            if (leftover != null && !cleanupTimedOut(currentLayerY)) {
                target = leftover; // mine the straggler before descending
            } else {
                currentLayerY = ty; // layer clean (or timed out) - descend
            }
        } else if (ty > currentLayerY) {
            currentLayerY = ty; // went up (e.g. after a reset)
        }

        // Outside the mine outline (e.g. after buying a pickaxe, depositing, or a
        // reset teleport) - pathfind back to the start corner (routes around walls
        // it can't just jump).
        if (!nearBox(mc, 2)) {
            stopMining(mc);
            navigate(mc, START, 2.0);
            return;
        }
        clearPath(); // inside the box - drop any travel path

        // Sweep up OUR dropped items before mining more, so nothing is left behind.
        // The facing gate keeps this from wandering, and we aim down at the item
        // (it's on the ground) so the head never levels/looks up like it used to.
        EntityItem drop = System.currentTimeMillis() < skipDropsUntil ? null : currentDrop(mc);
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

        aimAt(mc, target);

        // Mine whatever in-box block is under the crosshair if it's in reach - keyed
        // on the LOOKED-AT block, not the target's distance. That way, if we come
        // back on a reset and blocks are right in front of us (even buried), we dig
        // them instead of only trying to walk to the far target and getting stuck.
        MovingObjectPosition mop = mc.objectMouseOver;
        BlockPos looked = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? mop.getBlockPos() : null;
        if (looked != null && inBox(looked) && !mc.theWorld.isAirBlock(looked) && eyeDist(mc, looked) <= REACH + 0.5) {
            stopWalk(mc);
            mineLookedAt(mc, looked, mop.sideHit);
        } else if (eyeDist(mc, target) > REACH + 0.3) {
            // Nothing minable in reach under the crosshair - step toward the target.
            stopMining(mc);
            approach(mc, target.getX() + 0.5, target.getZ() + 0.5);
        } else {
            // Target is close but the crosshair hasn't settled on a block - wait.
            stopWalk(mc);
            stopMining(mc);
        }
    }

    /**
     * Walk toward (x,z) but only once we're roughly facing that direction, and
     * auto-jump when we bump a wall. Rotating-while-walking is what makes a bot
     * drift and circle, so we gate movement on the yaw being close first.
     */
    private void approach(Minecraft mc, double x, double z) {
        approach(mc, x, z, false);
    }

    /** As approach, but sprint + sprint-jump (bunny-hop) on a long straight stretch. */
    private void approach(Minecraft mc, double x, double z, boolean sprint) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(wantYaw - mc.thePlayer.rotationYaw));
        if (diff < WALK_ANGLE) {
            walkForward(mc);
            boolean sprinting = sprint && diff < 12f; // only sprint when well-lined-up
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprinting);
            if (sprinting) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), mc.thePlayer.onGround);
            }
        } else {
            stopWalk(mc);
        }
    }

    /**
     * Route to within {@code reach} of {@code goal} using a planned path (so it goes
     * around/over 2-high walls and up ladders), following it waypoint by waypoint.
     * While no path is found yet it stands still and keeps searching - it never walks
     * blindly straight at the goal.
     */
    private void navigate(Minecraft mc, BlockPos goal, double reach) {
        World w = mc.theWorld;
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        long now = System.currentTimeMillis();
        if (pathGoal == null || !pathGoal.equals(goal)) {
            // New destination - search right away.
            path = Pathfinder.findPath(w, feet, goal, reach, PATH_BUDGET);
            pathGoal = goal;
            pathIndex = 0;
            pathSearchAt = now;
            if (path != null) {
                pathProgressAt = now;
            }
        } else if ((path == null || now - pathProgressAt > 3000) && now - pathSearchAt >= 1000) {
            // No path yet, or stuck - re-search (throttled so the A* isn't run every tick).
            path = Pathfinder.findPath(w, feet, goal, reach, PATH_BUDGET);
            pathSearchAt = now;
            pathIndex = 0;
            if (path != null) {
                pathProgressAt = now;
            }
        }

        if (path == null || path.isEmpty()) {
            // No route yet - stand still and keep searching, don't walk blindly.
            stopWalk(mc);
            return;
        }

        // Drop waypoints we've reached.
        while (pathIndex < path.size()) {
            BlockPos wp = path.get(pathIndex);
            double dx = (wp.getX() + 0.5) - mc.thePlayer.posX;
            double dz = (wp.getZ() + 0.5) - mc.thePlayer.posZ;
            if (dx * dx + dz * dz < 0.45 && Math.abs(wp.getY() - mc.thePlayer.posY) < 1.3) {
                pathIndex++;
                pathProgressAt = System.currentTimeMillis();
            } else {
                break;
            }
        }
        if (pathIndex >= path.size()) {
            // Consumed a (possibly partial) path but not there yet - re-plan from here.
            stopWalk(mc);
            pathProgressAt = 0; // force a fresh search next allowed tick
            return;
        }
        BlockPos step = path.get(pathIndex);

        // Climbing a ladder to get out: face into the ladder's wall and hold forward
        // (pushing into it is what makes you climb up), instead of trying to walk.
        boolean up = step.getY() > MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos ladderPos = Pathfinder.isLadder(w, feet) ? feet : (Pathfinder.isLadder(w, feet.up()) ? feet.up()
                : (Pathfinder.isLadder(w, step) ? step : null));
        if (up && ladderPos != null) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            if (into != null) {
                // Snap to face the wall and push in EVERY tick. On a ladder, any tick
                // we're not pushing into the wall we slide back down, so we can't
                // afford an eased turn or a facing gate here.
                float y = Pathfinder.yawOf(into);
                mc.thePlayer.rotationYaw = y;
                tYaw = y;
                tPitch = 0f;
                climbForward(mc); // forward into the wall = climb (NO jumping)
                return;
            }
        }

        aimAt(mc, step);
        approach(mc, step.getX() + 0.5, step.getZ() + 0.5, Pathfinder.straightRun(path, pathIndex, 4));
    }

    private void clearPath() {
        path = null;
        pathGoal = null;
        pathIndex = 0;
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

    private boolean holdingPickaxe(Minecraft mc) {
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        return held != null && held.getItem() instanceof ItemPickaxe;
    }

    /** Select a hotbar slot that holds a pickaxe (just a held-item change), or false. */
    private boolean selectHotbarPickaxe(Minecraft mc) {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemPickaxe) {
                mc.thePlayer.inventory.currentItem = i;
                return true;
            }
        }
        return false;
    }

    private boolean hasPickaxe(Minecraft mc) {
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s != null && s.getItem() instanceof ItemPickaxe) {
                return true;
            }
        }
        return false;
    }

    /** Poll the Auto Mine keybind while a GUI is open, so it can be switched off there. */
    private void pollToggleKey() {
        if (CelleScannerMod.autoMineKey == null) {
            return;
        }
        int kc = CelleScannerMod.autoMineKey.getKeyCode();
        boolean down = kc != 0 && Keyboard.isKeyDown(kc);
        if (down && !toggleKeyWasDown) {
            CelleActions.toggleAutoMine();
        }
        toggleKeyWasDown = down;
    }

    /**
     * Walk to the shop sign and right-click it to buy a pickaxe. As soon as a
     * pickaxe shows up in the inventory we're done (it gets equipped next tick).
     * If we can't buy one within 20s (e.g. no money) we switch Auto Mine off and
     * say so, rather than stand there forever.
     */
    private void doBuy(Minecraft mc) {
        stopMining(mc);

        if (hasPickaxe(mc)) {
            buying = false;
            stopWalk(mc);
            return;
        }
        if (System.currentTimeMillis() - buyStart > 20000) {
            buying = false;
            stopAll(mc);
            CelleScannerMod.config.autoMineEnabled = false;
            CelleScannerMod.config.save();
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§cAuto Mine: kunne ikke købe en ny hakke - slukket."));
            return;
        }

        if (eyeDist(mc, SHOP_SIGN) > 3.3) {
            navigate(mc, SHOP_SIGN, 2.5); // pathfind over/around obstacles
            return;
        }
        stopWalk(mc);
        clearPath();
        aimAt(mc, SHOP_SIGN);

        // Aimed at the sign and in reach - right-click it (throttled).
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && SHOP_SIGN.equals(mop.getBlockPos())
                && System.currentTimeMillis() - lastBuyClick > 800) {
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.getCurrentEquippedItem(), SHOP_SIGN, mop.sideHit, mop.hitVec);
            mc.thePlayer.swingItem();
            lastBuyClick = System.currentTimeMillis();
        }
    }

    /**
     * Walk to the Skraldespand, open the deposit chest (a normal block interaction),
     * then stand by and ping the player to shift-click the junk in. We never move
     * items ourselves - that's what the server flags - so the player does that part.
     */
    private void doDeposit(Minecraft mc) {
        stopMining(mc);
        if (eyeDist(mc, DEPOSIT_SIGN) > 3.3) {
            navigate(mc, DEPOSIT_SIGN, 2.5); // pathfind over/around obstacles
            return;
        }
        stopWalk(mc);
        clearPath();
        aimAt(mc, DEPOSIT_SIGN);

        // In reach - right-click the sign to open the Diposit chest (throttled), then
        // ping the player. Once the chest opens, onTick pauses us (screen open) until
        // they've deposited and closed it.
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && DEPOSIT_SIGN.equals(mop.getBlockPos())
                && System.currentTimeMillis() - lastDepositClick > 1000) {
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.getCurrentEquippedItem(), DEPOSIT_SIGN, mop.sideHit, mop.hitVec);
            mc.thePlayer.swingItem();
            lastDepositClick = System.currentTimeMillis();
        }
        notifyDeposit(mc);
    }

    private void notifyDeposit(Minecraft mc) {
        if (!notifiedDeposit) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§eAuto Mine: inventory fuldt - shift-klik dit skrald i Skraldespanden, så fortsætter jeg."));
            mc.thePlayer.playSound("random.orb", 1f, 1f);
            notifiedDeposit = true;
        }
    }

    /** Bag is full of iron: walk to the drop-off spot and ping the player to store it. */
    private void doStoreIron(Minecraft mc) {
        stopMining(mc);
        double dx = (IRON_DROP.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (IRON_DROP.getZ() + 0.5) - mc.thePlayer.posZ;
        if (Math.sqrt(dx * dx + dz * dz) > 1.8) {
            navigate(mc, IRON_DROP, 2.0);
            return;
        }
        stopWalk(mc);
        clearPath();
        notifyIron(mc); // then just wait until the player frees space
    }

    private void notifyIron(Minecraft mc) {
        if (!notifiedIron) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§eAuto Mine: inventory fuldt af jern - opbevar det her (20 60 -684), så fortsætter jeg."));
            mc.thePlayer.playSound("random.orb", 1f, 1f);
            notifiedIron = true;
        }
    }

    private void notifyPickaxe(Minecraft mc) {
        if (!notifiedPickaxe) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    "§eAuto Mine: læg en hakke i hotbaren, så fortsætter jeg."));
            mc.thePlayer.playSound("random.orb", 1f, 1f);
            notifiedPickaxe = true;
        }
    }

    /** Cobblestone, sandstone and lapis are junk; everything else (picks, iron ore) is kept. */
    private boolean isJunk(ItemStack s) {
        if (s == null) {
            return false;
        }
        Item it = s.getItem();
        return it == Item.getItemFromBlock(Blocks.cobblestone)
                || it == Item.getItemFromBlock(Blocks.sandstone)
                || it == Item.getItemFromBlock(Blocks.lapis_block)
                || (it == Items.dye && s.getMetadata() == 4); // lapis lazuli
    }

    private boolean hasJunk(Minecraft mc) {
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (isJunk(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Build the serpentine mining order once: top layer to bottom (Y down), and
     * within each layer snake along Z, stepping 1 in X between rows. That gives
     * the "mine forward, step 1 to the side, mine back, drop a layer, repeat"
     * pattern.
     */
    private void buildPlan() {
        plan = new ArrayList<BlockPos>();
        boolean back = false;
        for (int y = MAX_Y; y >= MIN_Y; y--) {
            for (int x = MIN_X; x <= MAX_X; x++) {
                if (!back) {
                    for (int z = MIN_Z; z <= MAX_Z; z++) {
                        plan.add(new BlockPos(x, y, z));
                    }
                } else {
                    for (int z = MAX_Z; z >= MIN_Z; z--) {
                        plan.add(new BlockPos(x, y, z));
                    }
                }
                back = !back; // snake: forward then back on the next row
            }
        }
    }

    /** The current block to mine in plan order, skipping ones already cleared. */
    private BlockPos planTarget(Minecraft mc) {
        if (plan == null) {
            buildPlan();
            planIndexSince = System.currentTimeMillis();
        }
        if (finished) {
            return null;
        }
        // If we've been stuck on one block too long (can't reach it), skip past it.
        if (System.currentTimeMillis() - planIndexSince > SKIP_STUCK) {
            advanceIndex();
        }
        while (planIndex < plan.size()) {
            BlockPos p = plan.get(planIndex);
            if (!mc.theWorld.isAirBlock(p)) {
                return p;
            }
            advanceIndex();
        }
        finished = true;
        return null;
    }

    private void advanceIndex() {
        planIndex++;
        planIndexSince = System.currentTimeMillis();
    }

    /** First non-air in-box block on layer y (a straggler to clean up), or null. */
    private BlockPos leftoverInLayer(Minecraft mc, int y) {
        if (y < MIN_Y || y > MAX_Y) {
            return null;
        }
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!mc.theWorld.isAirBlock(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** True once we've been cleaning up layer y too long (probably an unreachable straggler). */
    private boolean cleanupTimedOut(int y) {
        long now = System.currentTimeMillis();
        if (cleanupLayer != y) {
            cleanupLayer = y;
            cleanupSince = now;
        }
        return now - cleanupSince > 6000;
    }

    /**
     * True when the mine has clearly refilled (a reset), not just a few leftover
     * blocks we skipped. Requires at least half of the sampled positions to be
     * solid, so stray skipped blocks don't retrigger the pattern in a loop.
     */
    private boolean mineIsFull(Minecraft mc) {
        if (plan == null || plan.isEmpty()) {
            return false;
        }
        int step = Math.max(1, plan.size() / 12);
        int solid = 0, total = 0;
        for (int i = 0; i < plan.size(); i += step) {
            total++;
            if (!mc.theWorld.isAirBlock(plan.get(i))) {
                solid++;
            }
        }
        return total > 0 && solid >= (total + 1) / 2;
    }

    /** Record blocks we finished breaking, and drop entries older than BREAK_TTL. */
    private void recordBroken(Minecraft mc) {
        if (mining != null && mc.theWorld.isAirBlock(mining)) {
            broken.add(new long[]{mining.getX(), mining.getY(), mining.getZ(), System.currentTimeMillis()});
            mining = null;
        }
        long now = System.currentTimeMillis();
        for (Iterator<long[]> it = broken.iterator(); it.hasNext(); ) {
            if (now - it.next()[3] > BREAK_TTL) {
                it.remove();
            }
        }
    }

    /** Iron ore (the prize). Vanilla 1.8.9 iron_ore block. */
    private boolean isIronDrop(EntityItem e) {
        ItemStack s = e.getEntityItem();
        return s != null && s.getItem() == Item.getItemFromBlock(Blocks.iron_ore);
    }

    /**
     * Claim iron items that just came out of a block WE broke, by entity id. Once
     * claimed we follow them by id, so another player's iron near our spot is never
     * taken. Also prunes ids for items that despawned or got picked up.
     */
    private void claimDrops(Minecraft mc) {
        long now = System.currentTimeMillis();
        Set<Integer> present = new HashSet<Integer>();
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityItem)) {
                continue;
            }
            EntityItem it = (EntityItem) o;
            int id = it.getEntityId();
            present.add(id);
            if (seenItems.add(id) && isIronDrop(it) && bornFromOurBreak(it, now)) {
                ourIron.add(id); // fresh iron from our break -> it's ours
            }
        }
        seenItems.retainAll(present);
        ourIron.retainAll(present);
    }

    private boolean bornFromOurBreak(EntityItem it, long now) {
        for (long[] b : broken) {
            if (now - b[3] > CLAIM_WINDOW) {
                continue;
            }
            double dx = it.posX - (b[0] + 0.5);
            double dz = it.posZ - (b[2] + 0.5);
            double dy = it.posY - b[1];
            if (dx * dx + dz * dz <= CLAIM_MATCH * CLAIM_MATCH && Math.abs(dy) <= 2.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * A drop to walk to, re-scanning only every 5 ticks (4x/sec). We only ever detour
     * for iron we've claimed as ours; junk is never chased.
     */
    private EntityItem currentDrop(Minecraft mc) {
        if (cachedDrop == null || cachedDrop.isDead || tick % 5 == 0) {
            cachedDrop = findItem(mc);
        }
        return cachedDrop;
    }

    /** Nearest of OUR iron drops worth walking to, or null. */
    private EntityItem findItem(Minecraft mc) {
        if (ourIron.isEmpty()) {
            return null;
        }
        double px = mc.thePlayer.posX, py = mc.thePlayer.posY, pz = mc.thePlayer.posZ;
        EntityItem best = null;
        double bestD = COLLECT_R * COLLECT_R;
        for (Object o : mc.theWorld.loadedEntityList) {
            if (!(o instanceof EntityItem)) {
                continue;
            }
            EntityItem it = (EntityItem) o;
            if (!ourIron.contains(it.getEntityId())) {
                continue;
            }
            double dx = it.posX - px, dz = it.posZ - pz;
            // Iron right next to us gets picked up automatically - don't walk for it.
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

    /**
     * Ease the player's look toward the target with a capped step, so it turns
     * smoothly instead of snapping. No per-tick jitter: that made the crosshair
     * wobble across block edges every tick, which kept restarting the break on a
     * new block and spamming digging particles - the main auto-mine FPS killer.
     * The eased (non-linear, slightly randomised speed) turn is what keeps it from
     * looking snappy.
     */
    private void applyRotation(Minecraft mc) {
        float dy = MathHelper.wrapAngleTo180_float(tYaw - mc.thePlayer.rotationYaw);
        float dp = tPitch - mc.thePlayer.rotationPitch;
        float stepY = dy * (0.28f + rng.nextFloat() * 0.12f);
        float stepP = dp * (0.28f + rng.nextFloat() * 0.12f);
        float cap = 11f + rng.nextFloat() * 5f;
        stepY = clamp(stepY, -cap, cap);
        stepP = clamp(stepP, -cap, cap);
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

    /**
     * Climb a ladder the 1.8.9 way: press forward with NO jump. You climb by pushing
     * into the wall the ladder is on (which we're facing); jumping does nothing on a
     * ladder and just hops you off at the base.
     */
    private void climbForward(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
    }

    private void stopWalk(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
    }

    private void releaseKeys(Minecraft mc) {
        stopWalk(mc);
        stopMining(mc);
    }

    private void stopAll(Minecraft mc) {
        releaseKeys(mc);
        if (AutoEat.isEating()) {
            AutoEat.stop(mc);
        }
        target = null;
        cachedDrop = null;
        chaseSince = 0;
        depositing = false;
        notifiedDeposit = false;
        notifiedPickaxe = false;
        storingIron = false;
        notifiedIron = false;
        buying = false;
        lastPosSet = false; // don't treat the next re-enable as a teleport
        clearPath();
        // Restart the pattern from the top next time it's switched on.
        planIndex = 0;
        finished = false;
        planIndexSince = System.currentTimeMillis();
        currentLayerY = MAX_Y;
        broken.clear();
        seenItems.clear();
        ourIron.clear();
        holding = false;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
