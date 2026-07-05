package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Debug tool (gear -> Debug). When the "Flip!" chest GUI is open it dumps every
 * non-empty slot's item, display name and lore to chat, and re-logs slots as they
 * change - so we can see how the flip reveal animates and where the players' names
 * and the winner marker live. Throwaway; used to build the flip case-opening addon.
 */
public class FlipDebug {

    private boolean inFlip = false;
    private String[] prev = null;
    private int tick = 0;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (CelleScannerMod.config.debugEnabled == null || !CelleScannerMod.config.debugEnabled) {
            inFlip = false;
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        IInventory inv = flipChest(mc);
        if (inv == null) {
            inFlip = false;
            prev = null;
            return;
        }

        if (!inFlip) {
            inFlip = true;
            prev = new String[inv.getSizeInventory()];
            msg(mc, "§6=== Flip! GUI åbnet (" + inv.getSizeInventory() + " slots) ===");
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                String d = describe(inv.getStackInSlot(i));
                prev[i] = d;
                if (d != null) {
                    msg(mc, "§7[" + i + "] §f" + d);
                }
            }
            return;
        }

        // Re-check for changes a few times a second, log only the slots that changed.
        tick++;
        if (tick % 4 != 0) {
            return;
        }
        int n = inv.getSizeInventory();
        if (prev == null || prev.length != n) {
            prev = new String[n];
        }
        for (int i = 0; i < n; i++) {
            String d = describe(inv.getStackInSlot(i));
            if (!eq(prev[i], d)) {
                msg(mc, "§e[" + i + "] §7" + str(prev[i]) + " §8-> §f" + str(d));
                prev[i] = d;
            }
        }
    }

    /** The lower chest inventory if the open screen is a "Flip!" chest, else null. */
    private IInventory flipChest(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiChest)) {
            return null;
        }
        Container c = ((GuiChest) mc.currentScreen).inventorySlots;
        if (!(c instanceof ContainerChest)) {
            return null;
        }
        IInventory inv = ((ContainerChest) c).getLowerChestInventory();
        String title = inv.getDisplayName() != null ? inv.getDisplayName().getUnformattedText() : "";
        return title != null && title.contains("Flip") ? inv : null;
    }

    /** "item meta=M x count name='DISPLAY' lore=[...]" or null for empty. */
    private String describe(ItemStack s) {
        if (s == null) {
            return null;
        }
        StringBuilder b = new StringBuilder();
        Object id = Item.itemRegistry.getNameForObject(s.getItem());
        b.append(id).append(" meta=").append(s.getMetadata()).append(" x").append(s.stackSize);
        if (s.hasDisplayName()) {
            b.append(" name='").append(s.getDisplayName()).append("'");
        }
        if (s.hasTagCompound() && s.getTagCompound().hasKey("display")) {
            NBTTagCompound disp = s.getTagCompound().getCompoundTag("display");
            if (disp.hasKey("Lore")) {
                NBTTagList lore = disp.getTagList("Lore", 8);
                b.append(" lore=[");
                for (int i = 0; i < lore.tagCount(); i++) {
                    if (i > 0) {
                        b.append(" | ");
                    }
                    b.append(lore.getStringTagAt(i));
                }
                b.append("]");
            }
        }
        return b.toString();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String str(String s) {
        return s == null ? "(tom)" : s;
    }

    private void msg(Minecraft mc, String text) {
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }
}
