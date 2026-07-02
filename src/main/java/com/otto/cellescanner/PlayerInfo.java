package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player Info addon: shift + right-click another player (or search by name) to
 * open a menu (GuiPlayerInfo) with their armor, their celle (from "/ce info
 * &lt;name&gt;"), and their bande. The armor and bande are read client-side; the
 * celle is fetched by quietly running /ce info once when the menu opens and
 * parsing the reply (which is hidden from chat).
 */
public class PlayerInfo {

    private static final Pattern HEADER = Pattern.compile("\\{\\s*([A-Za-z]{1,2}[0-9]{2,5})\\s*\\}");
    private static final Pattern TID_TOKEN = Pattern.compile("(\\d+)\\s*(dage?|timer?|minutter?|sekunder?)", Pattern.CASE_INSENSITIVE);
    private static final long WINDOW_MS = 4000L;

    /** Parsed "/ce info" block for the player the menu is currently showing. */
    public static final class Celle {
        public String id;
        public String gang;    // Lokation
        public String owner;   // Ejer
        public String block;   // Blok
        public String tid;     // raw Tid text
        public final List<String> members = new ArrayList<>();
    }

    private static volatile String lookupName = null;   // lowercased target we're waiting on
    private static volatile Celle celle = null;         // last parsed result
    private static volatile boolean loading = false;
    private static volatile long deadline = 0L;

    /** Kicks off a "/ce info <name>" lookup for the menu. */
    public static void lookup(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        lookupName = username.toLowerCase(Locale.ROOT);
        celle = null;
        loading = true;
        deadline = System.currentTimeMillis() + WINDOW_MS;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/ce info " + username);
        }
    }

    public static Celle getCelle() {
        return celle;
    }

    public static boolean isLoading() {
        if (loading && System.currentTimeMillis() > deadline && celle == null) {
            loading = false; // timed out (e.g. command on cooldown / no such celle)
        }
        return loading;
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (!CelleScannerMod.config.playerInfoEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (event.entityPlayer != mc.thePlayer || !event.entityPlayer.isSneaking()) {
            return;
        }
        if (!(event.target instanceof EntityPlayer) || event.target == mc.thePlayer) {
            return;
        }
        event.setCanceled(true);
        mc.displayGuiScreen(new GuiPlayerInfo((EntityPlayer) event.target));
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (!loading || event.message == null || System.currentTimeMillis() > deadline) {
            return;
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (text == null || text.isEmpty()) {
            return;
        }

        Matcher h = HEADER.matcher(text);
        if (h.find()) {
            celle = new Celle();
            celle.id = h.group(1);
            event.setCanceled(true);
            return;
        }
        if (celle == null) {
            return;
        }

        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean part = true;
        if (lower.startsWith("lokation:")) {
            celle.gang = value(trimmed);
        } else if (lower.startsWith("ejer:")) {
            celle.owner = value(trimmed);
        } else if (lower.startsWith("blok:")) {
            celle.block = value(trimmed);
        } else if (lower.startsWith("tid:")) {
            celle.tid = value(trimmed);
        } else if (lower.startsWith("medlemmer:")) {
            String v = value(trimmed);
            celle.members.clear();
            if (!v.isEmpty()) {
                for (String m : v.split("\\s+")) {
                    if (!m.isEmpty()) {
                        celle.members.add(m);
                    }
                }
            }
            loading = false; // members is the last line of the block
        } else {
            part = false;
        }
        if (part) {
            event.setCanceled(true); // keep our own lookup out of chat
        }
    }

    private static String value(String line) {
        int c = line.indexOf(':');
        return c >= 0 ? line.substring(c + 1).trim() : line.trim();
    }
}
