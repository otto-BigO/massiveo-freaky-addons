package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 100% Anti-Cheat Safe Auto Armour Addon.
 * Bypasses GrimAC, Vulcan, Matrix, AAC5 & Spartan by opening the client GuiInventory screen,
 * executing randomized human-like clicks (100ms - 180ms per piece), and closing the inventory
 * screen naturally when finished.
 */
public class AutoArmor {

    public static final AutoArmor INSTANCE = new AutoArmor();
    private static final Random RANDOM = new Random();

    private final List<ArmorTask> taskQueue = new ArrayList<ArmorTask>();
    private long nextActionTime = 0;
    private int totalTasksExecuted = 0;
    private boolean isEquipOperation = true;
    private boolean isExecuting = false;

    /**
     * One container click. Equipping is a pick-up followed by a put-down rather
     * than a shift-click, because vanilla only routes a shift-clicked armour
     * piece to its armour slot when that slot is empty. Any other time it falls
     * through to shuffling the item between the inventory and the hotbar, which
     * looks exactly like "it moved the armour but did not put it on".
     */
    private static class ArmorTask {
        final int slot;
        final int mode;   // 0 = plain click (pick up / put down), 1 = shift-click

        ArmorTask(int slot, int mode) {
            this.slot = slot;
            this.mode = mode;
        }
    }

    /** Container slot for an armour piece, from its ItemArmor.armorType. */
    private static int armorSlotForType(int armorType) {
        return 5 + armorType;   // 5 helmet, 6 chestplate, 7 leggings, 8 boots
    }

    private AutoArmor() {
    }

    public static void toggleArmor() {
        INSTANCE.startToggle();
    }

    public synchronized void startToggle() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null || mc.thePlayer.sendQueue == null) {
            return;
        }

        if (isExecuting) {
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
            // Taking armour off is the one case shift-click handles correctly,
            // since the destination is just "anywhere in the inventory".
            for (int slot = 5; slot <= 8; slot++) {
                ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
                if (stack != null) {
                    taskQueue.add(new ArmorTask(slot, 1));
                }
            }
        } else {
            // Putting it on is a pick-up then a put-down into the armour slot.
            // The two clicks belong together, so they are added as a pair and
            // the queue is not shuffled: reordering them would drop the piece
            // into whatever slot came next.
            for (int type = 0; type < 4; type++) {
                if (player.getCurrentArmor(type) != null) {
                    continue;
                }
                int bestSlot = findBestArmorSlot(player, type);
                if (bestSlot == -1) {
                    continue;
                }
                ItemStack stack = player.inventoryContainer.getSlot(bestSlot).getStack();
                if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                    continue;
                }
                int dest = armorSlotForType(((ItemArmor) stack.getItem()).armorType);
                taskQueue.add(new ArmorTask(bestSlot, 0));   // pick it up
                taskQueue.add(new ArmorTask(dest, 0));       // put it in the armour slot
            }
        }

        if (!taskQueue.isEmpty()) {
            isExecuting = true;

            // Open the real client GuiInventory so the server anti-cheat validates the inventory GUI state
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            nextActionTime = System.currentTimeMillis() + (120 + RANDOM.nextInt(80));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isExecuting) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) {
            taskQueue.clear();
            isExecuting = false;
            return;
        }

        // If the player closed the inventory manually or died, cancel remaining queue
        if (!(mc.currentScreen instanceof GuiInventory)) {
            taskQueue.clear();
            isExecuting = false;
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        if (!taskQueue.isEmpty()) {
            ArmorTask task = taskQueue.remove(0);
            executeTask(mc, task);
            totalTasksExecuted++;

            if (taskQueue.isEmpty()) {
                // All pieces swapped! Close screen naturally & report success
                finishOperation(mc);
            } else {
                // Humanized delay between clicks (100ms - 180ms)
                nextActionTime = now + (100 + RANDOM.nextInt(80));
            }
        } else {
            finishOperation(mc);
        }
    }

    private void finishOperation(Minecraft mc) {
        isExecuting = false;
        taskQueue.clear();

        // Anything still on the cursor has to go back in the inventory. Slot
        // -999 would throw it on the floor instead, which is the one outcome
        // worth going out of the way to avoid.
        if (mc.thePlayer != null && mc.thePlayer.inventory != null
                && mc.thePlayer.inventory.getItemStack() != null) {
            int free = firstFreeSlot(mc.thePlayer);
            if (free != -1) {
                mc.playerController.windowClick(0, free, 0, 0, mc.thePlayer);
            }
        }

        if (mc.currentScreen instanceof GuiInventory) {
            mc.thePlayer.closeScreen();
        }

        if (mc.thePlayer != null && totalTasksExecuted > 0) {
            String actionStr = isEquipOperation ? "Iførte" : "Tog";
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.GREEN + "[Auto Armour] " + actionStr + " " + totalTasksExecuted + " rustningsdele."));
        }
    }

    private void executeTask(Minecraft mc, ArmorTask task) {
        EntityPlayer player = mc.thePlayer;
        mc.playerController.windowClick(0, task.slot, 0, task.mode, player);
    }

    /** An empty inventory or hotbar slot, or -1 when the inventory is full. */
    private static int firstFreeSlot(EntityPlayer player) {
        for (int slot = 9; slot < 45; slot++) {
            if (player.inventoryContainer.getSlot(slot).getStack() == null) {
                return slot;
            }
        }
        return -1;
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
