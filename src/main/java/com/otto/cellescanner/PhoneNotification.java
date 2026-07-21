package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Phone Notification System:
 * - Listens for incoming whispers (/whisper or /msg) from friends.
 * - Detects when friends log online / enter chunk view.
 * - Displays a sleek cyber pop-up banner overlay at top-center with slide animation.
 * - Triggers sound notification hook.
 */
public class PhoneNotification {

    public enum NotifyType {
        MESSAGE, FRIEND_ONLINE
    }

    private static final long DISPLAY_MS = 3600L;
    private static final long ANIM_MS = 220L;

    private static ActiveNotify activeNotify = null;
    private final Set<String> knownOnlineFriends = new HashSet<String>();
    private int checkTick = 0;

    private static class ActiveNotify {
        final String title;
        final String body;
        final NotifyType type;
        final long startTime;

        ActiveNotify(String title, String body, NotifyType type) {
            this.title = title;
            this.body = body;
            this.type = type;
            this.startTime = System.currentTimeMillis();
        }
    }

    public static void notifyMessage(String sender, String message) {
        activeNotify = new ActiveNotify("💬 BESKED FRA " + sender.toUpperCase(), message, NotifyType.MESSAGE);
        playSound(Minecraft.getMinecraft());
    }

    public static void notifyOnline(String friendName) {
        activeNotify = new ActiveNotify("👥 CREW UPDATE", friendName + " er nu ONLINE!", NotifyType.FRIEND_ONLINE);
        playSound(Minecraft.getMinecraft());
    }

    public static void playSound(Minecraft mc) {
        try {
            if (mc.thePlayer != null) {
                // SFX Hook: Crisp pop/chime sound effect (user can replace SFX string)
                mc.thePlayer.playSound("gui.button.press", 1.0f, 1.4f);
            }
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event.message == null) return;
        String unformatted = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());

        CelleConfig cfg = CelleScannerMod.config;
        if (cfg.friendsList.isEmpty()) return;

        // Check for whisper formats: "Fra <Friend>: <msg>", "<Friend> whispers: <msg>", "[<Friend> -> Mig] <msg>"
        for (String friend : cfg.friendsList) {
            if (friend == null || friend.trim().isEmpty()) continue;
            String fName = friend.trim();

            if (unformatted.contains("Fra " + fName) || unformatted.contains(fName + " whispers")
                    || unformatted.contains("[" + fName + " ->")) {
                
                // Extract message body after colon or arrow
                String body = unformatted;
                int colonPos = unformatted.lastIndexOf(':');
                if (colonPos >= 0 && colonPos + 1 < unformatted.length()) {
                    body = unformatted.substring(colonPos + 1).trim();
                }

                // Add to GuiPhone chat history buffer
                GuiPhone.addReceivedMessage(fName, body);

                // Show notification pop-up
                notifyMessage(fName, body);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++checkTick % 20 != 0) return; // check once per second

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            knownOnlineFriends.clear();
            return;
        }

        CelleConfig cfg = CelleScannerMod.config;
        if (cfg.friendsList.isEmpty()) return;

        Set<String> currentOnline = new HashSet<String>();
        for (String friend : cfg.friendsList) {
            if (friend == null) continue;
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(friend);
            if (player != null) {
                currentOnline.add(friend);
                if (!knownOnlineFriends.contains(friend)) {
                    notifyOnline(friend); // New online notification
                }
            }
        }
        knownOnlineFriends.clear();
        knownOnlineFriends.addAll(currentOnline);
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || activeNotify == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - activeNotify.startTime;
        if (elapsed > DISPLAY_MS) {
            activeNotify = null;
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRendererObj;
        ScaledResolution res = event.resolution;

        // Slide Animation: Top slide-down / slide-up
        float animProgress = 1.0f;
        if (elapsed < ANIM_MS) {
            animProgress = (float) elapsed / ANIM_MS;
        } else if (elapsed > DISPLAY_MS - ANIM_MS) {
            animProgress = (float) (DISPLAY_MS - elapsed) / ANIM_MS;
        }
        animProgress = MathHelper.clamp_float(animProgress, 0.0f, 1.0f);
        float ease = 1.0f - (float) Math.pow(1.0f - animProgress, 3);

        int notifyW = 160;
        int notifyH = 32;
        int notifyX = (res.getScaledWidth() - notifyW) / 2;
        int targetY = 10;
        int curY = (int) (-notifyH + (targetY + notifyH) * ease);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 400.0f); // Render above HUD

        // Draw Cyber Notification Pop-Up Frame
        drawRect(notifyX - 2, curY - 2, notifyX + notifyW + 2, curY + notifyH + 2, 0xFF0A0D14);
        drawRect(notifyX - 1, curY - 1, notifyX + notifyW + 1, curY + notifyH + 1,
                activeNotify.type == NotifyType.MESSAGE ? 0xFFFF007F : 0xFF00E676); // Magenta for Msg, Emerald for Online
        drawRect(notifyX, curY, notifyX + notifyW, curY + notifyH, 0xFF121824);

        // Header Title
        font.drawStringWithShadow(activeNotify.title, notifyX + 8, curY + 4,
                activeNotify.type == NotifyType.MESSAGE ? 0xFFFF007F : 0xFF00E676);

        // Message Body
        String trimmedBody = activeNotify.body;
        if (trimmedBody.length() > 26) {
            trimmedBody = trimmedBody.substring(0, 24) + "..";
        }
        font.drawStringWithShadow(trimmedBody, notifyX + 8, curY + 17, 0xFFFFFFFF);

        GlStateManager.popMatrix();
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        net.minecraft.client.gui.Gui.drawRect(left, top, right, bottom, color);
    }
}
