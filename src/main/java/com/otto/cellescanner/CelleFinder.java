package com.otto.cellescanner;

import net.minecraft.client.Minecraft;

/**
 * Tracks which single celle id (if any) the player is currently trying to
 * relocate via GuiCelleFinder. Purely in-memory / per-session, and
 * deliberately not persisted the way CellePositions is - "what am I
 * looking for right now" is a momentary thing, unlike CellePositions'
 * permanent memory of where a celle physically is.
 */
public final class CelleFinder {

    private static volatile String targetId = null;

    private CelleFinder() {
    }

    public static void setTarget(String id) {
        targetId = (id == null || id.trim().isEmpty()) ? null : id.trim();
    }

    public static void clearTarget() {
        targetId = null;
    }

    public static String getTarget() {
        return targetId;
    }

    public static boolean hasTarget() {
        return targetId != null;
    }

    /** Null if there's no active target, or the target has never been scanned by this client. */
    public static CellePositions.Entry getTargetPosition() {
        return hasTarget() ? CellePositions.get(targetId) : null;
    }

    private static final String[] COMPASS = {"N", "NØ", "Ø", "SØ", "S", "SV", "V", "NV"};

    /** 8-point compass bearing from a delta position, using Minecraft's world axes (-Z = north, +X = east) - absolute, not relative to which way the player is facing. */
    public static String compassDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        if (angle < 0) {
            angle += 360;
        }
        int index = ((int) Math.round(angle / 45.0)) % 8;
        return COMPASS[index];
    }

    /** One-line status for the HUD/GUI: distance + direction if known, or an explanation of why not. Null if there's no active target at all. */
    public static String describeTarget(Minecraft mc) {
        if (!hasTarget()) {
            return null;
        }
        CellePositions.Entry pos = getTargetPosition();
        if (pos == null) {
            return "Finder: " + targetId + " - ikke set endnu";
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return "Finder: " + targetId + " - " + formatKnownPosition(pos);
        }
        if (mc.theWorld.provider.getDimensionId() != pos.dimension) {
            return "Finder: " + targetId + " - anden dimension (" + formatKnownPosition(pos) + ")";
        }

        double dx = pos.x + 0.5 - mc.thePlayer.posX;
        double dy = pos.y - mc.thePlayer.posY;
        double dz = pos.z + 0.5 - mc.thePlayer.posZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String vertical = dy > 3 ? " (op)" : dy < -3 ? " (ned)" : "";

        return String.format("Finder: %s - %.0fm mod %s%s", targetId, dist, compassDirection(dx, dz), vertical);
    }

    private static String formatKnownPosition(CellePositions.Entry pos) {
        return pos.x + ", " + pos.y + ", " + pos.z;
    }
}
