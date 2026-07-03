package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    // The auto-mine box (Otto's two corners).
    private static final int MIN_X = 37, MAX_X = 52, MIN_Y = 42, MAX_Y = 60, MIN_Z = -692, MAX_Z = -677;

    private static final double REACH = 4.3;
    private static final double COLLECT_R = 6.0;  // walk over to drops within this
    private static final double IGNORE_NEAR = 1.3; // drops this close auto-collect, don't walk

    // Only collect drops that came from blocks WE broke: an item counts as ours if
    // it sits near a block we broke in the last DROP_TTL. Keeps us from chasing
    // other players' drops in the same mine.
    private static final long DROP_TTL = 20000;
    private static final double DROP_MATCH = 1.9;

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

    // Positions of blocks we've broken recently, so we only collect our own drops.
    private final List<long[]> broken = new ArrayList<long[]>(); // {x, y, z, timeMillis}

    // When the inventory is full we turn aside and throw out the junk (cobblestone,
    // sandstone, lapis), keeping pickaxes, iron ore and everything else, then mine on.
    private boolean dumping = false;
    private float dumpYaw = 0f;

    // Auto-eat so we don't starve while mining. Eat when hunger drops to EAT_BELOW,
    // stop when full (or out of food).
    private static final int EAT_BELOW = 16;
    private static final int EAT_FULL = 20;
    private boolean eating = false;
    private int prevSlot = -1; // hotbar slot to switch back to after eating

    // Pickaxe upkeep: keep one equipped; when it breaks and there's no spare, walk
    // to the shop sign and buy a new one.
    private static final BlockPos SHOP_SIGN = new BlockPos(24, 62, -689);
    private boolean buying = false;
    private long buyStart = 0;
    private long lastBuyClick = 0;

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
            if (eating) {
                stopEating(mc);
            }
            return;
        }
        holding = true;
        tick++;

        applyRotation(mc); // every tick, for smooth turning

        if (doEat(mc)) {
            return; // eating - hold still until we're fed, then resume
        }

        if (buying) {
            doBuy(mc);
            return; // buying a pickaxe - hold off on mining
        }
        if (!ensurePickaxeHeld(mc)) {
            // Pickaxe broke and there's no spare - go buy one.
            buying = true;
            buyStart = System.currentTimeMillis();
            doBuy(mc);
            return;
        }

        if (dumping) {
            // Keep dumping until all the junk is gone, then get back to mining.
            if (hasJunk(mc)) {
                doDump(mc);
            } else {
                dumping = false;
                doMine(mc);
            }
        } else if (inventoryFull(mc) && hasJunk(mc)) {
            dumping = true;
            dumpYaw = mc.thePlayer.rotationYaw + 90f; // turn aside to throw it out
            doDump(mc);
        } else {
            doMine(mc);
        }
    }

    private void doMine(Minecraft mc) {
        recordBroken(mc); // note blocks we just finished breaking (for our-drop matching)

        if (!nearBox(mc, 3)) {
            // Walk back to the box; only move once we're facing it.
            stopMining(mc);
            approach(mc, (MIN_X + MAX_X) / 2.0 + 0.5, (MIN_Z + MAX_Z) / 2.0 + 0.5);
            return;
        }

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

        // Next block in the fixed serpentine order.
        target = planTarget(mc);
        if (target == null) {
            // Whole plan cleared - idle. Every second, if the mine reset (blocks
            // are back), start the pattern over from the top.
            stopMining(mc);
            stopWalk(mc);
            if (tick % 20 == 0 && plan != null && !plan.isEmpty() && !mc.theWorld.isAirBlock(plan.get(0))) {
                planIndex = 0;
                finished = false;
                planIndexSince = System.currentTimeMillis();
            }
            return;
        }

        aimAt(mc, target);
        double dist = eyeDist(mc, target);

        if (dist <= REACH + 0.3) {
            // In reach: never walk. Mine whatever in-box block the crosshair is
            // actually on (clears anything between us and the target), so the
            // server accepts it and it doesn't ghost-revert.
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

    /**
     * Auto-eat: when hunger drops, hold still and eat food from the hotbar (moving
     * a food item into the hotbar first if needed) until full. Returns true while
     * eating, so mining pauses. Never touches pickaxes/ore - only ItemFood.
     */
    private boolean doEat(Minecraft mc) {
        int food = mc.thePlayer.getFoodStats().getFoodLevel();
        if (!eating) {
            if (food > EAT_BELOW) {
                return false;
            }
            int slot = findFoodSlot(mc);
            if (slot < 0) {
                return false; // no food anywhere - nothing we can do
            }
            eating = true;
            prevSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
        }

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean holdingFood = held != null && held.getItem() instanceof ItemFood;
        if (food >= EAT_FULL) {
            stopEating(mc);
            return false;
        }
        if (!holdingFood) {
            // Ate the whole stack - grab another food if we still need it.
            int slot = findFoodSlot(mc);
            if (slot < 0) {
                stopEating(mc);
                return false;
            }
            mc.thePlayer.inventory.currentItem = slot;
        }

        // Hold still and hold right-click to eat.
        stopMining(mc);
        stopWalk(mc);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        return true;
    }

    private void stopEating(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (prevSlot >= 0) {
            mc.thePlayer.inventory.currentItem = prevSlot;
            prevSlot = -1;
        }
        eating = false;
    }

    /** A hotbar slot holding food; moves a food item into the hotbar if needed, or -1. */
    private int findFoodSlot(Minecraft mc) {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemFood) {
                return i;
            }
        }
        // No food in the hotbar - swap one in from the main inventory (into slot 8).
        for (int i = 9; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemFood) {
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 8, 2, mc.thePlayer);
                return 8;
            }
        }
        return -1;
    }

    /**
     * Make sure a pickaxe is held for mining. If the held item isn't a pickaxe
     * (e.g. the last one just broke), equip one from the hotbar, or move a spare
     * up from the main inventory. Returns false when there's no pickaxe at all.
     */
    private boolean ensurePickaxeHeld(Minecraft mc) {
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held != null && held.getItem() instanceof ItemPickaxe) {
            return true;
        }
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemPickaxe) {
                mc.thePlayer.inventory.currentItem = i;
                return true;
            }
        }
        for (int i = 9; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemPickaxe) {
                // Swap the spare into hotbar slot 0 and hold it.
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, i, 0, 2, mc.thePlayer);
                mc.thePlayer.inventory.currentItem = 0;
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

        aimAt(mc, SHOP_SIGN);
        if (eyeDist(mc, SHOP_SIGN) > 3.3) {
            approach(mc, SHOP_SIGN.getX() + 0.5, SHOP_SIGN.getZ() + 0.5);
            return;
        }
        stopWalk(mc);

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

    /** Stand still, look to the side, and throw out junk stacks one at a time. */
    private void doDump(Minecraft mc) {
        stopMining(mc);
        stopWalk(mc);
        tYaw = dumpYaw;
        tPitch = 12f;
        // One stack every other tick - fast enough, but not a 20/sec burst.
        if (tick % 2 == 0) {
            dropOneJunkStack(mc);
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

    /** Throw out the first junk stack found (whole stack), server-side via a window click. */
    private void dropOneJunkStack(Minecraft mc) {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < inv.length; i++) {
            if (isJunk(inv[i])) {
                // mainInventory 0-8 (hotbar) map to container slots 36-44; 9-35 stay.
                int slot = i < 9 ? i + 36 : i;
                // mode 4 = throw, button 1 = whole stack (like ctrl+Q).
                mc.playerController.windowClick(mc.thePlayer.inventoryContainer.windowId, slot, 1, 4, mc.thePlayer);
                return;
            }
        }
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

    /** Record blocks we finished breaking, and drop entries older than DROP_TTL. */
    private void recordBroken(Minecraft mc) {
        if (mining != null && mc.theWorld.isAirBlock(mining)) {
            broken.add(new long[]{mining.getX(), mining.getY(), mining.getZ(), System.currentTimeMillis()});
            mining = null;
        }
        long now = System.currentTimeMillis();
        for (Iterator<long[]> it = broken.iterator(); it.hasNext(); ) {
            if (now - it.next()[3] > DROP_TTL) {
                it.remove();
            }
        }
    }

    /** True if this item sits near a block we broke recently (i.e. it's our drop). */
    private boolean isOurDrop(EntityItem it) {
        for (long[] b : broken) {
            double dx = it.posX - (b[0] + 0.5);
            double dz = it.posZ - (b[2] + 0.5);
            double dy = it.posY - b[1];
            if (dx * dx + dz * dz <= DROP_MATCH * DROP_MATCH && Math.abs(dy) <= 2.0) {
                return true;
            }
        }
        return false;
    }

    /**
     * A drop to walk to, re-scanning the entity list only every 5 ticks (4x/sec)
     * instead of every tick. Scanning every loaded entity 20x/sec is wasted work
     * on a busy server; 4x/sec is plenty to notice and collect drops. Between
     * scans we reuse the cached drop as long as it's still alive.
     */
    private EntityItem currentDrop(Minecraft mc) {
        if (cachedDrop == null || cachedDrop.isDead || tick % 5 == 0) {
            cachedDrop = findItem(mc);
        }
        return cachedDrop;
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
            // Only our own drops (from blocks we broke), not other players'.
            if (!isOurDrop(it)) {
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
        if (eating) {
            stopEating(mc);
        }
        target = null;
        cachedDrop = null;
        chaseSince = 0;
        dumping = false;
        buying = false;
        // Restart the pattern from the top next time it's switched on.
        planIndex = 0;
        finished = false;
        planIndexSince = System.currentTimeMillis();
        broken.clear();
        holding = false;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
