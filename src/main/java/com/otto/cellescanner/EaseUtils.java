package com.otto.cellescanner;

/**
 * Apple-style easing functions and interpolation utilities for Minecraft 1.8.9.
 * Fast, pure math calculations without heap allocation per frame.
 */
public final class EaseUtils {

    private EaseUtils() {
    }

    public static float easeOutCubic(float x) {
        float f = 1.0f - x;
        return 1.0f - f * f * f;
    }

    public static float easeOutQuint(float x) {
        float f = 1.0f - x;
        return 1.0f - f * f * f * f * f;
    }

    public static float easeOutSine(float x) {
        return (float) Math.sin((x * Math.PI) / 2.0);
    }

    /**
     * Micro-overshoot ease for premium tactile feel (scale: 1.00 -> 1.02 -> 1.00).
     */
    public static float easeOutBackSubtle(float x) {
        float c1 = 1.2f;
        float c3 = c1 + 1.0f;
        float f = x - 1.0f;
        return 1.0f + c3 * f * f * f + c1 * f * f;
    }

    /**
     * Frame-rate independent exponential smooth dampening (lerp).
     */
    public static float damp(float current, float target, float speed, float deltaSeconds) {
        return current + (target - current) * (1.0f - (float) Math.exp(-speed * deltaSeconds));
    }
}
