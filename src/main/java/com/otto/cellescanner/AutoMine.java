package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Auto Mine addon (automation - off by default, use only where the server
 * allows it). Mines a fixed mine box block by block, walks to a deposit point
 * when the inventory is full, then comes back. When the box is mined out it
 * just
 * idles until the mine resets and blocks reappear, so it resumes on its own.
 *
 * Movement and aiming are deliberately smooth and slightly varied (eased turns,
 * jitter, small pauses) rather than snappy, so it doesn't twitch like a bot.
 * v1: expect to tune the pattern in-game.
 *
 * NOTE: navigate() below and PathWalker.tickWalk() share the same path-follow
 * shape (waypoint advance, ladder climb, sprint gating) on purpose - each is
 * tuned separately and both are known-good, so the duplication is left alone.
 * See the matching note in PathWalker.
 */
public class AutoMine {

    public static AutoMine INSTANCE;

    public AutoMine() {
        INSTANCE = this;
    }

    // The default auto-mine box (Otto's two corners: 34 60 -683 and 55 42 -685).
    // Ladders at x=33 (33 41 -683 / 33 60 -685) let the bot climb out of the mine.
    // Used when the player hasn't set a custom area (config.mineAreaSet == false).
    private static final int[] DEFAULT_BOX = { 34, 55, 42, 60, -685, -683 }; // minX,maxX,minY,maxY,minZ,maxZ

    // The live box the bot mines. Filled from DEFAULT_BOX or the player's custom
    // area by refreshBox(), which runs before each plan build and each mine tick.
    private int MIN_X = DEFAULT_BOX[0], MAX_X = DEFAULT_BOX[1],
            MIN_Y = DEFAULT_BOX[2], MAX_Y = DEFAULT_BOX[3],
            MIN_Z = DEFAULT_BOX[4], MAX_Z = DEFAULT_BOX[5];
    // Where mining begins (top corner, first block of the plan). When we're outside
    // the box (e.g. the mine just reset) we walk here before mining.
    private BlockPos START = new BlockPos(MIN_X, MAX_Y, MIN_Z);

    // "Set area" mode: after the player clicks Set Area in the GUI, the next two
    // right-clicks on blocks become the two corners. Static so the GUI can flip it
    // on; the registered AutoMine instance reads it in onMouseInput.
    public static boolean setAreaMode = false;
    private static BlockPos pendingCorner1 = null;

    private static final double REACH = 4.3;
    private static final double COLLECT_R = 6.0; // walk over to drops within this
    private static final double IGNORE_NEAR = 1.3; // drops this close auto-collect, don't walk

    // We only detour to collect IRON ORE (the prize), and only iron that WE
    // dropped.
    // Ownership: when an iron item first appears near a block we just broke, we
    // claim
    // it by entity id and follow it by id after that - so other players' iron in
    // the
    // same area is never claimed. Junk (cobble/sandstone/lapis) is not chased; what
    // we
    // walk over is auto-picked and trashed at the Skraldespand later.
    private static final long BREAK_TTL = 5000; // keep recent breaks this long (for claiming)
    private static final long CLAIM_WINDOW = 2500; // an iron item must appear within this of the break
    private static final double CLAIM_MATCH = 1.7; // ...and this close to the broken block
    private final Set<Integer> seenItems = new HashSet<Integer>(); // item ids we've judged
    private final Set<Integer> ourIron = new HashSet<Integer>(); // iron item ids claimed as ours

    // If we can't clear a planned block for this long (unreachable/stuck), skip it.
    private static final long SKIP_STUCK = 8000;

    private final Random rng = new Random();

    private enum Phase {
        DESTINATION,
        MINING
    }
    private Phase phase = Phase.DESTINATION;

    private boolean holding = false; // whether we currently hold any keys
    private BlockPos mining = null; // block being broken
    private BlockPos target = null; // block we're heading for
    private float tYaw, tPitch; // smoothed rotation targets
    private long chaseSince = 0; // when we started walking to the current drop
    private long skipDropsUntil = 0; // ignore drops until this (breaks a stuck chase)
    private int tick = 0; // tick counter, for throttling scans
    private EntityItem cachedDrop = null; // last found drop, re-scanned every few ticks

    // Fixed serpentine mining order (built once): forward along Z, step 1 in X,
    // back along Z, and down 1 in Y when a layer is done. Deterministic and
    // reliable, instead of scanning for the nearest block each tick.
    private List<BlockPos> plan = null;
    private int planIndex = 0;
    private long planIndexSince = 0; // when planIndex last changed (stuck detection)
    private boolean finished = false; // whole plan cleared - idle until the mine resets
    private final Set<BlockPos> unbreakableBlacklist = new HashSet<BlockPos>();
    private final Map<BlockPos, Long> unbreakableBlacklistTime = new HashMap<BlockPos, Long>();
    private int tempTargetIndex = 0;
    private double lastUnstuckX = 0;
    private double lastUnstuckY = 0;
    private double lastUnstuckZ = 0;
    private long lastUnstuckTime = 0;
    private long stuckTime = 0;
    private long lastGhostCheck = 0;
    private long ghostCollisionTime = 0;

    // Layer completeness: before descending to the next layer, make sure every
    // block
    // in the current layer is actually broken (the serpentine can skip stragglers).
    private int currentLayerY = MAX_Y;
    private int cleanupLayer = Integer.MIN_VALUE; // layer we're currently cleaning up
    private long cleanupSince = 0; // when cleanup of that layer started
    private BlockPos currentLeftover = null;
    private long leftoverStart = 0;

    // Positions of blocks we've broken recently, so we only collect our own drops.
    private final List<long[]> broken = new ArrayList<long[]>(); // {x, y, z, timeMillis}

    // Pickaxe upkeep: keep one equipped; when it breaks and there's no spare, walk
    // to the shop sign and buy a new one.
    private static final BlockPos SHOP_SIGN = new BlockPos(24, 62, -689);
    private boolean buying = false;
    private long buyStart = 0;
    private long lastBuyClick = 0;
    private long lastSlotClickTime = 0;

    // Inventory jobs done the manual way: actually open the inventory GUI and
    // click,
    // so the server sees real inventory interactions (not slot pokes it might
    // block).
    // Dropping = click the item (pick up), then click outside to drop it.
    // Pickaxe = click the spare pickaxe (pick up), then click a hotbar slot.
    private boolean toggleKeyWasDown = false; // edge-detect the toggle key while a GUI is open

    // The mine resets every ~10 min and teleports us out. A teleport shows up as
    // the
    // position jumping far in a single tick - detect that, restart the pattern from
    // the top, and let the normal return loop walk us back into the mine.
    private static final double TELEPORT_DIST = 8.0;
    private double lastPosX, lastPosY, lastPosZ;
    private boolean lastPosSet = false;

    // The server warns "b will be resetting in 10 seconds." before it refills the
    // mine. We restart the pattern right after that reset lands, so we don't keep
    // grinding at spots that no longer exist and look like a dumb bot.
    private long restartPlanAt = 0;

    // A* path to a far target (shop/deposit/return). Straight-line walking can't
    // get
    // over a 2-high wall (auto-jump only clears 1), so we plan a route that only
    // steps up 1 at a time and follow it waypoint by waypoint.
    private static final int PATH_BUDGET = 20000; // A* node budget for shop/deposit/start routes
    private List<BlockPos> path = null;
    private int pathIndex = 0;
    private BlockPos pathGoal = null;
    private long pathProgressAt = 0;
    private long pathSearchAt = 0;
    private boolean pathWalking = false; // forward held while following the path (hysteresis gate)
    private boolean pathSprinting = false; // sprinting along the path (so sprint doesn't flicker)

    // Deposit: the bot never moves items itself (the server flags that). When full,
    // it walks to the Skraldespand, opens it and pings the player to shift-click
    // the
    // junk in by hand, then resumes.
    private static final BlockPos DEPOSIT_SIGN = new BlockPos(55, 61, -691);
    private boolean depositing = false;
    private long lastDepositClick = 0;
    private boolean notifiedDeposit = false;
    private boolean notifiedPickaxe = false;

    // Iron storage: when the bag is full of iron (no junk left to trash), walk to
    // the
    // old drop-off spot and ping the player to store it, then resume once there's
    // room.
    private static final BlockPos IRON_DROP = new BlockPos(20, 60, -684);
    private boolean storingIron = false;
    private boolean notifiedIron = false;

    // Only walk once we're roughly facing where we want to go. This is the single
    // most important anti-wander rule (MineBot/Baritone do the same): if we walk
    // while still turning, we drift off toward wherever we're half-facing and
    // circle forever instead of settling on the block.
    private static final float WALK_ANGLE = 30f;
    // Path-follow hysteresis: once moving along a planned path, keep walking
    // through the small 45-degree jogs between diagonal waypoints instead of
    // stuttering to a stop at each one. Only sharp turns stop to swing around.
    // In-mine block/drop approaches keep the strict WALK_ANGLE gate above.
    private static final float WALK_KEEP = 60f;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        // Let the Auto Mine keybind toggle it on/off even while a GUI is open (e.g.
        // while the deposit chest is open). The normal keybind handler doesn't fire
        // when a screen is open, so poll the key here.
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
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

        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory)
                && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            // A screen is open (the deposit/shop, or the user's own) - stand still
            // and let the player do their thing. We never touch the inventory.
            releaseKeys(mc);
            if (AutoEat.isEating()) {
                AutoEat.stop(mc);
            }

            // Auto-disposal system:
            if (depositing && mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
                net.minecraft.inventory.Container container = mc.thePlayer.openContainer;
                long now = System.currentTimeMillis();
                if (now - lastSlotClickTime > 250) {
                    boolean clickedAny = false;
                    for (net.minecraft.inventory.Slot slot : container.inventorySlots) {
                        if (slot != null && slot.inventory == mc.thePlayer.inventory && slot.getHasStack()) {
                            ItemStack stack = slot.getStack();
                            if (isJunk(stack)) {
                                // Shift-click this slot! (mode = 1)
                                mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, 1,
                                        mc.thePlayer);
                                lastSlotClickTime = now;
                                clickedAny = true;
                                break; // one click per check
                            }
                        }
                    }
                    if (!clickedAny) {
                        // No more junk - close the disposal GUI chest so the bot can continue!
                        mc.thePlayer.closeScreen();
                    }
                }
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

        checkMineRefill(mc);

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
        // Keep a pickaxe in hand. Select a hotbar one if available, or automatically swap a whitelisted pickaxe from main inventory.
        if (!holdingPickaxe(mc) && !selectHotbarPickaxe(mc)) {
            if (swapMainInventoryPickaxe(mc)) {
                stopMining(mc);
                stopWalk(mc);
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
            target = null;
            clearPath();
        }
        if (storingIron) {
            if (full && !hasJunk(mc)) {
                doStoreIron(mc);
                return;
            }
            storingIron = false;
            notifiedIron = false;
            target = null;
            clearPath();
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

    /**
     * Draw the bot's planned route (to the shop/deposit/start) as a line on the
     * floor.
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // Existing: the travel path while mining.
        if (CelleScannerMod.config.autoMineEnabled && path != null) {
            Pathfinder.renderPath(path, mc.thePlayer, event.partialTicks, 0.35f, 0.9f, 1.0f);
        }

        // The mine-area box: shown while picking it, or while the bot is enabled
        // (whether using a custom area in its dimension or the default box) - so it
        // disappears when Auto Mine is disabled.
        CelleConfig cfg = CelleScannerMod.config;
        boolean customHere = cfg.mineAreaSet && mc.thePlayer.dimension == cfg.mineAreaDim;
        boolean showBox = setAreaMode || (cfg.autoMineEnabled && (customHere || !cfg.mineAreaSet));
        if (!showBox) {
            return;
        }

        double px = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * event.partialTicks;
        double py = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * event.partialTicks;
        double pz = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * event.partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-px, -py, -pz);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableTexture2D();
        GL11.glLineWidth(2.5f);

        int[] b = boxBounds();
        drawBox(b[0], b[2], b[4], b[1], b[3], b[5], 1.0f, 0.82f, 0.29f); // gold area box

        // The GOAL block: the exact block the bot wants to mine next. Cyan while
        // traveling to it (DESTINATION), green once stationed and mining. Local
        // copy - the tick thread swaps the field while we render.
        BlockPos goalBlock = target;
        if (cfg.autoMineEnabled && goalBlock != null) {
            GL11.glLineWidth(3.5f);
            if (phase == Phase.MINING) {
                drawBox(goalBlock.getX(), goalBlock.getY(), goalBlock.getZ(),
                        goalBlock.getX(), goalBlock.getY(), goalBlock.getZ(), 0.30f, 1.0f, 0.35f);
            } else {
                drawBox(goalBlock.getX(), goalBlock.getY(), goalBlock.getZ(),
                        goalBlock.getX(), goalBlock.getY(), goalBlock.getZ(), 0.30f, 0.90f, 1.0f);
            }
            GL11.glLineWidth(2.5f);
        }

        if (setAreaMode && pendingCorner1 != null) {
            BlockPos c = pendingCorner1;
            drawBox(c.getX(), c.getY(), c.getZ(), c.getX(), c.getY(), c.getZ(), 0.30f, 0.90f, 1.0f); // corner 1
            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos m = mc.objectMouseOver.getBlockPos();
                drawBox(Math.min(c.getX(), m.getX()), Math.min(c.getY(), m.getY()), Math.min(c.getZ(), m.getZ()),
                        Math.max(c.getX(), m.getX()), Math.max(c.getY(), m.getY()), Math.max(c.getZ(), m.getZ()),
                        0.30f, 0.90f, 1.0f); // live preview corner1 -> aimed block
            }
        }

        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    /**
     * Wireframe box spanning the given inclusive block range (drawn to block+1
     * edges).
     */
    private static void drawBox(int minBX, int minBY, int minBZ, int maxBX, int maxBY, int maxBZ,
            float r, float g, float b) {
        double x0 = minBX, y0 = minBY, z0 = minBZ;
        double x1 = maxBX + 1.0, y1 = maxBY + 1.0, z1 = maxBZ + 1.0;
        GlStateManager.color(r, g, b, 0.9f);

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x0, y0, z1);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x0, y1, z1);
        GL11.glEnd();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x0, y0, z1);
        GL11.glVertex3d(x0, y1, z1);
        GL11.glEnd();
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!CelleScannerMod.config.autoMineEnabled) {
            return;
        }
        // If the window loses focus (becomes inactive) and Minecraft tries to open
        // the standard pause/ingame menu, cancel the event to allow background mining.
        if (event.gui instanceof GuiIngameMenu) {
            if (!Display.isActive()) {
                event.setCanceled(true);
            }
        }
    }

    /**
     * The mine resets every ~10 min; the server warns in chat first. Arm a restart.
     */
    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!CelleScannerMod.config.autoMineEnabled || event.message == null) {
            return;
        }
        String msg = event.message.getUnformattedText();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("resetting") || lower.contains("genopretter")
                    || lower.contains("nulstilles") || lower.contains("genoprettet")
                    || lower.contains("nulstillet") || lower.contains("mine reset")
                    || lower.contains("minen er")) {

                // Instant reset messages ("er genoprettet", "er nulstillet")
                if (lower.contains("er genoprettet") || lower.contains("er nulstillet")
                        || lower.contains("has reset") || lower.contains("reset complete")) {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc != null && mc.thePlayer != null) {
                        restartPlan(mc);
                    }
                    return;
                }

                // Parse countdown seconds if present (e.g. "in 3 seconds", "om 10 sekunder")
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(in|om)\\s+(\\d+)\\s+(seconds?|sekunder?)");
                java.util.regex.Matcher matcher = pattern.matcher(lower);
                if (matcher.find()) {
                    try {
                        int seconds = Integer.parseInt(matcher.group(2));
                        restartPlanAt = System.currentTimeMillis() + (seconds * 1000L) + 1200L;
                    } catch (NumberFormatException e) {
                        restartPlanAt = System.currentTimeMillis() + 3500L;
                    }
                } else {
                    restartPlanAt = System.currentTimeMillis() + 3500L;
                }
            }
        }
    }

    /**
     * In "set area" mode, right-clicks on blocks capture the two mine-area corners.
     */
    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (!setAreaMode) {
            return;
        }
        // button 1 = right-click, buttonstate true = pressed
        if (event.button != 1 || !event.buttonstate) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || !mc.inGameHasFocus) {
            return;
        }
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        BlockPos pos = mc.objectMouseOver.getBlockPos();
        event.setCanceled(true); // don't also use the held item / place a block

        if (pendingCorner1 == null) {
            pendingCorner1 = pos;
            mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GOLD
                    + "[Auto Mine] Hjørne 1: " + posStr(pos) + ". Højreklik det 2. hjørne."));
            return;
        }

        // Second corner - save the area and rebuild the plan against it.
        CelleConfig cfg = CelleScannerMod.config;
        cfg.mineAreaX1 = pendingCorner1.getX();
        cfg.mineAreaY1 = pendingCorner1.getY();
        cfg.mineAreaZ1 = pendingCorner1.getZ();
        cfg.mineAreaX2 = pos.getX();
        cfg.mineAreaY2 = pos.getY();
        cfg.mineAreaZ2 = pos.getZ();
        cfg.mineAreaDim = mc.thePlayer.dimension;
        cfg.mineAreaSet = true;
        cfg.save();

        setAreaMode = false;
        pendingCorner1 = null;
        plan = null; // force a rebuild against the new box next mine tick

        int[] b = boxBounds();
        int vol = (b[1] - b[0] + 1) * (b[3] - b[2] + 1) * (b[5] - b[4] + 1);
        mc.thePlayer.addChatMessage(new ChatComponentText(EnumChatFormatting.GREEN
                + "[Auto Mine] Mine-område sat (" + vol + " blokke). Start botten for at mine her."));
    }

    private static String posStr(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    /**
     * Restart the mining pattern from the top and drop any stale travel/target
     * state.
     */
    private void restartPlan(Minecraft mc) {
        stopMining(mc);
        planIndex = 0;
        finished = false;
        planIndexSince = System.currentTimeMillis();
        currentLayerY = MAX_Y;
        target = null;
        cachedDrop = null;
        phase = Phase.DESTINATION;
        ghostCollisionTime = 0;
        currentLeftover = null;
        leftoverStart = 0;
        cleanupLayer = Integer.MIN_VALUE;
        unbreakableBlacklist.clear();
        unbreakableBlacklistTime.clear();
        clearPath();
    }

    private void checkMineRefill(Minecraft mc) {
        if (mc.theWorld == null || plan == null || plan.isEmpty()) {
            return;
        }
        if (tick % 20 == 0 && currentLayerY < MAX_Y - 1) {
            int topSolidCount = 0;
            int topCheckCount = 0;
            for (int x = MIN_X; x <= MAX_X; x += 2) {
                for (int z = MIN_Z; z <= MAX_Z; z += 2) {
                    topCheckCount++;
                    BlockPos p = new BlockPos(x, MAX_Y, z);
                    if (!mc.theWorld.isAirBlock(p)) {
                        topSolidCount++;
                    }
                }
            }
            if (topCheckCount > 0 && topSolidCount >= (topCheckCount + 1) / 2) {
                Pathfinder.log("[AutoMine-Reset] Mine refill detected at MAX_Y=" + MAX_Y + "! Restarting plan.");
                restartPlan(mc);
            }
        }
    }

    /**
     * Ghost Block Detection & Resync:
     * 1. Detects client-air/server-solid desync (player colliding horizontally with an invisible block).
     *    When colliding with air in front, sends a block damage hit to force server to resync/break it.
     * 2. Detects mining desync (stuck mining a target for > 3.5s without it turning to air).
     *    Blacklists the ghost block and immediately switches to the next target block.
     */
    private void checkGhostBlocks(Minecraft mc, BlockPos feet) {
        long now = System.currentTimeMillis();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        // 1. Collision Ghost Block Detection (Client Air, Server Solid)
        if (mc.thePlayer.isCollidedHorizontally && !mc.thePlayer.isOnLadder()) {
            EnumFacing facing = mc.thePlayer.getHorizontalFacing();
            BlockPos frontFeet = feet.offset(facing);
            BlockPos frontHead = feet.up().offset(facing);

            boolean airFrontFeet = mc.theWorld.isAirBlock(frontFeet);
            boolean airFrontHead = mc.theWorld.isAirBlock(frontHead);

            if (airFrontFeet || airFrontHead) {
                if (ghostCollisionTime == 0) {
                    ghostCollisionTime = now;
                }
                BlockPos ghostPos = airFrontFeet ? frontFeet : frontHead;
                if (now - lastGhostCheck > 300) {
                    lastGhostCheck = now;
                    Pathfinder.log("[AutoMine-Ghost] Horizontal collision with air block at " + ghostPos + ", sending resync hit...");
                    mc.playerController.onPlayerDamageBlock(ghostPos, facing.getOpposite());
                    mc.thePlayer.swingItem();
                }

                if (now - ghostCollisionTime > 1200) {
                    unbreakableBlacklist.add(ghostPos);
                    unbreakableBlacklistTime.put(ghostPos, now);
                    Pathfinder.log("[AutoMine-Ghost] Blacklisted persistent ghost collision block at " + ghostPos);
                    clearPath();
                    target = null;
                    ghostCollisionTime = 0;
                }
            } else {
                ghostCollisionTime = 0;
            }
        } else {
            ghostCollisionTime = 0;
        }

        // 2. Mining Ghost Block Detection (Target mining stall > 2.8 seconds)
        if (phase == Phase.MINING && target != null && !mc.theWorld.isAirBlock(target)) {
            long startTime = (target.equals(currentLeftover) && leftoverStart > 0) ? leftoverStart : planIndexSince;
            if (startTime > 0 && now - startTime > 2800) {
                Pathfinder.log("[AutoMine-Ghost] Mining target stalled >2.8s at " + target + ", blacklisting ghost block.");
                unbreakableBlacklist.add(target);
                unbreakableBlacklistTime.put(target, now);
                if (target.equals(currentLeftover)) {
                    currentLeftover = null;
                    leftoverStart = 0;
                    cleanupSince = now;
                }
                target = null;
                planIndexSince = now;
            }
        }
    }

    private void doMine(Minecraft mc) {
        refreshBox(); // keep the live box (and START) in sync with the configured area
        recordBroken(mc); // note blocks we just finished breaking (for our-drop matching)
        claimDrops(mc); // claim iron that came out of our breaks

        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        checkGhostBlocks(mc, feet);

        if (target == null || mc.theWorld.isAirBlock(target)) {
            target = planTarget(mc);
        }

        // Player Obstacle Avoidance: if another player is standing on target, skip it to avoid body conflict
        if (target != null && isPlayerOccupying(mc, target)) {
            Pathfinder.log("[AutoMine-Detour] Player standing on target " + target + ", skipping block for now.");
            advanceIndex();
            target = planTarget(mc);
        }

        // Holes are handled by AVOIDANCE, not layer-skipping: a layer with a pit
        // in it still gets fully mined top-to-bottom - the bot just never WALKS
        // over the pit (straightWalkSafe below forces the pathfinder, which
        // routes around drops, whenever the beeline would cross a hole).

        // Layer safety (4A): if another player has mined a pit under us and we've
        // dropped well below our working layer, don't fight to climb back - adopt the
        // layer we landed on and mine there (join their layer).
        if (target != null && nearBox(mc, 1)) {
            int feetY = MathHelper.floor_double(mc.thePlayer.posY);
            if (feetY >= MIN_Y && feetY <= MAX_Y && feetY < currentLayerY - 1) {
                skipToLayer(feetY);
                currentLayerY = feetY;
                target = planTarget(mc);
            }
        }

        // Stuck Detection and Recovery
        double px = mc.thePlayer.posX;
        double py = mc.thePlayer.posY;
        double pz = mc.thePlayer.posZ;
        boolean walking = (path != null) || (mc.gameSettings.keyBindForward.isKeyDown());

        if (walking) {
            double distSq = (px - lastUnstuckX) * (px - lastUnstuckX)
                    + (py - lastUnstuckY) * (py - lastUnstuckY)
                    + (pz - lastUnstuckZ) * (pz - lastUnstuckZ);
            if (distSq > 0.05 * 0.05) { // we are moving
                lastUnstuckX = px;
                lastUnstuckY = py;
                lastUnstuckZ = pz;
                stuckTime = System.currentTimeMillis();
            } else { // we are not moving significantly
                if (System.currentTimeMillis() - stuckTime > 3000) { // stuck for 3 seconds!
                    // Trigger unstuck action!
                    if (System.currentTimeMillis() - lastUnstuckTime > 5000) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                EnumChatFormatting.RED + "[Auto Mine] Sidder fast! Prøver at hoppe/rekalkulere..."));
                        // 1. Try to jump
                        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                        // 2. Clear path and target to force pathfinder recalculation
                        clearPath();
                        target = null;
                        lastUnstuckTime = System.currentTimeMillis();
                    }
                }
            }
        } else {
            stuckTime = System.currentTimeMillis();
            lastUnstuckX = px;
            lastUnstuckY = py;
            lastUnstuckZ = pz;
        }

        // Release jump after 200-400ms
        if (lastUnstuckTime > 0 && System.currentTimeMillis() - lastUnstuckTime > 200
                && System.currentTimeMillis() - lastUnstuckTime < 400) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        }

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
        // and clear them first. Gives up on a layer after timeout so we can't get wedged.
        int ty = target.getY();
        if (ty < currentLayerY) {
            BlockPos leftover = leftoverInLayer(mc, currentLayerY);
            if (leftover != null && !cleanupTimedOut(currentLayerY)) {
                if (!leftover.equals(currentLeftover)) {
                    long now = System.currentTimeMillis();
                    currentLeftover = leftover;
                    leftoverStart = now;
                    cleanupSince = now; // reset layer cleanup timeout per block
                }
                target = leftover; // mine the closest unmined straggler on current layer
            } else {
                Pathfinder.log("[AutoMine-Layer] Layer " + currentLayerY + " clean! Descending to layer " + ty);
                currentLayerY = ty; // layer clean (or timed out) - descend
                currentLeftover = null;
                leftoverStart = 0;
            }
        } else if (ty > currentLayerY) {
            currentLayerY = ty; // went up (e.g. after a reset)
            currentLeftover = null;
            leftoverStart = 0;
        }

        // Outside the mine outline (e.g. after buying a pickaxe, depositing, or a
        // reset teleport) - pathfind back to the start corner (routes around walls
        // it can't just jump).
        if (!nearBox(mc, 1.0)) {
            phase = Phase.DESTINATION;
            stopMining(mc);
            navigate(mc, START, 1.0);
            return;
        }

        // Phase decision: ARRIVAL wins. Close enough (right layer + within range)
        // means station and mine, no matter what leftover travel state exists.
        // The old check kept DESTINATION alive just because a path object still
        // existed - but navigate() re-plans a 1-waypoint path forever when you
        // stand at the goal, so the bot arrived and never started mining until
        // the stuck-timer kicked it. The path is cleared on entering MINING.
        double distToTarget = closestDist(mc, target);
        boolean verticalOff = (target.getY() > feet.getY() + 1) || (target.getY() < feet.getY() - 1);
        double reach = CelleScannerMod.config.autoMineReach;
        if (!verticalOff && distToTarget <= reach) {
            phase = Phase.MINING;
        } else {
            phase = Phase.DESTINATION;
        }
        // Within DESTINATION, a live path (or a level gap, or a hole in the
        // straight line) means follow the planner; otherwise walk straight at
        // the target. The hole check is what keeps the bot OUT of player-dug
        // pits without skipping the layer: the A* routes around drops.
        boolean needPath = (path != null) || verticalOff
                || (phase == Phase.DESTINATION && !straightWalkSafe(mc, feet, target));

        if (phase == Phase.DESTINATION) {
            // --- DESTINATION PHASE (Travel & Navigation Only) ---
            stopMining(mc); // strictly no mining mid-travel

            // Sweep up OUR dropped items if nearby before traveling (setting-gated -
            // when off, we still auto-pick items we walk over, just don't detour).
            boolean collect = CelleScannerMod.config.autoMineCollectDrops == null
                    || CelleScannerMod.config.autoMineCollectDrops;
            EntityItem drop = (!collect || System.currentTimeMillis() < skipDropsUntil) ? null : currentDrop(mc);
            if (drop != null) {
                if (chaseSince == 0) chaseSince = System.currentTimeMillis();
                if (System.currentTimeMillis() - chaseSince > 5000) {
                    skipDropsUntil = System.currentTimeMillis() + 3000;
                    chaseSince = 0;
                } else {
                    aimAtEntity(mc, drop);
                    approach(mc, drop.posX, drop.posZ);
                    return;
                }
            } else {
                chaseSince = 0;
            }

            if (needPath) {
                navigate(mc, target, 2.0); // navigate aims along the path (aimAlong)
            } else {
                clearPath();
                // Track the block we're walking to (look AT it, pitch down) instead
                // of staring at the horizon and only looking down on arrival - the
                // crosshair is already settled when we get in reach, so mining
                // starts instantly. Same walk direction (both aim at target x,z),
                // so no fight with the movement. Applies in normal and crazy mode.
                aimTrack(mc, target);
                approach(mc, target.getX() + 0.5, target.getZ() + 0.5);
            }
            return;
        }

        // --- MINING PHASE (Arrived in Reach, Mining Active) ---
        clearPath();
        
        // Mine-afstand: walk closer to the target block if farther than autoMineApproachDist,
        // otherwise halt movement keys and remain stationed at the configured distance.
        double approachDist = CelleScannerMod.config.autoMineApproachDist;
        if (distToTarget > approachDist) {
            approach(mc, target.getX() + 0.5, target.getZ() + 0.5);
        } else {
            stopWalk(mc);
        }

        // Check for Y-axis / line-of-sight block obstructions between player eyes and target
        BlockPos effectiveTarget = target;
        Vec3 eyes = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 tVec = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        MovingObjectPosition losHit = mc.theWorld.rayTraceBlocks(eyes, tVec, false, true, false);
        if (losHit != null && losHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos blocker = losHit.getBlockPos();
            if (blocker != null && !blocker.equals(target) && inBox(blocker)
                    && blocker.getY() >= currentLayerY && !mc.theWorld.isAirBlock(blocker)) {
                effectiveTarget = blocker;
            }
        }

        MovingObjectPosition mop = mc.objectMouseOver;
        BlockPos looked = mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                ? mop.getBlockPos()
                : null;
        double distToLooked = looked != null ? closestDist(mc, looked) : Double.MAX_VALUE;

        // Smooth camera aim at effective target block (clearing any Y-axis obstruction first)
        aimAt(mc, effectiveTarget);

        // Raytrace bypass: if aimed towards effective target within reach but raytrace hit non-box block or missed
        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(tYaw - mc.thePlayer.rotationYaw));
        float pitchDiff = Math.abs(tPitch - mc.thePlayer.rotationPitch);
        boolean lookingAtTarget = yawDiff < 25f && pitchDiff < 25f;
        if (lookingAtTarget && distToTarget <= reach) {
            if (looked == null || !inBox(looked)) {
                looked = effectiveTarget;
                distToLooked = closestDist(mc, effectiveTarget);
            }
        }

        // Hard layer guard: NEVER break a block below the working layer.
        boolean belowLayer = looked != null && looked.getY() < currentLayerY;

        if (looked != null && inBox(looked) && !belowLayer
                && !mc.theWorld.isAirBlock(looked) && distToLooked <= reach) {
            if (tick % 10 == 0) {
                Pathfinder.log("[AutoMine-Mine] target=" + effectiveTarget + " looked=" + looked
                        + " layerY=" + currentLayerY + " feetY=" + feet.getY()
                        + " distT=" + String.format("%.2f", distToTarget)
                        + " distL=" + String.format("%.2f", distToLooked) + " reach=" + reach);
            }
            mineLookedAt(mc, looked, (mop != null && looked.equals(mop.getBlockPos())) ? mop.sideHit : EnumFacing.UP);
        } else {
            if (belowLayer && tick % 10 == 0) {
                Pathfinder.log("[AutoMine-Mine] REFUSED below-layer block looked=" + looked
                        + " layerY=" + currentLayerY + " (target=" + effectiveTarget + ")");
            }
            // Stationed at destination, waiting for crosshair to settle on target
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

    /**
     * As approach, but sprint + sprint-jump (bunny-hop) on a long straight stretch.
     */
    private void approach(Minecraft mc, double x, double z, boolean sprint) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        if (CelleScannerMod.config.autoMineCrazy) {
            mc.thePlayer.rotationYaw = wantYaw; // crazy mode: snap and go
        }
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(wantYaw - mc.thePlayer.rotationYaw));

        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));
        boolean onLadder = Pathfinder.isLadder(mc.theWorld, feet) || Pathfinder.isLadder(mc.theWorld, feet.up())
                || Pathfinder.isLadder(mc.theWorld, feet.down());

        if (onLadder || diff < WALK_ANGLE) {
            walkForward(mc);
            boolean sprinting = sprint && diff < 12f && !onLadder; // only sprint when well-lined-up and not on ladder
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprinting);
            if (sprinting) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                        mc.thePlayer.onGround || Pathfinder.shouldSwimUp(mc.thePlayer));
            }
        } else {
            stopWalk(mc);
        }
    }

    /**
     * As approach(), but for following a planned path: once moving, the facing gate
     * widens to WALK_KEEP so we walk straight through the small 45-degree jogs
     * between diagonal waypoints instead of stuttering to a stop at each one. The
     * strict WALK_ANGLE gate still applies from a standstill, and approach() keeps
     * it for in-mine block/drop moves where drifting matters more than flow.
     */
    private void approachPath(Minecraft mc, double x, double z, boolean sprint) {
        double dx = x - mc.thePlayer.posX;
        double dz = z - mc.thePlayer.posZ;
        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        if (CelleScannerMod.config.autoMineCrazy) {
            mc.thePlayer.rotationYaw = wantYaw; // crazy mode: snap and go
        }
        float diff = Math.abs(MathHelper.wrapAngleTo180_float(wantYaw - mc.thePlayer.rotationYaw));
        float gate = pathWalking ? WALK_KEEP : WALK_ANGLE;

        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));
        boolean onLadder = Pathfinder.isLadder(mc.theWorld, feet) || Pathfinder.isLadder(mc.theWorld, feet.up())
                || Pathfinder.isLadder(mc.theWorld, feet.down());

        if (onLadder || diff < gate) {
            pathWalking = true;
            walkForward(mc);
            boolean sprinting = sprint && diff < 12f && !onLadder; // only sprint when well-lined-up and not on ladder
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprinting);
            if (sprinting) {
                boolean ceilingClear = Pathfinder.passable(mc.theWorld, feet.up().up());
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                        ceilingClear || Pathfinder.shouldSwimUp(mc.thePlayer));
            }
            pathSprinting = sprinting;
        } else {
            stopWalk(mc);
        }
    }

    /**
     * Travel aim: yaw toward the waypoint, but pitch held near the horizon (about
     * head height over the waypoint) instead of staring down at the floor block
     * like aimAt() would. aimAt() stays for mining, where the crosshair has to be
     * on the block; this is just for walking, where the floor-stare looked janky.
     */
    private void aimAlong(Minecraft mc, BlockPos step) {
        double dx = (step.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (step.getY() + 1.5) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = (step.getZ() + 0.5) - mc.thePlayer.posZ;
        double dh = Math.sqrt(dx * dx + dz * dz);
        tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        tPitch = (float) (-Math.toDegrees(Math.atan2(dy, dh)));
    }

    /**
     * Plan a travel route, preferring an air/ladder path and only carving a mined
     * staircase when genuinely trapped (no air route exists). Mining is the
     * last resort, not an equal option - otherwise the bot would mine toward the
     * goal instead of detouring to the ladder, i.e. "never use the ladder".
     */
    private List<BlockPos> planTravelPath(Minecraft mc, World w, BlockPos feet, BlockPos goal, double reach) {
        int fall = Pathfinder.safeFall(mc.thePlayer);
        // 1) Air/ladder route only (no mining). Whenever a clear way up exists, this
        // is what makes the bot walk to the ladder and climb out instead of digging.
        List<BlockPos> air = Pathfinder.findPath(w, feet, goal, reach, PATH_BUDGET, fall, false);
        if (reachesGoal(air, goal, reach)) {
            return air;
        }
        // 2) The air route can't reach the goal (walled in at the bottom). Carve a
        // mined staircase out, but keep the air route if it still gets closer.
        List<BlockPos> mined = Pathfinder.findPath(w, feet, goal, reach, PATH_BUDGET, fall, true);
        if (mined == null || mined.isEmpty()) {
            return air;
        }
        if (air == null || air.isEmpty()) {
            return mined;
        }
        double airEnd = endDistSq(air, goal);
        double minedEnd = endDistSq(mined, goal);
        return airEnd <= minedEnd ? air : mined;
    }

    /** True if this path's last waypoint lands within reach of the goal. */
    private static boolean reachesGoal(List<BlockPos> path, BlockPos goal, double reach) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        double r = reach + 1.0;
        return endDistSq(path, goal) <= r * r;
    }

    private static double endDistSq(List<BlockPos> path, BlockPos goal) {
        BlockPos end = path.get(path.size() - 1);
        return end.distanceSq(goal.getX(), goal.getY(), goal.getZ());
    }

    /**
     * Route to within {@code reach} of {@code goal} using a planned path (so it
     * goes
     * around/over 2-high walls and up ladders), following it waypoint by waypoint.
     * While no path is found yet it stands still and keeps searching - it never
     * walks
     * blindly straight at the goal.
     */
    private void navigate(Minecraft mc, BlockPos goal, double reach) {
        if (BaritoneIntegration.isAvailable()) {
            boolean goalMoved = pathGoal == null || !pathGoal.equals(goal);
            if (goalMoved) {
                BaritoneIntegration.walkTo(goal);
                pathGoal = goal;
            }
            return;
        }
        World w = mc.theWorld;
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        long now = System.currentTimeMillis();
        if (pathGoal == null || !pathGoal.equals(goal)) {
            // New destination - search right away.
            path = planTravelPath(mc, w, feet, goal, reach);
            pathGoal = goal;
            pathIndex = 0;
            pathSearchAt = now;
            if (path != null) {
                pathProgressAt = now;
            }
        } else if ((path == null || now - pathProgressAt > 3000) && now - pathSearchAt >= 1000) {
            // No path yet, or stuck - re-search (throttled so the A* isn't run every tick).
            path = planTravelPath(mc, w, feet, goal, reach);
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

        // Drop waypoints we've reached (looking a few ahead, so overshooting a
        // corner recaptures the path instead of walking back to it).
        int reached = Pathfinder.consumeWaypoints(w, path, pathIndex,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (reached != pathIndex) {
            pathIndex = reached;
            pathProgressAt = System.currentTimeMillis();
        }
        if (pathIndex >= path.size()) {
            // Reached the end of a partial path but not the goal yet - re-plan from
            // here right away (new chunks loaded as we walked), like PathWalker does,
            // instead of standing frozen for the re-search throttle.
            path = planTravelPath(mc, w, feet, goal, reach);
            pathSearchAt = now;
            pathIndex = 0;
            if (path == null || path.isEmpty()) {
                stopWalk(mc);
                return; // no route from here - the throttled re-search above retries
            }
            pathProgressAt = now;
        }
        BlockPos step = Pathfinder.getLineOfSightTarget(w, feet, path, pathIndex);
        BlockPos ladderPos = Pathfinder.getClimbableLadder(w, feet, step);

        // If the path contains blocks we need to break through (not passable):
        if (!Pathfinder.passable(w, step) || !Pathfinder.passable(w, step.up())) {
            BlockPos toBreak = !Pathfinder.passable(w, step) ? step : step.up();
            stopWalk(mc);
            if (ladderPos != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            }
            aimAt(mc, toBreak);
            mineLookedAt(mc, toBreak, EnumFacing.UP);
            return;
        }

        // Climbing a ladder: the up/down DECISION is shared with PathWalker and
        // AutoFollow (Pathfinder.climbDecision, rule 11 in Pathfinder) so the three
        // bots can never drift apart again; only the key-pressing below stays local.
        Pathfinder.Climb climb = Pathfinder.climbDecision(w, feet, step, ladderPos, mc.thePlayer.posY);
        boolean up = climb == Pathfinder.Climb.UP;
        boolean down = climb == Pathfinder.Climb.DOWN;

        if (ladderPos != null) {
            Pathfinder.log("[AutoMine-Ladder] feet=" + feet + " step=" + step + " ladderPos=" + ladderPos + " posY="
                    + mc.thePlayer.posY + " up=" + up + " down=" + down);
        }

        if (up) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            Pathfinder.log("[AutoMine-Ladder] UP into=" + into);
            if (into != null) {
                // Snap to face the wall and push in EVERY tick. On a ladder, any tick
                // we're not pushing into the wall we slide back down, so we can't
                // afford an eased turn or a facing gate here.
                float y = Pathfinder.yawOf(into);
                mc.thePlayer.rotationYaw = y;
                tYaw = y;
                tPitch = 0f;
                climbForward(mc);
                // Bottom mount: the column's lowest rung can start a block above the
                // floor. 1.8.9's isOnLadder() reads the FEET block, so standing under
                // the rung pushing into the wall does nothing (the log showed 3s
                // pinned at posY=40 until the stuck-jump saved it). Hop so the feet
                // enter the rung cell and the climb engages.
                if (!Pathfinder.isLadder(w, feet) && mc.thePlayer.onGround) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), true);
                }
                return;
            }
        } else if (down) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            Pathfinder.log("[AutoMine-Ladder] DOWN into=" + into);
            if (into != null) {
                if (mc.thePlayer.onGround) {
                    // Still standing on solid ground (the rim beside the shaft, or a
                    // rung edge). Releasing all keys here just freezes on the ledge -
                    // the 21:20 log showed the bot parked at (34,60) for 8+ seconds
                    // this way. Walk toward the ladder cell until we drop into the
                    // column; only then does sliding make sense.
                    double cx = ladderPos.getX() + 0.5;
                    double cz = ladderPos.getZ() + 0.5;
                    float wy = (float) (Math.toDegrees(Math.atan2(cz - mc.thePlayer.posZ, cx - mc.thePlayer.posX))
                            - 90.0);
                    mc.thePlayer.rotationYaw = wy;
                    tYaw = wy;
                    tPitch = 0f;
                    climbForward(mc);
                } else {
                    // Airborne in the column - face the wall and release everything
                    // to slide down safely in 1.8.9.
                    float y = Pathfinder.yawOf(into);
                    mc.thePlayer.rotationYaw = y;
                    tYaw = y;
                    tPitch = 0f;
                    stopWalk(mc);
                }
                return;
            }
        }

        aimAlong(mc, step);
        // Sprint hysteresis: start on a 4-step straight, but once sprinting keep it
        // across short 2-step jogs so the FOV doesn't pump on and off constantly.
        approachPath(mc, step.getX() + 0.5, step.getZ() + 0.5,
                Pathfinder.straightRun(path, pathIndex, pathSprinting ? 2 : 4));
    }

    private void clearPath() {
        if (BaritoneIntegration.isAvailable()) {
            BaritoneIntegration.cancel();
        }
        path = null;
        pathGoal = null;
        pathIndex = 0;
    }

    private void mineLookedAt(Minecraft mc, BlockPos pos, EnumFacing side) {
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory
                || mc.currentScreen instanceof net.minecraft.client.gui.GuiChat) {
            return;
        }
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

    public static boolean inMineBox(BlockPos p) {
        if (INSTANCE == null) {
            return false;
        }
        return INSTANCE.inBox(p);
    }

    public static boolean isMinableBlock(World w, BlockPos p) {
        if (!inMineBox(p)) {
            return false;
        }
        net.minecraft.block.Block b = w.getBlockState(p).getBlock();
        return b != Blocks.air && b != Blocks.barrier && b != Blocks.bedrock;
    }

    private boolean isUsablePickaxe(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemPickaxe)) {
            return false;
        }
        Item item = stack.getItem();
        // Whitelist: Only use Iron Pickaxes (plain or enchanted), Diamond, and Gold. Exclude Wooden & Stone.
        if (item == Items.wooden_pickaxe || item == Items.stone_pickaxe) {
            return false;
        }
        if (item != Items.iron_pickaxe && item != Items.diamond_pickaxe && item != Items.golden_pickaxe) {
            return false;
        }
        if (!stack.isItemStackDamageable()) {
            return true;
        }
        int maxDur = stack.getMaxDamage();
        int curDamage = stack.getItemDamage();
        int left = maxDur - curDamage;
        return left > CelleScannerMod.config.autoMinePickaxeMin;
    }

    /**
     * Swaps a whitelisted usable pickaxe from main inventory (slots 9-35) into the current active hotbar slot.
     */
    private boolean swapMainInventoryPickaxe(Minecraft mc) {
        if (mc.thePlayer == null || mc.playerController == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastSlotClickTime < 250) {
            return true; // Click throttled, retry next tick
        }
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 9; i < 36; i++) {
            if (isUsablePickaxe(inv[i])) {
                int currentHotbar = mc.thePlayer.inventory.currentItem; // 0..8
                int windowId = mc.thePlayer.openContainer != null ? mc.thePlayer.openContainer.windowId : 0;

                // Hotbar Swap click (mode 2, button = currentHotbar index)
                mc.playerController.windowClick(windowId, i, currentHotbar, 2, mc.thePlayer);
                lastSlotClickTime = now;
                return true;
            }
        }
        return false;
    }

    private boolean holdingPickaxe(Minecraft mc) {
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        return isUsablePickaxe(held);
    }

    /**
     * Select a hotbar slot that holds a pickaxe (just a held-item change), or
     * false.
     */
    private boolean selectHotbarPickaxe(Minecraft mc) {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            if (isUsablePickaxe(inv[i])) {
                mc.thePlayer.inventory.currentItem = i;
                return true;
            }
        }
        return false;
    }

    private boolean hasPickaxe(Minecraft mc) {
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (isUsablePickaxe(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Poll the Auto Mine keybind while a GUI is open, so it can be switched off
     * there.
     */
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

    private void doBuy(Minecraft mc) {
        stopMining(mc);

        if (hasPickaxe(mc)) {
            buying = false;
            target = null;
            clearPath();
            stopWalk(mc);
            return;
        }
        if (System.currentTimeMillis() - buyStart > 60000) {
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

        ItemStack firstSlot = mc.thePlayer.inventory.mainInventory[0];
        if (firstSlot != null) {
            mc.thePlayer.inventory.currentItem = 0;
            mc.thePlayer.dropOneItem(true);
            return;
        }

        aimAt(mc, SHOP_SIGN);

        // Aimed at the sign and in reach - right-click it (throttled).
        MovingObjectPosition mop = mc.objectMouseOver;
        boolean lookingAtShop = false;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos hit = mop.getBlockPos();
            if (SHOP_SIGN.equals(hit) ||
                    (Math.abs(hit.getX() - SHOP_SIGN.getX()) <= 1 &&
                            Math.abs(hit.getY() - SHOP_SIGN.getY()) <= 1 &&
                            Math.abs(hit.getZ() - SHOP_SIGN.getZ()) <= 1)) {
                lookingAtShop = true;
            }
        }
        if (lookingAtShop && System.currentTimeMillis() - lastBuyClick > 800) {
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                    mc.thePlayer.getCurrentEquippedItem(), SHOP_SIGN, mop.sideHit, mop.hitVec);
            mc.thePlayer.swingItem();
            lastBuyClick = System.currentTimeMillis();
        }
    }

    /**
     * Walk to the Skraldespand, open the deposit chest (a normal block
     * interaction),
     * then stand by and ping the player to shift-click the junk in. We never move
     * items ourselves - that's what the server flags - so the player does that
     * part.
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
        boolean lookingAtSign = false;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos hit = mop.getBlockPos();
            if (DEPOSIT_SIGN.equals(hit) ||
                    (Math.abs(hit.getX() - DEPOSIT_SIGN.getX()) <= 1 &&
                            Math.abs(hit.getY() - DEPOSIT_SIGN.getY()) <= 1 &&
                            Math.abs(hit.getZ() - DEPOSIT_SIGN.getZ()) <= 1)) {
                lookingAtSign = true;
            }
        }
        if (lookingAtSign && System.currentTimeMillis() - lastDepositClick > 1000) {
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

    /**
     * Bag is full of iron: walk to the drop-off spot and ping the player to store
     * it.
     */
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

    /**
     * Cobblestone, sandstone and lapis are junk; everything else (picks, iron ore)
     * is kept.
     */
    private boolean isJunk(ItemStack s) {
        return CelleScannerMod.config.isTrash(s);
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
    /**
     * The active mine box as {minX,maxX,minY,maxY,minZ,maxZ} - custom area or
     * default.
     */
    private static int[] boxBounds() {
        CelleConfig c = CelleScannerMod.config;
        if (c.mineAreaSet) {
            return new int[] {
                    Math.min(c.mineAreaX1, c.mineAreaX2), Math.max(c.mineAreaX1, c.mineAreaX2),
                    Math.min(c.mineAreaY1, c.mineAreaY2), Math.max(c.mineAreaY1, c.mineAreaY2),
                    Math.min(c.mineAreaZ1, c.mineAreaZ2), Math.max(c.mineAreaZ1, c.mineAreaZ2)
            };
        }
        return DEFAULT_BOX.clone();
    }

    /**
     * Load the live box (MIN_X..MAX_Z + START) from the custom area or the default.
     */
    private void refreshBox() {
        int[] b = boxBounds();
        MIN_X = b[0];
        MAX_X = b[1];
        MIN_Y = b[2];
        MAX_Y = b[3];
        MIN_Z = b[4];
        MAX_Z = b[5];
        START = new BlockPos(MIN_X, MAX_Y, MIN_Z);
    }

    /**
     * Called from the GUI: arm "set area" mode so the next two right-clicks pick
     * corners.
     */
    public static void beginSetArea() {
        setAreaMode = true;
        pendingCorner1 = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.GOLD + "[Auto Mine] Højreklik det 1. hjørne af mine-området."));
        }
    }

    private void buildPlan() {
        refreshBox();
        currentLayerY = MAX_Y;
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

    /**
     * The current block to mine in plan order, skipping ones already cleared or
     * blacklisted.
     */
    private BlockPos planTarget(Minecraft mc) {
        if (plan == null) {
            buildPlan();
            planIndexSince = System.currentTimeMillis();
        }
        if (finished) {
            return null;
        }

        // If we've been stuck on one block too long (can't reach it), blacklist it and
        // skip past it.
        if (System.currentTimeMillis() - planIndexSince > SKIP_STUCK) {
            if (planIndex < plan.size()) {
                BlockPos p = plan.get(planIndex);
                unbreakableBlacklist.add(p);
                unbreakableBlacklistTime.put(p, System.currentTimeMillis());
            }
            advanceIndex();
        }

        // Clean up temporary blacklist of unbreakable blocks (clear older than 2
        // minutes)
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<BlockPos, Long>> it = unbreakableBlacklistTime.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (now - entry.getValue() > 120000) {
                unbreakableBlacklist.remove(entry.getKey());
                it.remove();
            }
        }

        while (planIndex < plan.size()) {
            BlockPos p = plan.get(planIndex);
            if (!mc.theWorld.isAirBlock(p) && !unbreakableBlacklist.contains(p)) {
                currentLayerY = p.getY();
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

    /**
     * Advance the plan (top-down) down to layer y, skipping the layers above it.
     */
    private void skipToLayer(int y) {
        while (planIndex < plan.size() && plan.get(planIndex).getY() > y) {
            advanceIndex();
        }
    }

    /**
     * Finds the closest non-air, non-blacklisted block on layer y (a straggler to clean up), or null.
     */
    private BlockPos leftoverInLayer(Minecraft mc, int y) {
        if (y < MIN_Y || y > MAX_Y || mc.theWorld == null || mc.thePlayer == null) {
            return null;
        }
        BlockPos closest = null;
        double minDist = Double.MAX_VALUE;

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                BlockPos p = new BlockPos(x, y, z);
                if (!mc.theWorld.isAirBlock(p) && !unbreakableBlacklist.contains(p)) {
                    double d = closestDist(mc, p);
                    if (d < minDist) {
                        minDist = d;
                        closest = p;
                    }
                }
            }
        }
        return closest;
    }

    private boolean isPassableHoleBlock(World w, BlockPos p) {
        net.minecraft.block.state.IBlockState st = w.getBlockState(p);
        net.minecraft.block.Block block = st.getBlock();
        if (block == Blocks.air) {
            return true;
        }
        net.minecraft.util.AxisAlignedBB box = block.getCollisionBoundingBox(w, p, st);
        return box == null;
    }

    /** True if another player is standing directly on or occupying block position p. */
    private boolean isPlayerOccupying(Minecraft mc, BlockPos p) {
        if (mc.theWorld == null || p == null) {
            return false;
        }
        AxisAlignedBB box = new AxisAlignedBB(p.getX(), p.getY(), p.getZ(),
                p.getX() + 1.0, p.getY() + 2.0, p.getZ() + 1.0);
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != null && player != mc.thePlayer && !player.isDead) {
                if (player.getEntityBoundingBox() != null && player.getEntityBoundingBox().intersectsWith(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if another player is standing along the straight walking line to target. */
    private boolean isPathBlockedByPlayer(Minecraft mc, BlockPos feet, BlockPos target) {
        if (mc.theWorld == null || mc.thePlayer == null || target == null) {
            return false;
        }
        double dx = (target.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (target.getZ() + 0.5) - mc.thePlayer.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.8) {
            return false;
        }

        int steps = (int) Math.ceil(len * 2);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double cx = mc.thePlayer.posX + dx * t;
            double cz = mc.thePlayer.posZ + dz * t;
            AxisAlignedBB stepBox = new AxisAlignedBB(cx - 0.35, feet.getY(), cz - 0.35,
                    cx + 0.35, feet.getY() + 1.8, cz + 0.35);

            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player != null && player != mc.thePlayer && !player.isDead) {
                    if (player.getEntityBoundingBox() != null && player.getEntityBoundingBox().intersectsWith(stepBox)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * True when the straight beeline from the player to the target crosses no
     * hole (a 3+ deep drop under the walking level) and no standing player.
     * When false, the caller routes with the pathfinder instead to detour around.
     */
    private boolean straightWalkSafe(Minecraft mc, BlockPos feet, BlockPos target) {
        if (isPathBlockedByPlayer(mc, feet, target)) {
            Pathfinder.log("[AutoMine-Detour] Player in walking path to " + target + ", routing detour with pathfinder...");
            return false; // player standing in path - route around them
        }
        World w = mc.theWorld;
        double dx = (target.getX() + 0.5) - mc.thePlayer.posX;
        double dz = (target.getZ() + 0.5) - mc.thePlayer.posZ;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) {
            return true;
        }
        int steps = (int) Math.ceil(len * 2); // sample every half block
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            int cx = MathHelper.floor_double(mc.thePlayer.posX + dx * t);
            int cz = MathHelper.floor_double(mc.thePlayer.posZ + dz * t);
            BlockPos p1 = new BlockPos(cx, feet.getY() - 1, cz);
            BlockPos p2 = new BlockPos(cx, feet.getY() - 2, cz);
            BlockPos p3 = new BlockPos(cx, feet.getY() - 3, cz);
            if (isPassableHoleBlock(w, p1) && isPassableHoleBlock(w, p2) && isPassableHoleBlock(w, p3)) {
                return false; // 3+ deep drop on the line - go around it
            }
        }
        return true;
    }

    /**
     * True once we've been cleaning up layer y too long (probably an unreachable
     * straggler).
     */
    private boolean cleanupTimedOut(int y) {
        long now = System.currentTimeMillis();
        if (cleanupLayer != y) {
            cleanupLayer = y;
            cleanupSince = now;
        }
        return now - cleanupSince > 10000;
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

    /**
     * Record blocks we finished breaking, and drop entries older than BREAK_TTL.
     */
    private void recordBroken(Minecraft mc) {
        if (mining != null && mc.theWorld.isAirBlock(mining)) {
            broken.add(new long[] { mining.getX(), mining.getY(), mining.getZ(), System.currentTimeMillis() });
            mining = null;
        }
        long now = System.currentTimeMillis();
        for (Iterator<long[]> it = broken.iterator(); it.hasNext();) {
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
     * A drop to walk to, re-scanning only every 5 ticks (4x/sec). We only ever
     * detour
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
     * Track aim for the approach: look AT the target block (yaw + downward pitch)
     * while walking to it, so the crosshair is already on it by arrival instead
     * of the old walk-flat-then-snap-down. Guards the spin: when the block is
     * nearly straight below (tiny horizontal distance) the yaw is meaningless, so
     * we hold the current yaw and only pitch down. Pitch stays valid at any dh.
     */
    private void aimTrack(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (pos.getY() + 0.5) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = (pos.getZ() + 0.5) - mc.thePlayer.posZ;
        double dh = Math.sqrt(dx * dx + dz * dz);
        if (dh > 0.6) {
            tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        }
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
        // Crazy mode: 100% lock-on - snap straight to the target angles, no
        // humanized easing at all.
        if (CelleScannerMod.config.autoMineCrazy) {
            mc.thePlayer.rotationYaw = tYaw;
            mc.thePlayer.rotationPitch = clamp(tPitch, -90f, 90f);
            return;
        }
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

    private double closestDist(Minecraft mc, BlockPos pos) {
        double eyeX = mc.thePlayer.posX;
        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double eyeZ = mc.thePlayer.posZ;

        double minX = pos.getX();
        double maxX = pos.getX() + 1.0;
        double minY = pos.getY();
        double maxY = pos.getY() + 1.0;
        double minZ = pos.getZ();
        double maxZ = pos.getZ() + 1.0;

        double closestX = Math.max(minX, Math.min(eyeX, maxX));
        double closestY = Math.max(minY, Math.min(eyeY, maxY));
        double closestZ = Math.max(minZ, Math.min(eyeZ, maxZ));

        double dx = closestX - eyeX;
        double dy = closestY - eyeY;
        double dz = closestZ - eyeZ;

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
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));
        boolean onLadder = Pathfinder.isLadder(mc.theWorld, feet) || Pathfinder.isLadder(mc.theWorld, feet.up())
                || Pathfinder.isLadder(mc.theWorld, feet.down());
        boolean jump = !onLadder && mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround;
        // Rule 13: swimming - hold space to float up/out of water (Baritone-style).
        jump = jump || Pathfinder.shouldSwimUp(mc.thePlayer);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), jump);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    private void climbForward(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
    }

    private void stopWalk(Minecraft mc) {
        if (BaritoneIntegration.isAvailable()) {
            BaritoneIntegration.cancel();
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        pathWalking = false;
        pathSprinting = false;
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
