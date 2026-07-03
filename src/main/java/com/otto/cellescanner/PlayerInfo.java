package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player Info addon: shift + right-click another player (or search by name) to
 * open a menu (GuiPlayerInfo) with their armor, their celle (from "/ce info
 * &lt;name&gt;"), and how many celler they own in total (from "/ce find
 * &lt;name&gt;"). The two commands are spaced apart so the server's /ce cooldown
 * doesn't drop one, and their replies are read from chat then hidden.
 */
public class PlayerInfo {

    private static final Pattern HEADER = Pattern.compile("\\{\\s*([A-Za-z]{1,2}[0-9]{2,5})\\s*\\}");
    // Loose celle-id token, for counting /ce find output (same shape MineCeller uses).
    private static final Pattern CELLE_ID = Pattern.compile("\\b[A-Za-z]{1,2}[0-9]{2,5}\\b");
    private static final long WINDOW_MS = 4000L;
    private static final long FIND_DELAY_MS = 1500L;

    /** Parsed "/ce info" block for the player the menu is currently showing. */
    public static final class Celle {
        public String id;
        public String gang;    // Lokation
        public String owner;   // Ejer
        public String block;   // Blok
        public String tid;     // raw Tid text
        public final List<String> members = new ArrayList<>();
    }

    private static volatile String lookupName = null;
    private static volatile Celle celle = null;
    private static volatile boolean loading = false;
    private static volatile long deadline = 0L;

    // Delayed "/ce find" for the celler count + full list.
    private static volatile String findName = null;
    private static volatile boolean findPending = false;
    private static volatile boolean findActive = false;
    private static volatile long findAt = 0L;
    private static final Set<String> celleIds = new java.util.LinkedHashSet<String>();

    /** Kicks off "/ce info" now and "/ce find" shortly after, for the menu. */
    public static void lookup(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        lookupName = username.toLowerCase(Locale.ROOT);
        findName = username;
        celle = null;
        celleIds.clear();
        findActive = false;
        loading = true;
        deadline = now + WINDOW_MS;
        findPending = true;
        findAt = now + FIND_DELAY_MS;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/ce info " + username);
        }
    }

    public static Celle getCelle() {
        return celle;
    }

    /** Total distinct celler the player has access to, from the /ce find reply. */
    public static int getCelleCount() {
        return celleIds.size();
    }

    /** Every celle id the player has access to, in the order seen, for the side panel. */
    public static List<String> getCellerList() {
        return new ArrayList<String>(celleIds);
    }

    public static boolean isLoading() {
        if (loading && System.currentTimeMillis() > deadline && celle == null) {
            loading = false;
        }
        return loading;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !findPending) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < findAt) {
            return;
        }
        findPending = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && findName != null) {
            findActive = true;
            mc.thePlayer.sendChatMessage("/ce find " + findName);
            deadline = now + WINDOW_MS; // keep capturing for the find reply
        }
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

        // Info-block header: which celle this /ce info reply is about.
        Matcher h = HEADER.matcher(text);
        if (h.find()) {
            String id = h.group(1);
            celle = new Celle();
            celle.id = id;
            celleIds.add(id.toUpperCase(Locale.ROOT));
            event.setCanceled(true);
            return;
        }

        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (celle != null && lower.startsWith("lokation:")) {
            celle.gang = value(trimmed);
            event.setCanceled(true);
            return;
        }
        if (celle != null && lower.startsWith("ejer:")) {
            celle.owner = value(trimmed);
            event.setCanceled(true);
            return;
        }
        if (celle != null && lower.startsWith("blok:")) {
            celle.block = value(trimmed);
            event.setCanceled(true);
            return;
        }
        if (celle != null && lower.startsWith("tid:")) {
            celle.tid = value(trimmed);
            event.setCanceled(true);
            return;
        }
        if (celle != null && lower.startsWith("medlemmer:")) {
            String v = value(trimmed);
            celle.members.clear();
            if (!v.isEmpty()) {
                for (String m : v.split("\\s+")) {
                    if (!m.isEmpty()) {
                        celle.members.add(m);
                    }
                }
            }
            event.setCanceled(true);
            return;
        }

        // Once /ce find has been sent, remaining lines are its output: count the
        // celle ids and keep the raw lines for the side panel.
        if (findActive) {
            Matcher fm = CELLE_ID.matcher(text);
            boolean found = false;
            while (fm.find()) {
                celleIds.add(fm.group().toUpperCase(Locale.ROOT));
                found = true;
            }
            if (found) {
                event.setCanceled(true);
            }
        }
    }

    private static String value(String line) {
        int c = line.indexOf(':');
        return c >= 0 ? line.substring(c + 1).trim() : line.trim();
    }
}
