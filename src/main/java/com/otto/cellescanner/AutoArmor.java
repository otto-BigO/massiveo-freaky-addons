package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Container;
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
 * Puts armour on, or takes it off, in one keypress.
 *
 * It opens the real inventory screen, clicks through the pieces with a small
 * random gap between them, and closes it again, which is the same sequence a
 * person produces. Every click is a shift-click: the container already knows
 * where an armour piece belongs, so there is never an item held on the cursor
 * and never a half-finished swap to clean up.
 */
public class AutoArmor {

    public static final AutoArmor INSTANCE = new AutoArmor();
    private static final Random RANDOM = new Random();

    private final List<ArmorTask> taskQueue = new ArrayList<ArmorTask>();
    private long nextActionTime = 0;
    private int totalTasksExecuted = 0;
    private boolean isEquipOperation = true;
    private boolean isExecuting = false;
    private long closeAfter = 0;

    /* Equipping happens in two phases, because the two halves cannot share a
       screen: the hotbar swaps need the inventory open, and using an item needs
       it shut. So the swaps run first with the inventory up, it closes, and
       then these get held and right clicked one at a time. */
    private final List<Integer> pendingUse = new ArrayList<Integer>();
    private long nextUseTime = 0;
    private int heldBeforeEquip = -1;

    /**
     * One container click: which slot, and whether it is a plain click or a
     * shift-click.
     *
     * Everything is a shift-click now, both putting on and taking off. See the
     * note in startToggle for why the pick-up-and-put-down pair went.
     */
    private static class ArmorTask {
        /** Container slot the piece is in. */
        final int slot;
        /** Window click mode. 1 is shift-click, 2 is the number-key swap. */
        final int mode;
        /** For mode 2, which hotbar index to swap into. */
        final int button;

        ArmorTask(int slot, int mode) {
            this(slot, mode, 0);
        }

        ArmorTask(int slot, int mode, int button) {
            this.slot = slot;
            this.mode = mode;
            this.button = button;
        }
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
        pendingUse.clear();
        heldBeforeEquip = -1;
        totalTasksExecuted = 0;
        isEquipOperation = !hasEquippedArmor;

        if (hasEquippedArmor) {
            // Shift-click. The container moves it straight into the inventory.
            for (int slot = 5; slot <= 8; slot++) {
                ItemStack stack = player.inventoryContainer.getSlot(slot).getStack();
                if (stack != null) {
                    taskQueue.add(new ArmorTask(slot, 1));
                }
            }
        } else {
            /* Putting armour on is NOT done with a container click on this
               server, and that is the whole story of why this addon never
               worked.

               FreakyVille refuses armour equips made by clicking in the
               inventory. Confirmed by hand, not guessed: shift-clicking a piece
               yourself puts it on for a moment and then the server sends the
               inventory back as it was. Every version of this addon so far was
               faithfully sending a click the server was always going to undo,
               which is why the piece kept reappearing where it started, and
               earlier, with a pick-up and put-down pair, why it was left on the
               cursor.

               So it equips the way that does work: hold the piece and use it.
               ItemArmor.onItemRightClick swaps the piece onto the body, and it
               arrives as a use-item packet rather than a window click, which is
               a different thing entirely as far as the server is concerned.

               That needs the piece in the hand, so each one becomes: swap it
               into a hotbar slot, hold that slot, right click. The held slot is
               put back afterwards. */
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
                if (bestSlot >= 36 && bestSlot <= 44) {
                    // Already in the hotbar. Nothing to move, just use it later.
                    pendingUse.add(Integer.valueOf(bestSlot - 36));
                } else {
                    int hotbar = firstEmptyHotbarIndex(player, pendingUse);
                    if (hotbar == -1) {
                        continue;   // nowhere to put it without displacing something
                    }
                    // Mode 2 is the number-key swap.
                    taskQueue.add(new ArmorTask(bestSlot, 2, hotbar));
                    pendingUse.add(Integer.valueOf(hotbar));
                }
            }
        }

        if (!taskQueue.isEmpty() || !pendingUse.isEmpty()) {
            isExecuting = true;
            heldBeforeEquip = mc.thePlayer.inventory.currentItem;
            nextUseTime = System.currentTimeMillis() + 150;

            if (!taskQueue.isEmpty()) {
                // The swaps need the inventory open. If every piece was already
                // in the hotbar there is nothing to swap and no reason to open
                // anything: it goes straight to phase two.
                mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
                nextActionTime = System.currentTimeMillis() + (120 + RANDOM.nextInt(80));
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isExecuting) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) {
            taskQueue.clear();
            pendingUse.clear();
            isExecuting = false;
            return;
        }

        // Phase two: the inventory is shut, the pieces are in the hotbar, and
        // each one gets held and used. Runs before the screen checks below,
        // because this half specifically needs no screen open.
        if (taskQueue.isEmpty() && closeAfter == 0 && !pendingUse.isEmpty()) {
            if (mc.currentScreen != null) {
                return;   // wait for it to actually be gone
            }
            long t = System.currentTimeMillis();
            if (t < nextUseTime) {
                return;
            }
            int hotbar = pendingUse.remove(0).intValue();
            ItemStack held = mc.thePlayer.inventory.getStackInSlot(hotbar);
            if (held != null && held.getItem() instanceof ItemArmor) {
                mc.thePlayer.inventory.currentItem = hotbar;
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
                totalTasksExecuted++;
            }
            nextUseTime = t + (140 + RANDOM.nextInt(90));
            if (pendingUse.isEmpty()) {
                // Put the hand back the way it was found.
                if (heldBeforeEquip >= 0 && heldBeforeEquip < 9) {
                    mc.thePlayer.inventory.currentItem = heldBeforeEquip;
                }
                heldBeforeEquip = -1;
                isExecuting = false;
                report(mc);
            }
            return;
        }

        /* Stop if the inventory is no longer what is open. Two separate checks,
           and the first one is why this used to do nothing at all:

           GuiContainer, not GuiInventory. This runs under LabyMod, which does
           not necessarily hand back a literal GuiInventory when one is opened,
           and testing for the exact class meant the run was abandoned on its
           first tick. Every inventory screen is a GuiContainer.

           And the container has to actually be the player's own, because slot
           numbers only mean what this code thinks they mean in that one
           container. If a chest or a shop is open, the same numbers point at
           somebody else's slots.

           Out through finishOperation rather than dropping the queue, so
           anything still on the cursor goes back in the inventory. */
        if (!(mc.currentScreen instanceof GuiContainer)
                || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer) {
            finishOperation(mc);
            return;
        }

        long now = System.currentTimeMillis();

        // The window closes a beat after the final click, not with it.
        if (closeAfter > 0 && now >= closeAfter) {
            closeAfter = 0;
            finishOperation(mc);
            return;
        }
        if (closeAfter > 0) return;

        if (now < nextActionTime) return;

        if (!taskQueue.isEmpty()) {
            ArmorTask task = taskQueue.remove(0);
            executeTask(mc, task);
            totalTasksExecuted++;

            if (taskQueue.isEmpty()) {
                /* Do NOT close the window in the same tick as the last click.
                   closeScreen sends C0DPacketCloseWindow, and sending that
                   immediately behind C0EPacketClickWindow gives the server a
                   close to process against a click it may not have finished
                   with. A rejected click is answered with a full inventory
                   resync, which is exactly what "it goes on and then comes
                   back" looks like from here. Let the click land first. */
                closeAfter = now + 250;
            } else {
                // Humanized delay between clicks (100ms - 180ms)
                nextActionTime = now + (100 + RANDOM.nextInt(80));
            }
        } else {
            finishOperation(mc);
        }
    }

    private void finishOperation(Minecraft mc) {
        closeAfter = 0;
        taskQueue.clear();
        // Phase two carries on after the screen is gone, so the run is only
        // over when there is nothing left to put on either.
        isExecuting = !pendingUse.isEmpty();

        // Anything still on the cursor has to go back in the inventory. Slot
        // -999 would throw it on the floor instead, which is the one outcome
        // worth going out of the way to avoid.
        if (mc.thePlayer != null && mc.thePlayer.inventory != null
                && mc.thePlayer.inventory.getItemStack() != null) {
            int free = firstFreeSlot(mc.thePlayer);
            if (free != -1) {
                Container container = mc.thePlayer.openContainer != null
                        ? mc.thePlayer.openContainer : mc.thePlayer.inventoryContainer;
                mc.playerController.windowClick(container.windowId, free, 0, 0, mc.thePlayer);
            }
        }

        if (mc.currentScreen instanceof GuiInventory) {
            mc.thePlayer.closeScreen();
        }

        if (!isExecuting) {
            report(mc);
        }
    }

    private void report(Minecraft mc) {
        if (mc.thePlayer != null && totalTasksExecuted > 0) {
            String actionStr = isEquipOperation ? "Iførte" : "Tog";
            mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                    EnumChatFormatting.GREEN + "[Auto Armour] " + actionStr + " " + totalTasksExecuted + " rustningsdele."));
        }
        totalTasksExecuted = 0;
    }

    private void executeTask(Minecraft mc, ArmorTask task) {
        Container container = mc.thePlayer.openContainer != null
                ? mc.thePlayer.openContainer : mc.thePlayer.inventoryContainer;

        mc.playerController.windowClick(container.windowId, task.slot, task.button, task.mode, mc.thePlayer);
    }

    /**
     * An empty hotbar slot as an index 0 to 8, or -1 when the hotbar is full.
     *
     * Skips any index already promised to another piece in this same run, or
     * two pieces would be swapped into the same slot and the second would
     * knock the first straight back out.
     */
    private static int firstEmptyHotbarIndex(EntityPlayer player, List<Integer> taken) {
        for (int i = 0; i < 9; i++) {
            if (player.inventory.getStackInSlot(i) == null
                    && !taken.contains(Integer.valueOf(i))) {
                return i;
            }
        }
        return -1;
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
