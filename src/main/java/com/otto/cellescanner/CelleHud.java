package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Small draggable HUD listing upcoming celler - i.e. any celle (SOLGT or
 * TIL_SALG) whose remaining timer falls inside [minHours, maxHours] -
 * soonest first. A SOLGT celle inside the window is one that's about to
 * flip to TIL_SALG; a TIL_SALG celle inside the window is already
 * available. Both count as "upcoming" per the original spec.
 * Position is moved via /celler move (opens GuiCelleHudMover) and persisted
 * in CelleConfig.hudX / hudY. Which optional info (owner, status tag,
 * distance, seconds) is shown is configurable via the Settings screen.
 */
public class CelleHud {

    private static final int MIN_WIDTH = 90;

    // The actual on-screen rectangle drawn by the last render pass, in GUI
    // pixels: [lastBoxLeft, lastBoxTop] to [lastBoxRight, lastBoxBottom].
    // The HUD sizes itself to its content now, so the drag hitbox and the
    // on-screen clamp in GuiCelleHudMover read these instead of guessing at a
    // fixed size. Defaults cover the "nothing drawn yet" case.
    static int lastBoxLeft = 6;
    static int lastBoxTop = 6;
    static int lastBoxRight = 6 + MIN_WIDTH;
    static int lastBoxBottom = 6 + 40;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) {
            return;
        }

        boolean editing = mc.currentScreen instanceof GuiCelleHudMover;
        if (!CelleScannerMod.config.enabled && !editing) {
            return;
        }

        // Always draw the box (with a placeholder line if nothing currently
        // qualifies) so it's never ambiguous whether the mod is running.
        List<Celle> entries = CelleFilter.collectUpcoming();

        FontRenderer fr = mc.fontRendererObj;
        int x = CelleScannerMod.config.hudX;
        int y = CelleScannerMod.config.hudY;
        int lineHeight = fr.FONT_HEIGHT + 2;

        int shown = Math.min(entries.size(), Math.max(CelleScannerMod.config.maxHudEntries, 0));

        // Build the text for each row first so the box can size itself to
        // whatever optional info is currently turned on, instead of using
        // a fixed width that clips longer lines (owner names especially).
        List<String> nameLines = new ArrayList<String>();
        List<String> timeLines = new ArrayList<String>();
        for (int i = 0; i < shown; i++) {
            Celle c = entries.get(i);

            StringBuilder nameLine = new StringBuilder(c.celleId);
            if (CelleScannerMod.config.showStatusTag) {
                nameLine.append(c.status == CelleStatus.TIL_SALG ? " (til salg)" : " (solgt)");
            }
            if (CelleScannerMod.config.showOwner && c.owner != null && !c.owner.isEmpty()) {
                nameLine.append(" - ").append(c.owner);
            }
            nameLines.add(nameLine.toString());

            StringBuilder timeLine = new StringBuilder(formatDuration(c));
            if (CelleScannerMod.config.showDistance) {
                timeLine.append("  ").append(String.format("%.0fm", distanceTo(mc.thePlayer, c)));
            }
            timeLines.add(timeLine.toString());
        }

        // Celle Finder status line - shown whenever a finder target is
        // active, regardless of the hour window, so you keep seeing it
        // while walking around with the GUI closed.
        String finderLine = CelleFinder.hasTarget() ? CelleFinder.describeTarget(mc) : null;

        int textWidth = fr.getStringWidth("Celle Scanner");
        textWidth = Math.max(textWidth, fr.getStringWidth("KOMMER SNART"));
        textWidth = Math.max(textWidth, fr.getStringWidth("(ingen lige nu)"));
        for (String s : nameLines) {
            textWidth = Math.max(textWidth, fr.getStringWidth(s));
        }
        for (String s : timeLines) {
            textWidth = Math.max(textWidth, fr.getStringWidth(s));
        }
        if (finderLine != null) {
            textWidth = Math.max(textWidth, fr.getStringWidth(finderLine));
        }
        int boxWidth = Math.max(MIN_WIDTH, textWidth + 6);

        int lines = 2 + shown * 2 + (finderLine != null ? 1 : 0);
        int boxHeight = lines * lineHeight + 6;

        lastBoxLeft = x - 4;
        lastBoxTop = y - 4;
        lastBoxRight = x + boxWidth;
        lastBoxBottom = y + boxHeight;

        Gui.drawRect(x - 4, y - 4, x + boxWidth, y + boxHeight, 0x88000000);

        int drawY = y;
        fr.drawStringWithShadow("Celle Scanner", x, drawY, 0xFFFFFF);
        drawY += lineHeight + 2;
        fr.drawStringWithShadow("KOMMER SNART", x, drawY, 0xFFAA00);
        drawY += lineHeight;

        if (shown == 0) {
            fr.drawStringWithShadow("(ingen lige nu)", x, drawY, 0xAAAAAA);
        } else {
            for (int i = 0; i < shown; i++) {
                fr.drawStringWithShadow(nameLines.get(i), x, drawY, 0xFFFFFF);
                drawY += lineHeight;
                fr.drawStringWithShadow(timeLines.get(i), x, drawY, 0xAAAAAA);
                drawY += lineHeight;
            }
        }

        if (finderLine != null) {
            fr.drawStringWithShadow(finderLine, x, drawY, 0x55FFFF);
        }
    }

    private static double distanceTo(EntityPlayer player, Celle celle) {
        double dx = player.posX - (celle.position.getX() + 0.5);
        double dy = player.posY - (celle.position.getY() + 0.5);
        double dz = player.posZ - (celle.position.getZ() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (CelleScannerMod.config.showSeconds) {
            long s = seconds % 60;
            return String.format("%dh %02dm %02ds", h, m, s);
        }
        return String.format("%dh %02dm", h, m);
    }

    /**
     * Formats a celle's live countdown, prefixed with "~" if we haven't yet
     * personally witnessed a real refresh of its sign (see Celle.timerConfirmed) -
     * meaning the value is our best guess, not a confirmed-accurate reading.
     */
    public static String formatDuration(Celle c) {
        String base = formatDuration(c.liveRemainingSeconds());
        return c.timerConfirmed ? base : "~" + base;
    }
}
