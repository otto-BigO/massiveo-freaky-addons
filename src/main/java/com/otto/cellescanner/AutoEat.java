package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;

/**
 * Shared auto-eat used by both the Auto Mine bot and Walk to celle, so neither
 * starves (sprinting drains hunger). Eats a food item from the hotbar when hunger
 * drops, holds right-click until full, and restores the previously held slot. It
 * only ever holds an ItemFood - it never moves items (which the server flags).
 *
 * {@link #tick} returns true while it's actively eating, so the caller holds still.
 */
public final class AutoEat {

    private static final int EAT_BELOW = 16;
    private static final int EAT_FULL = 20;

    private static boolean eating = false;
    private static int prevSlot = -1;

    private AutoEat() {
    }

    /** Eat if hungry. Returns true while eating (the caller should hold still). */
    public static boolean tick(Minecraft mc) {
        if (mc.thePlayer == null) {
            return false;
        }
        int food = mc.thePlayer.getFoodStats().getFoodLevel();
        if (!eating) {
            if (food > EAT_BELOW) {
                return false;
            }
            int slot = findFoodSlot(mc);
            if (slot < 0) {
                return false; // no food in the hotbar - nothing we can do
            }
            eating = true;
            prevSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
        }

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        boolean holdingFood = held != null && held.getItem() instanceof ItemFood;
        if (food >= EAT_FULL) {
            stop(mc);
            return false;
        }
        if (!holdingFood) {
            int slot = findFoodSlot(mc); // ate the whole stack - grab another
            if (slot < 0) {
                stop(mc);
                return false;
            }
            mc.thePlayer.inventory.currentItem = slot;
        }

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        return true;
    }

    public static void stop(Minecraft mc) {
        if (mc.gameSettings != null) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
        if (prevSlot >= 0 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = prevSlot;
        }
        prevSlot = -1;
        eating = false;
    }

    public static boolean isEating() {
        return eating;
    }

    /** A hotbar slot holding food (just a held-item change, never a moved item), or -1. */
    private static int findFoodSlot(Minecraft mc) {
        ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
        for (int i = 0; i < 9; i++) {
            if (inv[i] != null && inv[i].getItem() instanceof ItemFood) {
                return i;
            }
        }
        return -1;
    }
}
