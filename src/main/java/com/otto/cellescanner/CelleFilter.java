package com.otto.cellescanner;

import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.List;

/**
 * Thin accessor for "which celler currently matter" - any celle (SOLGT or
 * TIL_SALG) whose remaining time falls inside [minHours, maxHours], soonest
 * first. Built from CelleScanner's SESSION cache (every celle seen this run),
 * not just the currently-loaded live cache, so the HUD and ESP keep showing a
 * celle after its chunk unloads or after a relog/death - it only clears when
 * the game closes. Filtered to the player's current dimension.
 */
public final class CelleFilter {

    private CelleFilter() {
    }

    public static List<Celle> collectUpcoming() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return Collections.emptyList();
        }
        return CelleScanner.upcomingForDimension(mc.theWorld.provider.getDimensionId());
    }
}
