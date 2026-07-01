package com.otto.cellescanner;

import net.minecraft.util.BlockPos;

/**
 * In-memory representation of a celle (prison cell) sign.
 */
public class Celle {

    public final BlockPos position;
    public CelleStatus status;
    public String celleId;
    public String owner;          // only set when status == SOLGT
    public long remainingSeconds; // value as last read directly off the sign
    public long lastSeen;         // System.currentTimeMillis() of last scan that saw this sign
    public boolean notified;      // whether the "entered window" notification has fired

    // Wall-clock time (millis) at which remainingSeconds was last refreshed
    // because the sign's own text actually changed. The server only
    // updates a sign's countdown roughly every ~20 minutes, so this lets
    // us extrapolate a smooth, continuously-ticking value in between
    // instead of the display sitting frozen until the next sign refresh.
    public long valueUpdatedAt;

    // False until we've personally witnessed at least one real value
    // change for this celle (set in CelleScanner). The very first reading
    // of a freshly-discovered sign could already be partway through its
    // ~20min display cycle on the server - we have no way to know how far
    // in - so the live countdown might start out optimistic. Once we catch
    // an actual refresh, valueUpdatedAt is exact and stays accurate from
    // then on, so this flips permanently true.
    public boolean timerConfirmed;

    public Celle(BlockPos position) {
        this.position = position;
    }

    /** Raw value as last read off the sign - unchanged until the sign itself updates. */
    public double remainingHours() {
        return remainingSeconds / 3600.0;
    }

    /** remainingSeconds, extrapolated down to "now" since it was last refreshed. Never negative. */
    public long liveRemainingSeconds() {
        long elapsedSeconds = (System.currentTimeMillis() - valueUpdatedAt) / 1000L;
        return Math.max(0L, remainingSeconds - elapsedSeconds);
    }

    public double liveRemainingHours() {
        return liveRemainingSeconds() / 3600.0;
    }
}
