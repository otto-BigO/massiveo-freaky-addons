package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

/**
 * Auto-Crate Opener bot that left-clicks crate blocks when holding keys (tripwire hooks) and loops automatically.
 */
public class AutoCrate {

    private static Method rightClickMethod = null;
    static {
        try {
            rightClickMethod = Minecraft.class.getDeclaredMethod("rightClickMouse");
            rightClickMethod.setAccessible(true);
        } catch (Throwable t1) {
            try {
                rightClickMethod = Minecraft.class.getDeclaredMethod("func_147121_ag");
                rightClickMethod.setAccessible(true);
            } catch (Throwable t2) {
                System.err.println("[CelleScanner] AutoCrate reflection failed to find rightClickMouse method: " + t2);
            }
        }
    }

    private int tickCooldown = 0;
    private boolean guiWasOpen = false;

    private void rightClick() {
        try {
            if (rightClickMethod != null) {
                rightClickMethod.invoke(Minecraft.getMinecraft());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (!CelleScannerMod.config.autoCrateEnabled) {
            guiWasOpen = false;
            tickCooldown = 0;
            return;
        }

        // If a chest GUI is currently open, we are watching the opening animation.
        if (mc.currentScreen instanceof GuiChest) {
            guiWasOpen = true;
            tickCooldown = 15; // Set 15-tick (750ms) cooldown to run after the GUI closes.
            return;
        }

        // GUI is closed. Handle transition from open to closed.
        if (guiWasOpen && mc.currentScreen == null) {
            guiWasOpen = false;
        }

        // Handle active click cooldown
        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        // Cooldown has expired. Make sure we are holding a music disc (key).
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof net.minecraft.item.ItemRecord)) {
            // Scan hotbar to find next key
            int keySlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && stack.getItem() instanceof net.minecraft.item.ItemRecord) {
                    keySlot = i;
                    break;
                }
            }

            if (keySlot != -1) {
                // Auto switch held item to the key
                mc.thePlayer.inventory.currentItem = keySlot;
                tickCooldown = 5; // wait 250ms for client sync
                return;
            } else {
                // No keys left! Disable Auto-Crate and notify player
                CelleScannerMod.config.autoCrateEnabled = false;
                CelleScannerMod.config.save();
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        net.minecraft.util.EnumChatFormatting.RED + "[Massiveo's addons] Alle kasser åbnet / ingen nøgler fundet! Auto-Crate deaktiveret."
                ));
                return;
            }
        }

        // Verify player is looking at a block to click on
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            rightClick();
            // Fallback click timer: if the GUI doesn't open within 30 ticks (1.5s) due to a lag/miss, click again.
            tickCooldown = 30;
        }
    }
}
