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

/**
 * PvP Mine watcher: reads the drop-timer sign for the "PvP minen" and shows it
 * on a HUD, and detects when another player is inside the mine area (a fixed box)
 * while it's in render distance - alerting once when someone enters, so you know
 * to go contest the random diamond drops.
 */
public class PvpMine {

    // Mine area box (the two corners Otto gave), inclusive of the block span.
    private static final int MIN_X = -19, MAX_X = -11;
    private static final int MIN_Y = 34, MAX_Y = 43;
    private static final int MIN_Z = -593, MAX_Z = -588;
    private static final BlockPos SIGN = new BlockPos(-47, 49, -608);

    private String[] signLines = null;
    private final Set<String> inMine = new HashSet<String>();
    private List<String> inMineList = new ArrayList<String>();
    private int tick = 0;
    private static boolean errorLogged = false;

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

        // Read the drop-timer sign if it's loaded.
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
        if (signLines != null) {
            boolean any = false;
            for (String s : signLines) {
                if (s != null && !s.isEmpty()) {
                    lines.add(EnumChatFormatting.GRAY + s);
                    any = true;
                }
            }
            if (!any) {
                lines.add(EnumChatFormatting.DARK_GRAY + "(skilt tomt)");
            }
        } else {
            lines.add(EnumChatFormatting.DARK_GRAY + "(skilt ikke i syne)");
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

    private static String clean(String t) {
        if (t == null) {
            return "";
        }
        return EnumChatFormatting.getTextWithoutFormattingCodes(t).trim();
    }
}
