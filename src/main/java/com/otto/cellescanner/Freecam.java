package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Freecam. The camera comes off the player and flies; the player does not move.
 *
 * <h3>The rule this whole class is built around</h3>
 *
 * The server must never learn that anything is happening. It is not enough for
 * the player to look like they are standing still: the client must not send a
 * single packet it would not have sent if the player were genuinely stood there
 * doing nothing. Flying through a wall is not what gets you banned. Telling the
 * server you flew through a wall is.
 *
 * So there are exactly two things this touches, and neither of them reaches the
 * network:
 *
 * <ol>
 *   <li>{@code Minecraft.setRenderViewEntity}, which decides where the camera
 *       is drawn from and is client side by definition.</li>
 *   <li>{@code EntityPlayerSP.movementInput}, swapped for one that reports
 *       nothing pressed, so the player stands still on its own rather than
 *       being held in place by us.</li>
 * </ol>
 *
 * The player entity itself is never moved, never rotated, and never touched.
 * It goes on running its normal update every tick and goes on sending the
 * normal position packet, reporting the same position it was already at, which
 * is exactly what standing still looks like from the other end.
 *
 * <h3>Why the camera is not in the world</h3>
 *
 * The camera is an entity, but it is deliberately never added to the world.
 * A client can add entities to its own world without telling anyone, so adding
 * it would not have leaked either, but not adding it means nothing can tick it,
 * collide with it, or find it in a lookup. It exists only to be a position and
 * a pair of angles for the renderer to read.
 *
 * <h3>The dangerous part, which is clicking</h3>
 *
 * This is the one that would actually get you banned, and it is not obvious.
 * The crosshair ray trace starts at the render view entity, so while the camera
 * is out flying, {@code objectMouseOver} is whatever the camera is pointing at,
 * hundreds of blocks away and through walls. A single left click would send a
 * dig packet for that block; a right click would send a place packet. Both are
 * flatly impossible for a player stood where the server thinks this one is.
 *
 * So mouse buttons are swallowed for as long as the camera is out, at the
 * earliest point they can be, before the click ever becomes a key press.
 */
public class Freecam {

    public static final Freecam INSTANCE = new Freecam();

    /** Anything below this and the camera crawls; above it, it is unflyable. */
    private static final double SPEED_UNIT = 0.35D;
    /** Held ctrl, for crossing distance. */
    private static final double SPRINT_MULTIPLIER = 2.5D;

    private boolean active;
    private CameraEntity camera;

    /**
     * The player's real input handler, kept so it can be put back.
     *
     * Swapping this out is what stops the player walking, and it is better than
     * unpressing keys every tick: an unpressed key is only unpressed until the
     * next keyboard event, and the window between the event and the player's
     * own update is enough for one step to get out.
     */
    private MovementInput realInput;

    public boolean isActive() {
        return active;
    }

    public void toggle() {
        if (active) {
            disable();
        } else {
            enable();
        }
    }

    public void enable() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || active) {
            return;
        }

        /* The one call in this class that can put a packet on the wire, so it
           is worth being exact about.

           It sends C07PacketPlayerDigging ABORT_DESTROY_BLOCK, and only if the
           player was already part way through breaking a block. That is byte
           for byte what vanilla sends when you let go of the mouse, about a
           block the player is stood next to and within reach of, before the
           camera has gone anywhere.

           Leaving it out is the unsafe option: the half-finished dig would stay
           open against a block the player is about to appear to walk away from. */
        if (mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }

        camera = new CameraEntity(mc.theWorld);
        camera.copyLocationAndAnglesFrom(mc.thePlayer);
        camera.noClip = true;
        startTick();
        // Deliberately NOT added to the world. See the class comment.

        realInput = mc.thePlayer.movementInput;
        // Reports nothing pressed, every tick, so the player reads no input at
        // all and stays exactly where it is.
        mc.thePlayer.movementInput = new Frozen();

        active = true;
        mc.setRenderViewEntity(camera);

        // The build stamp is here on purpose. Java loads a class once per run,
        // so a jar copied in while the game is open does nothing until a full
        // quit and relaunch, and "it still does the old thing" is impossible to
        // tell from "the fix did not work" without it.
        say(mc, "§a[Freecam] §eTIL§a §7(build " + MassiveOsFreakyAddons.VERSION + ")");
        say(mc, "§aWASD + mus, mellemrum og shift for op og ned, ctrl for fart.");
        say(mc, "§7Du står stille imens, og du kan ikke slå eller bygge. Din krop bliver hvor den er.");
    }

    public void disable() {
        Minecraft mc = Minecraft.getMinecraft();
        active = false;
        camera = null;

        if (mc.thePlayer != null) {
            // Put the real input handler back before anything else, so that even
            // if the view entity restore below were to fail the player is at
            // least controllable again.
            if (realInput != null) {
                mc.thePlayer.movementInput = realInput;
            }
            mc.setRenderViewEntity(mc.thePlayer);
            say(mc, "§c[Freecam] §7FRA.");
        }
        realInput = null;
    }

    private static void say(Minecraft mc, String message) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(message));
        }
    }

    /* ---------------------------------------------------------------------
       Looking around.

       Done on the render tick rather than the game tick so the camera turns at
       the frame rate rather than at 20 Hz.

       Reading the mouse here is also what keeps the player's head still.
       LWJGL hands out the movement since the last read and then forgets it, so
       taking it here means vanilla's own read, which happens later in the same
       frame and would have turned the player, gets nothing. The player's
       rotation is part of the position packet, so this is the difference
       between a player stood still and a player stood still looking wildly
       around at things they cannot see.
       --------------------------------------------------------------------- */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active || camera == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        // Something else took the view. Rather than fight it, stand down: a
        // freecam that is not the camera is just a player who cannot move.
        if (mc.getRenderViewEntity() != camera) {
            disable();
            return;
        }

        if (mc.currentScreen != null || !Mouse.isGrabbed()) {
            return;
        }

        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float factor = sensitivity * sensitivity * sensitivity * 8.0F * 0.15F;

        camera.prevRotationYaw = camera.rotationYaw;
        camera.prevRotationPitch = camera.rotationPitch;

        camera.rotationYaw += Mouse.getDX() * factor;
        camera.rotationPitch -= Mouse.getDY() * factor
                * (mc.gameSettings.invertMouse ? -1.0F : 1.0F);
        if (camera.rotationPitch < -90.0F) {
            camera.rotationPitch = -90.0F;
        }
        if (camera.rotationPitch > 90.0F) {
            camera.rotationPitch = 90.0F;
        }
    }

    /* ---------------------------------------------------------------------
       Flying.
       --------------------------------------------------------------------- */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        // Left the world, died into a respawn, disconnected. Stand down rather
        // than hold a camera pointed at a world that is no longer there.
        if (mc.thePlayer == null || mc.theWorld == null || camera == null) {
            disable();
            return;
        }

        // The player's input handler is ours for as long as this is on. If
        // something else replaced it, put ours back rather than let the player
        // start walking under a camera the user is steering.
        if (!(mc.thePlayer.movementInput instanceof Frozen)) {
            mc.thePlayer.movementInput = new Frozen();
        }

        if (mc.currentScreen != null) {
            return;
        }

        GameSettings gs = mc.gameSettings;

        // Read the physical keys, not the key bindings' pressed flags. The
        // bindings are what the player's own movement code reads, and leaving
        // them alone is what keeps this from having to fight with it.
        double forward = 0.0D;
        double strafe = 0.0D;
        double vertical = 0.0D;
        if (down(gs.keyBindForward.getKeyCode())) forward += 1.0D;
        if (down(gs.keyBindBack.getKeyCode())) forward -= 1.0D;
        if (down(gs.keyBindLeft.getKeyCode())) strafe += 1.0D;
        if (down(gs.keyBindRight.getKeyCode())) strafe -= 1.0D;
        if (down(gs.keyBindJump.getKeyCode())) vertical += 1.0D;
        if (down(gs.keyBindSneak.getKeyCode())) vertical -= 1.0D;

        if (forward == 0.0D && strafe == 0.0D && vertical == 0.0D) {
            // Still has to happen on a still frame, or the render origin keeps
            // interpolating away from a position the camera already left.
            startTick();
            return;
        }

        double speed = SPEED_UNIT;
        CelleConfig config = MassiveOsFreakyAddons.config;
        if (config != null && config.freecamSpeed > 0) {
            speed *= config.freecamSpeed;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            speed *= SPRINT_MULTIPLIER;
        }

        // Minecraft's own convention: yaw 0 faces +Z, and strafe is positive to
        // the left. Forward follows the pitch as well, so the camera goes where
        // it is pointed the way spectator mode does, while space and shift stay
        // straight up and down whatever it is looking at.
        double yaw = Math.toRadians(camera.rotationYaw);
        double pitch = Math.toRadians(camera.rotationPitch);
        double sinYaw = Math.sin(yaw);
        double cosYaw = Math.cos(yaw);
        double cosPitch = Math.cos(pitch);

        double dx = (strafe * cosYaw - forward * sinYaw * cosPitch) * speed;
        double dy = (vertical - forward * Math.sin(pitch)) * speed;
        double dz = (forward * cosYaw * cosPitch + strafe * sinYaw) * speed;

        startTick();
        camera.setPosition(camera.posX + dx, camera.posY + dy, camera.posZ + dz);
    }

    /**
     * What {@code Entity.onUpdate} would have done at the top of the tick.
     *
     * This is the whole reason the camera used to snap back, and it is worth
     * spelling out because the two look interchangeable and are not.
     *
     * The camera transform reads {@code prevPos}, but the world is drawn around
     * an origin the renderer builds from {@code lastTickPos} instead, each
     * interpolated toward {@code pos} by the partial tick. Vanilla keeps both
     * in step because every entity's update begins by copying its position into
     * them. This camera is never ticked by anything, so nothing was moving
     * lastTickPos: it stayed at the spot the camera was created, which is the
     * player's position at the moment freecam was switched on.
     *
     * The result was the render origin sliding from the player's old position
     * out to the camera and back again, twenty times a second, getting worse
     * the further away you flew. So: both fields, in lockstep, exactly as
     * vanilla does it.
     */
    private void startTick() {
        camera.lastTickPosX = camera.prevPosX = camera.posX;
        camera.lastTickPosY = camera.prevPosY = camera.posY;
        camera.lastTickPosZ = camera.prevPosZ = camera.posZ;
    }

    private static boolean down(int keyCode) {
        // Mouse buttons are stored as negative key codes. Those are swallowed
        // while this is on, and binding movement to one is not worth the
        // special case, so only real keys count.
        return keyCode > 0 && Keyboard.isKeyDown(keyCode);
    }

    /* ---------------------------------------------------------------------
       Not touching anything.
       --------------------------------------------------------------------- */

    /**
     * Swallows clicks while the camera is out.
     *
     * HIGHEST so it runs before anything else that might act on the click, and
     * before Forge turns it into a key press at all. Cancelling here means the
     * click never reaches the attack or use binding, so the two loops in
     * {@code runTick} that fire off digging and placing never see it.
     *
     * Buttons only. Scroll wheel movement arrives with a button of -1 and is
     * left alone, since changing the held slot is a thing a stood-still player
     * does all the time.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (active && event.button >= 0) {
            event.setCanceled(true);
        }
    }

    /**
     * The second lock on the same door.
     *
     * The click swallowing above should mean this never fires, and if the two
     * ever disagree that is worth knowing about rather than trusting. Anything
     * that reaches here while the camera is out is an interaction from a
     * position the server does not think the player is at.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (active) {
            event.setCanceled(true);
        }
    }

    /**
     * The camera. Deliberately the least capable entity that will do the job.
     *
     * It started out as an {@code EntityOtherPlayerMP}, which was a mistake:
     * that class carries the interpolation fields the client uses to smooth
     * other players between position updates, and their entire purpose is to
     * drag the entity toward a remembered position. A camera has no business
     * owning anything that can move it. This one has no update, no physics and
     * nothing that reads its position but the renderer, so the only thing in
     * the game that can move it is the code above.
     */
    private static final class CameraEntity extends Entity {
        CameraEntity(net.minecraft.world.World world) {
            super(world);
            this.noClip = true;
            // Roughly a standing player's eyes, so switching in and out does
            // not jump the view up or down.
            this.setSize(0.0F, 0.0F);
        }

        @Override
        public float getEyeHeight() {
            return 1.62F;
        }

        /**
         * Vanilla's {@code Entity.getLookVec()} is literally {@code return null}.
         * Only {@code EntityLivingBase} bothers to override it, and a bare
         * Entity is what this is.
         *
         * That null went straight through the render view entity into LabyMod,
         * whose camera reads a look vector off it every tick. The result was a
         * NullPointerException per tick from LabyMod's Flux addon, from the
         * moment freecam came on, and the log spam from that is what eventually
         * stalled the client until the server dropped the connection.
         *
         * The lesson is more general than the one line: anything handed the
         * render view entity assumes it is at least as capable as a player, so
         * the defaults a bare Entity inherits are not safe to leave.
         */
        @Override
        public Vec3 getLookVec() {
            return this.getLook(1.0F);
        }

        /** Nothing ticks this, and if anything ever did, it would do nothing. */
        @Override
        public void onUpdate() {
        }

        @Override
        protected void entityInit() {
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound tag) {
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound tag) {
        }
    }

    /**
     * A movement input that is never anything but zero.
     *
     * Its own type rather than a plain {@code MovementInput} so the tick check
     * above can tell ours from the real one, and so a stack trace naming it is
     * self-explanatory.
     */
    private static final class Frozen extends MovementInput {
        @Override
        public void updatePlayerMoveState() {
            this.moveStrafe = 0.0F;
            this.moveForward = 0.0F;
            this.jump = false;
            this.sneak = false;
        }
    }
}
