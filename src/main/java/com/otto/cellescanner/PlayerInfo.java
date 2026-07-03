package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Player Info addon: shift + right-click a player (or search by name) opens a
 * menu (GuiPlayerInfo). On open it runs "/ce find &lt;name&gt;" to list/count
 * the player's celler; clicking a celle in the side panel runs "/ce info
 * &lt;id&gt;" to show that celle's details. Both replies are read from chat and
 * hidden.
 */
public class PlayerInfo {

    private static final Pattern HEADER = Pattern.compile("\\{\\s*([A-Za-z]{1,2}[0-9]{2,5})\\s*\\}");
    private static final Pattern CELLE_ID = Pattern.compile("\\b[A-Za-z]{1,2}[0-9]{2,5}\\b");
    private static final long WINDOW_MS = 4000L;

    /** Parsed "/ce info" block for a single celle. */
    public static final class Celle {
        public String id;
        public String gang;    // Lokation
        public String owner;   // Ejer
        public String block;   // Blok
        public String tid;     // raw Tid text
        public final List<String> members = new ArrayList<>();
    }

    // "/ce find <player>": the player's celler (count + list).
    private static final Set<String> celleIds = new LinkedHashSet<String>();
    private static volatile boolean findActive = false;
    private static volatile long findDeadline = 0L;

    // "/ce info <celleId>": the celle clicked in the side panel.
    private static volatile Celle selected = null;
    private static volatile String selectedId = null;
    private static volatile boolean infoActive = false;
    private static volatile long infoDeadline = 0L;

    /** Runs "/ce find <name>" for the player's celler list + count. */
    public static void lookup(String username) {
        if (username == null || username.isEmpty()) {
            return;
        }
        celleIds.clear();
        selected = null;
        selectedId = null;
        infoActive = false;
        findActive = true;
        findDeadline = System.currentTimeMillis() + WINDOW_MS;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/ce find " + username);
        }
    }

    /** Runs "/ce info <celleId>" for one celle's details (from clicking it in the panel). */
    public static void selectCelle(String celleId) {
        if (celleId == null || celleId.isEmpty()) {
            return;
        }
        selected = null;
        selectedId = celleId;
        infoActive = true;
        infoDeadline = System.currentTimeMillis() + WINDOW_MS;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.sendChatMessage("/ce info " + celleId);
        }
    }

    public static int getCelleCount() {
        return celleIds.size();
    }

    public static List<String> getCellerList() {
        return new ArrayList<String>(celleIds);
    }

    public static Celle getSelectedCelle() {
        return selected;
    }

    public static String getSelectedId() {
        return selectedId;
    }

    public static boolean isFindLoading() {
        return findActive && System.currentTimeMillis() <= findDeadline && celleIds.isEmpty();
    }

    public static boolean isSelectedLoading() {
        return infoActive && System.currentTimeMillis() <= infoDeadline && selected == null;
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
        if (event.message == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean info = infoActive && now <= infoDeadline;
        boolean find = findActive && now <= findDeadline;
        if (!info && !find) {
            return;
        }
        String text = EnumChatFormatting.getTextWithoutFormattingCodes(event.message.getUnformattedText());
        if (text == null || text.isEmpty()) {
            return;
        }

        // The selected celle's /ce info block.
        if (info) {
            Matcher h = HEADER.matcher(text);
            if (h.find()) {
                selected = new Celle();
                selected.id = h.group(1);
                event.setCanceled(true);
                return;
            }
            if (selected != null) {
                String tr = text.trim();
                String lo = tr.toLowerCase(Locale.ROOT);
                if (lo.startsWith("lokation:")) {
                    selected.gang = value(tr);
                    event.setCanceled(true);
                    return;
                }
                if (lo.startsWith("ejer:")) {
                    selected.owner = value(tr);
                    event.setCanceled(true);
                    return;
                }
                if (lo.startsWith("blok:")) {
                    selected.block = value(tr);
                    event.setCanceled(true);
                    return;
                }
                if (lo.startsWith("tid:")) {
                    selected.tid = value(tr);
                    event.setCanceled(true);
                    return;
                }
                if (lo.startsWith("medlemmer:")) {
                    String v = value(tr);
                    selected.members.clear();
                    if (!v.isEmpty()) {
                        for (String m : v.split("\\s+")) {
                            if (!m.isEmpty()) {
                                selected.members.add(m);
                            }
                        }
                    }
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // The "/ce find" list: collect celle ids.
        if (find) {
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
