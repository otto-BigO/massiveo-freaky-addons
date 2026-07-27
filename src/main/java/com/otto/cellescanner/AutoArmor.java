package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Humanized Anti-Cheat Safe Auto Armour Addon.
 * Equips or strips armor pieces asynchronously with randomized human-like delays (70ms - 140ms)
 * and randomized slot swap order to bypass server anti-cheat checks (GrimAC, Vulcan, Matrix, AAC).
 */
public class AutoArmor {

    public static final AutoArmor INSTANCE = new AutoArmor();
    private static final Random RANDOM = new Random();

    private final List<ArmorTask> taskQueue = new ArrayList<ArmorTask>();
    private long nextActionTime = 0;
    private int totalTasksExecuted = 0;
    private boolean isEquipOperation = true;

    private static class ArmorTask {
        int slot;
        boolean isHotbar;
        boolean isEquip;

        ArmorTask(int slot, boolean isHotbar, boolean isEquip) {
            this.slot = slot;
            this.isHotbar = isHotbar;
            this.isEquip = isEquip;
        }
    }

    private AutoArmor() {
    }

    /**
     * Toggles armor state:
     * If armor is currently equipped on the player: queues stripping all 4 armor slots.
     * If any armor slot is empty: searches inventory for best available armor pieces and queues equipping them.
     */
    public static void toggleArmor() {
        INSTANCE.startToggle();
    }

    public synchronized void startToggle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null || mc.thePlayer.sendQueue == null) {
            return;
        }

        if (!taskQueue.isEmpty()) {
            return; // Already executing a swap operation
        }

        EntityPlayer player = mc.thePlayer;
        boolean hasEquippedArmor = player.getCurrentArmor(0) != null
                || player.getCurrentArmor(1) != null
                || player.getCurrentArmor(2) != null
                || player.getCurrentArmor(3) != null;

        taskQueue.clear();
        totalTasksExecuted = 0;
        isEquipOperation = !hasEquippedArmor;

        if (hasEquippedArmor) {
            // Queue stripping armor (slots 5..8)
            for (int slot = 5; slot <= 8; slot++) {
                ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
                if (stack != null) {
                    taskQueue.add(new ArmorTask(slot, false, false));
                }
            }
        } else {
            // Queue equipping best armor
            for (int type = 0; type < 4; type++) {
                if (player.getCurrentArmor(type) != null) {
                    continue;
                }
                int bestSlot = findBestArmorSlot(player, type);
                if (bestSlot != -1) {
                    boolean isHotbar = bestSlot >= 36 && bestSlot <= 44;
                    taskQueue.add(new ArmorTask(bestSlot, isHotbar, true));
                }
            }
        }

        if (!taskQueue.isEmpty()) {
            // Randomize order so slot swap order varies (e.g. Helmet -> Boots vs Chest -> Legs)
            Collections.shuffle(taskQueue, RANDOM);
            nextActionTime = System.currentTimeMillis() + (40 + RANDOM.nextInt(40));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (taskQueue.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null || mc.thePlayer.sendQueue == null) {
            taskQueue.clear();
            return;
        }

        ArmorTask task = taskQueue.remove(0);
        executeTask(mc, task);
        totalTasksExecuted++;

        if (taskQueue.isEmpty()) {
            // All armor pieces swapped! Send completion message
            if (mc.thePlayer != null) {
                String actionStr = isEquipOperation ? "Iførte" : "Tog";
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        EnumChatFormatting.GREEN + "[Auto Armour] " + actionStr + " " + totalTasksExecuted + " rustningsdele."));
            }
        } else {
            // Schedule next piece with randomized human delay (70ms - 130ms)
            nextActionTime = now + (70 + RANDOM.nextInt(60));
        }
    }

    private void executeTask(Minecraft mc, ArmorTask task) {
        EntityPlayer player = mc.thePlayer;

        // Open inventory container state on server so anti-cheat acknowledges inventory action
        mc.thePlayer.sendQueue.addToSendQueue(
                new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));

        if (task.isHotbar) {
            int origSlot = player.inventory.currentItem;
            player.inventory.currentItem = task.slot - 36;
            ItemStack stack = player.inventory.getCurrentItem();
            if (stack != null) {
                mc.thePlayer.sendQueue.addToSendQueue(new C08PacketPlayerBlockPlacement(stack));
            }
            player.inventory.currentItem = origSlot;
        } else {
            // Shift-click item into/out of armor slot
            mc.playerController.windowClick(0, task.slot, 0, 1, player);
        }

        // Close inventory container state on server
        mc.thePlayer.sendQueue.addToSendQueue(new C0DPacketCloseWindow(0));
    }

    private static int findBestArmorSlot(EntityPlayer player, int targetType) {
        int bestSlot = -1;
        int bestProt = -1;

        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && stack.getItem() instanceof ItemArmor) {
                ItemArmor armor = (ItemArmor) stack.getItem();
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
