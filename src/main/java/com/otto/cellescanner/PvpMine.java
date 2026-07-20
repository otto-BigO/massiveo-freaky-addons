package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PvP Mine watcher: reads the drop-timer sign for the "PvP minen" and shows it
 * on a HUD, and detects when another player is inside the mine area (a fixed box)
 * while it's in render distance - alerting once when someone enters, so you know
 * to go contest the random diamond drops.
 */
public class PvpMine {

    public static int lastWidth = 108;
    public static int lastHeight = 60;

    // Mine area box (the two corners Otto gave), inclusive of the block span.
    private static final int MIN_X = -19, MAX_X = -11;
    private static final int MIN_Y = 34, MAX_Y = 43;
    private static final int MIN_Z = -593, MAX_Z = -588;
    private static final BlockPos SIGN = new BlockPos(-47, 49, -608);

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DUR = Pattern.compile("(\\d+)\\s*(timer?|minutter?|sekunder?|min|sek|[hmst])", Pattern.CASE_INSENSITIVE);

    private String[] signLines = null;
    private final Set<String> inMine = new HashSet<String>();
    private List<String> inMineList = new ArrayList<String>();
    private int tick = 0;
    private static boolean errorLogged = false;

    // Cached drop timer: seconds remaining at the moment it was last read off the
    // sign, the wall-clock anchor, and the biggest value ever seen (the full
    // cycle). Lets the countdown keep ticking while the sign is out of range and
    // loop back to the top when it hits 0.
    private long timerRemaining = -1;
    private long timerAnchor = 0;
    private long timerMax = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CelleScannerMod.config.pvpMineEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (++tick % 5 != 0) {
            return;
        }

        // Read the drop-timer sign if it's loaded, and re-anchor the cached timer
        // ONLY on a fresh read (not from stale signLines) so it keeps ticking when
        // the sign is out of range.
        try {
            TileEntity te = mc.theWorld.getTileEntity(SIGN);
            if (te instanceof TileEntitySign) {
                TileEntitySign s = (TileEntitySign) te;
                if (s.signText != null && s.signText.length >= 4) {
                    String[] lines = new String[4];
                    for (int i = 0; i < 4; i++) {
                        lines[i] = clean(s.signText[i].getUnformattedText());
                    }
                    signLines = lines;
                    long t = parseTime(lines);
                    if (t >= 0) {
                        timerRemaining = t;
                        timerAnchor = System.currentTimeMillis();
                        if (t > timerMax) {
                            timerMax = t;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Who's in the mine right now (excluding you).
        Set<String> now = new HashSet<String>();
        List<String> list = new ArrayList<String>();
        for (Object o : mc.theWorld.playerEntities) {
            if (!(o instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer p = (EntityPlayer) o;
            if (p == mc.thePlayer || !inBox(p)) {
                continue;
            }
            now.add(p.getName());
            list.add(p.getName());
        }

        if (CelleScannerMod.config.pvpMineAlert) {
            for (String n : now) {
                if (!inMine.contains(n)) {
                    mc.thePlayer.addChatMessage(new ChatComponentText(
                            EnumChatFormatting.RED + "[PvP Mine] " + EnumChatFormatting.WHITE + n + " er i minen!"));
                    mc.thePlayer.playSound("note.pling", 1.0F, 1.5F);
                }
            }
        }
        inMine.clear();
        inMine.addAll(now);
        inMineList = list;
    }

    private static boolean inBox(EntityPlayer p) {
        return p.posX >= MIN_X && p.posX <= MAX_X + 1
                && p.posY >= MIN_Y - 1 && p.posY <= MAX_Y + 2
                && p.posZ >= MIN_Z && p.posZ <= MAX_Z + 1;
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || !CelleScannerMod.config.pvpMineEnabled) {
            return;
        }
        try {
            render();
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                System.err.println("[CelleScanner] PvP Mine HUD failed: " + t);
            }
        }
    }

    private void render() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;

        List<String> lines = new ArrayList<String>();
        lines.add(EnumChatFormatting.GOLD + "PvP Mine");
        long live = liveRemainingSeconds();
        if (live >= 0) {
            lines.add(EnumChatFormatting.GRAY + "Drop om: " + EnumChatFormatting.WHITE + fmt(live));
        } else if (signLines == null) {
            lines.add(EnumChatFormatting.DARK_GRAY + "(timer ikke set endnu)");
        } else {
            lines.add(EnumChatFormatting.DARK_GRAY + "(ingen timer på skiltet)");
        }
        if (inMineList.isEmpty()) {
            lines.add(EnumChatFormatting.GREEN + "Ingen i minen");
        } else {
            lines.add(EnumChatFormatting.RED + "" + inMineList.size() + " i minen:");
            for (String nm : inMineList) {
                lines.add(EnumChatFormatting.WHITE + " " + nm);
            }
        }

        int w = 0;
        for (String s : lines) {
            w = Math.max(w, fr.getStringWidth(s));
        }
        int lineH = fr.FONT_HEIGHT + 1;
        int boxH = lines.size() * lineH + 5;
        lastWidth = Math.max(108, w);
        lastHeight = Math.max(20, boxH);

        ScaledResolution sr = new ScaledResolution(mc);
        int x = CelleScannerMod.config.pvpMineX != null ? CelleScannerMod.config.pvpMineX : 4;
        int y = CelleScannerMod.config.pvpMineY != null ? CelleScannerMod.config.pvpMineY : sr.getScaledHeight() - boxH - 4;

        Gui.drawRect(x - 3, y - 3, x + w + 3, y + boxH - 3, 0x88000000);
        int dy = y;
        for (String s : lines) {
            fr.drawStringWithShadow(s, x, dy, 0xFFFFFF);
            dy += lineH;
        }
    }

    /** Seconds left on the cached timer, extrapolated to now and looped at 0. -1 if never read. */
    private long liveRemainingSeconds() {
        if (timerAnchor == 0 || timerRemaining < 0) {
            return -1;
        }
        long elapsed = (System.currentTimeMillis() - timerAnchor) / 1000L;
        long rem = timerRemaining - elapsed;
        if (timerMax > 0) {
            rem = ((rem % timerMax) + timerMax) % timerMax; // wrap: 0 restarts at the top
        } else if (rem < 0) {
            rem = 0;
        }
        return rem;
    }

    private static long parseTime(String[] lines) {
        if (lines == null) {
            return -1;
        }
        for (String s : lines) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            Matcher m = TIME.matcher(s);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1)) * 60L + Long.parseLong(m.group(2));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        // Fallback: duration units (e.g. "4m 32s", "3 min").
        for (String s : lines) {
            if (s == null) {
                continue;
            }
            Matcher m = DUR.matcher(s);
            long total = 0;
            boolean any = false;
            while (m.find()) {
                any = true;
                long v = Long.parseLong(m.group(1));
                char u = Character.toLowerCase(m.group(2).charAt(0));
                if (u == 't' || u == 'h') {
                    total += v * 3600L;
                } else if (u == 'm') {
                    total += v * 60L;
                } else {
                    total += v;
                }
            }
            if (any) {
                return total;
            }
        }
        return -1;
    }

    private static String fmt(long s) {
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, sec);
        }
        return String.format("%d:%02d", m, sec);
    }

    private static String clean(String t) {
        if (t == null) {
            return "";
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(t).trim();
    }
}
