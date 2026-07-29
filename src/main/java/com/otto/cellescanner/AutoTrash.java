package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * Auto Trash / Skralde-Filter Addon.
 * Automatically drops unwanted junk items (Cobblestone, Dirt, Gravel, Sand,
 * Wood & Stone tools)
 * from your inventory so your slots stay clean for Iron Ore and DBs.
 */
public class AutoTrash {

    public static final AutoTrash INSTANCE = new AutoTrash();
    private static final Random RANDOM = new Random();
    private long nextDropTime = 0;

    private AutoTrash() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START)
            return;

        CelleConfig cfg = CelleScannerMod.config;
        if (cfg == null || !cfg.autoTrashEnabled)
            return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null || mc.currentScreen != null)
            return;

        long now = System.currentTimeMillis();
        if (now < nextDropTime)
            return;

        // Scan inventory slots 9..44 for trash items
        for (int slot = 9; slot < 45; slot++) {
            ItemStack stack = mc.thePlayer.inventoryContainer.getSlot(slot).getStack();
            if (stack != null && isTrashItem(stack, cfg)) {
                // Drop the entire stack (mode 4, button 1)
                mc.playerController.windowClick(0, slot, 1, 4, mc.thePlayer);
                nextDropTime = now + 180 + RANDOM.nextInt(120);
                return;
            }
        }
    }

    private boolean isTrashItem(ItemStack stack, CelleConfig cfg) {
        if (stack == null || stack.getItem() == null)
            return false;
        Item item = stack.getItem();

        // Check junk blocks
        if (cfg.trashBlocks) {
            Item cobble = Item.getItemFromBlock(Blocks.cobblestone);
            Item dirt = Item.getItemFromBlock(Blocks.dirt);
            Item gravel = Item.getItemFromBlock(Blocks.gravel);
            Item sand = Item.getItemFromBlock(Blocks.sand);
            Item cobbleWall = Item.getItemFromBlock(Blocks.cobblestone_wall);

            if (item == cobble || item == dirt || item == gravel || item == sand || item == cobbleWall
                    || item == Items.flint || item == Items.rotten_flesh || item == Items.poisonous_potato) {
                return true;
            }
        }

        // Check wooden tools
        if (cfg.trashWoodTools) {
            if (item == Items.wooden_pickaxe || item == Items.wooden_axe || item == Items.wooden_shovel
                    || item == Items.wooden_sword || item == Items.wooden_hoe) {
                return true;
            }
        }

        // Check stone tools
        if (cfg.trashStoneTools) {
            if (item == Items.stone_pickaxe || item == Items.stone_axe || item == Items.stone_shovel
                    || item == Items.stone_sword || item == Items.stone_hoe) {
                return true;
            }
        }

        return false;
    }
}
