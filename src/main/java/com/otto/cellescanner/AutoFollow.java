package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.util.EnumFacing;

import java.util.List;

/**
 * Auto-Follow addon (combat/QOL helper): pathfinds and follows a teammate,
 * positioning the player roughly 1.2 blocks to their side to stand beside them.
 */
public class AutoFollow {

    private static boolean active = false;
    private static String targetName = null;

    private static List<BlockPos> path = null;
    private static int pathIndex = 0;
    private static BlockPos lastGoal = null;
    private static long lastSearch = 0;
    private static long progressAt = 0;
    private static boolean pathSprinting = false;

    // Movement angles matching AutoMine/PathWalker
    private static final float WALK_ANGLE = 30f;
    private static final float WALK_KEEP = 60f;

    public static void start(String name) {
        targetName = name;
        active = true;
        path = null;
        pathIndex = 0;
        lastGoal = null;
        lastSearch = 0;
        progressAt = System.currentTimeMillis();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§aAuto-Følg: Fælger nu " + name));
        }
    }

    public static void stop() {
        if (active) {
            active = false;
            targetName = null;
            path = null;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                releaseKeys(mc);
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new ChatComponentText("§cAuto-Følg: Stoppet."));
                }
            }
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static String getTargetName() {
        return targetName;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || targetName == null) {
            return;
        }
        // Don't fight the Auto Mine bot
        if (CelleScannerMod.config.autoMineEnabled) {
            stop();
            return;
        }
        // Don't fight PathWalker (walk to celle)
        if (PathWalker.isActive()) {
            stop();
            return;
        }

        // Stand still while any screen is open
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiInventory) && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            releaseKeys(mc);
            return;
        }

        EntityPlayer target = null;
        for (Object o : mc.theWorld.playerEntities) {
            if (o instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) o;
                if (p.getName().equalsIgnoreCase(targetName)) {
                    target = p;
                    break;
                }
            }
        }

        if (target == null) {
            // Player walked out of render distance - wait still
            releaseKeys(mc);
            path = null;
            return;
        }

        // Calculate follow position: 1.2 blocks to the side of the teammate
        double yawRad = Math.toRadians(target.rotationYaw);
        // Perpendicular vector to the right side
        double sideX = -Math.cos(yawRad);
        double sideZ = -Math.sin(yawRad);

        double destX = target.posX + sideX * 1.2;
        double destZ = target.posZ + sideZ * 1.2;
        double destY = target.posY;

        World w = mc.theWorld;
        BlockPos goal = new BlockPos(destX, destY, destZ);

        // If the right side is not standable, check the left side
        if (!Pathfinder.canStand(w, goal)) {
            sideX = Math.cos(yawRad);
            sideZ = Math.sin(yawRad);
            destX = target.posX + sideX * 1.2;
            destZ = target.posZ + sideZ * 1.2;
            goal = new BlockPos(destX, destY, destZ);
            
            // If neither side is standable, fallback to target's exact position
            if (!Pathfinder.canStand(w, goal)) {
                goal = new BlockPos(target.posX, target.posY, target.posZ);
            }
        }

        double dx = goal.getX() + 0.5 - mc.thePlayer.posX;
        double dz = goal.getZ() + 0.5 - mc.thePlayer.posZ;
        double dy = goal.getY() - mc.thePlayer.posY;
        double distSq = dx * dx + dz * dz;

        // If close enough horizontally and vertically, release keys and face the teammate
        if (distSq < 1.2 * 1.2 && Math.abs(dy) < 2.0) {
            releaseKeys(mc);
            path = null;
            // Smoothly look at target player
            double tdx = target.posX - mc.thePlayer.posX;
            double tdz = target.posZ - mc.thePlayer.posZ;
            mc.thePlayer.rotationYaw = (float) (Math.toDegrees(Math.atan2(tdz, tdx)) - 90.0);
            return;
        }

        // Search or follow A* path
        long now = System.currentTimeMillis();
        BlockPos feet = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY), MathHelper.floor_double(mc.thePlayer.posZ));

        boolean goalMoved = lastGoal == null || lastGoal.distanceSq(goal.getX(), goal.getY(), goal.getZ()) > 1.5 * 1.5;
        boolean stale = path == null || now - progressAt > 3000;

        if ((goalMoved || stale) && now - lastSearch >= 500) {
            path = Pathfinder.findPath(w, feet, goal, 1.2, 5000, Pathfinder.safeFall(mc.thePlayer));
            lastSearch = now;
            pathIndex = 0;
            lastGoal = goal;
            if (path != null) {
                progressAt = now;
            }
        }

        if (path == null || path.isEmpty()) {
            releaseKeys(mc);
            return;
        }

        int reached = Pathfinder.consumeWaypoints(path, pathIndex,
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (reached != pathIndex) {
            pathIndex = reached;
            progressAt = now;
        }

        if (pathIndex >= path.size()) {
            releaseKeys(mc);
            return;
        }

        BlockPos step = path.get(pathIndex);

        // Ladder mechanics (using our 1.8.9 safe movement)
        BlockPos ladderPos = Pathfinder.isLadder(w, feet) ? feet : (Pathfinder.isLadder(w, feet.up()) ? feet.up() : null);
        boolean up = false;
        if (ladderPos != null) {
            for (int i = pathIndex; i < path.size(); i++) {
                if (path.get(i).getY() > MathHelper.floor_double(mc.thePlayer.posY)) {
                    up = true;
                    break;
                }
            }
        }
        boolean down = false;
        if (ladderPos != null && !up) {
            if (step.getY() < feet.getY()) {
                down = true;
            }
        }

        if (up) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            if (into != null) {
                mc.thePlayer.rotationYaw = Pathfinder.yawOf(into);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
                return;
            }
        } else if (down) {
            EnumFacing into = Pathfinder.ladderInto(w, ladderPos);
            if (into != null) {
                mc.thePlayer.rotationYaw = Pathfinder.yawOf(into);
                releaseKeys(mc); // release keys to slide down safely in 1.8.9
                return;
            }
        }

        // Steer and Walk
        double sdx = step.getX() + 0.5 - mc.thePlayer.posX;
        double sdz = step.getZ() + 0.5 - mc.thePlayer.posZ;
        float want = (float) (Math.toDegrees(Math.atan2(sdz, sdx)) - 90.0);
        float diff = MathHelper.wrapAngleTo180_float(want - mc.thePlayer.rotationYaw);
        float adiff = Math.abs(diff);

        // Smooth rotation yaw easing
        float ease = adiff * 0.25f;
        if (ease < 2f) {
            ease = 2f;
        }
        if (adiff <= ease) {
            mc.thePlayer.rotationYaw = want;
        } else {
            mc.thePlayer.rotationYaw += Math.copySign(ease, diff);
        }

        boolean walk = adiff < (pathIndex > 0 ? WALK_KEEP : WALK_ANGLE);
        if (walk) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);
            boolean sprintNow = Pathfinder.straightRun(path, pathIndex, pathSprinting ? 2 : 4) && adiff < 12f;
            boolean autoJump = mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), sprintNow);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), sprintNow ? mc.thePlayer.onGround : autoJump);
            pathSprinting = sprintNow;
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        }
    }

    private static void releaseKeys(Minecraft mc) {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        pathSprinting = false;
    }
}
