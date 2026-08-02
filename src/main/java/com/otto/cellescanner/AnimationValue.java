package com.otto.cellescanner;

/**
 * Animated float property that interpolates smoothly using Easing curves.
 * Zero GC allocations during rendering.
 */
public class AnimationValue {

    private float startValue;
    private float targetValue;
    private float currentValue;
    private long startTimeMs;
    private long durationMs;
    private boolean active;

    public AnimationValue(float initialValue) {
        this.startValue = initialValue;
        this.targetValue = initialValue;
        this.currentValue = initialValue;
        this.startTimeMs = System.currentTimeMillis();
        this.durationMs = 1;
        this.active = false;
    }

    public void animateTo(float target, long durationMs) {
        if (Math.abs(this.targetValue - target) < 0.0001f && active) {
            return;
        }
        this.startValue = getValue();
        this.targetValue = target;
        this.durationMs = Math.max(1, durationMs);
        this.startTimeMs = System.currentTimeMillis();
        this.active = true;
    }

    public void setValueInstant(float val) {
        this.startValue = val;
        this.targetValue = val;
        this.currentValue = val;
        this.active = false;
    }

    public float getValue() {
        if (!active) {
            return targetValue;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        if (elapsed >= durationMs) {
            currentValue = targetValue;
            active = false;
            return targetValue;
        }
        float progress = (float) elapsed / (float) durationMs;
        float eased = EaseUtils.easeOutCubic(progress);
        currentValue = startValue + (targetValue - startValue) * eased;
        return currentValue;
    }

    public float getTargetValue() {
        return targetValue;
    }

    public boolean isFinished() {
        return !active || (System.currentTimeMillis() - startTimeMs) >= durationMs;
    }
}
