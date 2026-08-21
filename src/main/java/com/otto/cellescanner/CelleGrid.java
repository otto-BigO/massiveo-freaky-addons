package com.otto.cellescanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * How fast the server's celle countdown actually runs.
 *
 * A celle sign does not tick every second. It drops in whole twenty minute
 * steps, so a sign reading "4t" is one that will read "3t 40m" one step from
 * now, and the celle frees twelve steps from now. Everything about predicting
 * when a celle comes free is therefore a question of when the next step lands,
 * not of counting seconds.
 *
 * <h3>Why this is measured rather than assumed</h3>
 *
 * A step is not exactly twenty minutes of wall clock. The server runs it on its
 * own schedule and that schedule drifts, so the real figure is near 1200
 * seconds but not on it. The old code carried 1199600 ms as a constant,
 * measured once over twelve ticks, and being 868 ms short per step does not
 * sound like much until it is multiplied by the number of steps left:
 *
 * <pre>
 *    1 hour out  =  3 steps  =   2.6 s early
 *    4 hours out = 12 steps  =  10.4 s early
 *   20 hours out = 60 steps  =  52   s early
 * </pre>
 *
 * That is the "timer is out by about ten seconds" everybody notices, and it is
 * worse the further out the celle. A prediction built by multiplying a constant
 * has this problem no matter how good the constant is.
 *
 * <h3>So it learns</h3>
 *
 * Every time this client watches a sign step down by exactly one step, the gap
 * since the previous witnessed step is a direct measurement of the period. Those
 * get kept, and the estimate is their median, which ignores the occasional
 * sample taken across a missed step or a laggy tick rather than being dragged
 * by it.
 *
 * The estimate is saved, so a fresh session starts from what the last one
 * learned instead of from a guess.
 */
public final class CelleGrid {

    /** The size of one step, in the sign's own units. Not a measurement. */
    public static final long STEP_SECONDS = 1200L;

    /**
     * Where to start before this install has measured anything for itself.
     *
     * The median of 22 clean single steps recorded on Otto's own client, which
     * is a better opening guess than a round twenty minutes and much better
     * than the 1199600 that used to be here. It is only a starting point: five
     * witnessed steps and the estimate is this client's own.
     */
    public static final long DEFAULT_PERIOD_MS = 1200468L;

    /**
     * How far from twenty minutes a sample may be and still be believed.
     *
     * A gap well outside this is not a slow step, it is a step this client
     * simply did not see, and averaging those in would drag the estimate
     * towards nonsense.
     */
    private static final long SAMPLE_SLACK_MS = 50000L;

    /** Enough to be steady, few enough to follow the server if it changes. */
    private static final int MAX_SAMPLES = 32;

    /** Below this the median is noisier than the value it would replace. */
    private static final int MIN_SAMPLES = 5;

    private static final Deque<Long> samples = new ArrayDeque<Long>();
    private static long periodMs = DEFAULT_PERIOD_MS;

    private CelleGrid() {
    }

    /** The best current estimate of one step, in milliseconds. */
    public static long periodMs() {
        return periodMs;
    }

    public static int sampleCount() {
        return samples.size();
    }

    /** Restores what an earlier session learned. */
    public static synchronized void load(long savedPeriodMs) {
        if (isPlausible(savedPeriodMs)) {
            periodMs = savedPeriodMs;
        }
    }

    private static boolean isPlausible(long ms) {
        return ms > 0 && Math.abs(ms - DEFAULT_PERIOD_MS) <= SAMPLE_SLACK_MS;
    }

    /**
     * Offers one witnessed step as a measurement.
     *
     * @param gapMs          wall clock between this step and the previous one
     * @param droppedSeconds how far the sign fell; only a single clean step counts
     * @return whether the estimate moved, which is only of interest to the log
     */
    public static synchronized boolean sample(long gapMs, long droppedSeconds) {
        // Exactly one step down. A bigger drop is a step that was missed while
        // out of range, and a rise is the owner buying more time.
        if (droppedSeconds != STEP_SECONDS) {
            return false;
        }
        if (!isPlausible(gapMs)) {
            return false;
        }

        samples.addLast(Long.valueOf(gapMs));
        while (samples.size() > MAX_SAMPLES) {
            samples.removeFirst();
        }
        if (samples.size() < MIN_SAMPLES) {
            return false;
        }

        List<Long> sorted = new ArrayList<Long>(samples);
        Collections.sort(sorted);
        long median = sorted.get(sorted.size() / 2).longValue();
        if (median == periodMs) {
            return false;
        }
        periodMs = median;
        return true;
    }

    /** How many whole steps are left when a sign reads this. */
    public static long stepsLeft(long remainingSeconds) {
        if (remainingSeconds <= 0L) {
            return 0L;
        }
        return remainingSeconds / STEP_SECONDS;
    }

    /**
     * When a celle should come free, in wall clock millis.
     *
     * The steps left times the measured length of a step, from the moment the
     * sign was last seen to change. Both the countdown on screen and the buyer
     * go through here, so they can never disagree about when zero is.
     */
    public static long freeAt(long remainingSeconds, long valueUpdatedAt) {
        if (valueUpdatedAt <= 0L) {
            return -1L;
        }
        return valueUpdatedAt + stepsLeft(remainingSeconds) * periodMs;
    }

    /** Milliseconds until it comes free, never negative. */
    public static long remainingMs(long remainingSeconds, long valueUpdatedAt) {
        long at = freeAt(remainingSeconds, valueUpdatedAt);
        if (at < 0L) {
            return 0L;
        }
        return Math.max(0L, at - System.currentTimeMillis());
    }

    /**
     * How far out a prediction of this many steps could be.
     *
     * The per-step error is small and the count multiplies it, so a celle
     * twenty hours out is a much vaguer promise than one twenty minutes out.
     * The buyer uses this to start clicking earlier on the vague ones rather
     * than trusting them to the millisecond.
     */
    public static long uncertaintyMs(long remainingSeconds) {
        // Once the period has been measured here the spread between samples is
        // tens of milliseconds. Until then the figure is inherited from another
        // machine, so it deserves a good deal less trust.
        long perStep = samples.size() >= MIN_SAMPLES ? 40L : 250L;
        return Math.min(6000L, stepsLeft(remainingSeconds) * perStep);
    }
}
