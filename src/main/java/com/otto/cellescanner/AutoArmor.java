package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * Auto Armour Addon.
 * Allows quick-equipping the best armor set from inventory or stripping
 * all equipped armor back into main inventory using windowClick (shift-click).
 */
public class AutoArmor {

    private static long lastEquipTime = 0;

    /**
     * Toggles armor state:
     * If armor is currently equipped on the player: strips all 4 armor slots into inventory.
     * If any armor slot is empty: searches inventory for best available armor pieces and equips them.
     */
    public static void toggleArmor() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEquipTime < 250) {
            return; // Debounce
        }
        lastEquipTime = now;

        EntityPlayer player = mc.thePlayer;
        boolean hasEquippedArmor = player.getCurrentArmor(0) != null
                || player.getCurrentArmor(1) != null
                || player.getCurrentArmor(2) != null
                || player.getCurrentArmor(3) != null;

        if (hasEquippedArmor) {
            stripArmor(mc, player);
        } else {
            equipArmor(mc, player);
        }
    }

    private static void stripArmor(Minecraft mc, EntityPlayer player) {
        // Armor slots in ContainerPlayer: 5=Helmet, 6=Chestplate, 7=Leggings, 8=Boots
        int stripped = 0;
        for (int slot = 5; slot <= 8; slot++) {
            ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
            if (stack != null) {
                // Shift-click armor piece back to inventory (mode = 1)
                mc.playerController.windowClick(0, slot, 0, 1, player);
                stripped++;
            }
        }
        if (mc.thePlayer != null && stripped > 0) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.GREEN + "[Auto Armour] Tog " + stripped + " rustningsdele af."));
        }
    }

    private static void equipArmor(Minecraft mc, EntityPlayer player) {
        // Target armor types: 0 = Helmet, 1 = Chestplate, 2 = Leggings, 3 = Boots
        int equipped = 0;
        for (int type = 0; type < 4; type++) {
            // Check if slot is empty (armorType index 0=boots, 1=legs, 2=chest, 3=helm in Minecraft entity array)
            if (player.getCurrentArmor(type) != null) {
                continue;
            }

            int bestSlot = findBestArmorSlot(player, type);
            if (bestSlot != -1) {
                // Shift-click best armor item into armor slot (mode = 1)
                mc.playerController.windowClick(0, bestSlot, 0, 1, player);
                equipped++;
            }
        }

        if (mc.thePlayer != null && equipped > 0) {
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.GREEN + "[Auto Armour] Iførte " + equipped + " rustningsdele."));
        }
    }

    private static int findBestArmorSlot(EntityPlayer player, int targetType) {
        // TargetType: 0 = Boots, 1 = Leggings, 2 = Chestplate, 3 = Helmet
        int bestSlot = -1;
        int bestProt = -1;

        // Check Inventory slots 9 to 44
        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) stack.getItem();
                // armor.armorType: 0 = Helmet, 1 = Chestplate, 2 = Leggings, 3 = Boots
                int matchType = 3 - armor.armorType;
                if (matchType == targetType) {
                    int prot = armor.damageReduceAmount;
                    if (prot > bestProt) {
                        bestProt = prot;
                        bestSlot = slot;
                    }
                }
            }
        }
        return bestSlot;
    }
}
