package com.otto.cellescanner;

import net.minecraft.util.BlockPos;

public class BaritoneIntegration {
    private static Boolean hasBaritone = null;

    public static boolean isAvailable() {
        if (hasBaritone == null) {
            try {
                Class.forName("baritone.api.BaritoneAPI");
                hasBaritone = true;
            } catch (Throwable t) {
                hasBaritone = false;
            }
        }
        return hasBaritone;
    }

    public static void walkTo(BlockPos pos) {
        if (pos == null) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object customGoal = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);

            Class<?> goalBlockClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Object goal = goalBlockClass.getConstructor(int.class, int.class, int.class).newInstance(pos.getX(), pos.getY(), pos.getZ());

            customGoal.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal")).invoke(customGoal, goal);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void cancel() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathing = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            pathing.getClass().getMethod("cancelEverything").invoke(pathing);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isPathing() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathing = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            return (Boolean) pathing.getClass().getMethod("isPathing").invoke(pathing);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean hasPath() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathing = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
            return (Boolean) pathing.getClass().getMethod("hasPath").invoke(pathing);
        } catch (Throwable t) {
            return false;
        }
    }
}
