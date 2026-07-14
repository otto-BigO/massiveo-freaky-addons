package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

/**
 * Auto-Fish AFK bot that listens to splash sounds near the bobber to automatically reel in and recast.
 */
public class AutoFish {

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
                System.err.println("[CelleScanner] AutoFish reflection failed to find rightClickMouse method: " + t2);
            }
        }
    }

    private long lastRightClickTime = 0;
    private long bobberSpawnTime = 0;
    private boolean hadBobberLastTick = false;
    private int castDelayTicks = -1;

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

        if (!CelleScannerMod.config.autoFishEnabled) {
            hadBobberLastTick = false;
            castDelayTicks = -1;
            return;
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || held.getItem() != Items.fishing_rod) {
            int rodSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
                if (stack != null && stack.getItem() == Items.fishing_rod) {
                    rodSlot = i;
                    break;
                }
            }

            if (rodSlot != -1) {
                mc.thePlayer.inventory.currentItem = rodSlot;
            } else {
                CelleScannerMod.config.autoFishEnabled = false;
                CelleScannerMod.config.save();
                mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                        net.minecraft.util.EnumChatFormatting.RED + "[Massiveo's addons] Du har ingen fiskestang i dit hotbar! Auto-Fish deaktiveret."
                ));
                hadBobberLastTick = false;
                castDelayTicks = -1;
                return;
            }
        }

        EntityFishHook bobber = mc.thePlayer.fishEntity;
        boolean hasBobber = (bobber != null);

        // Detect when bobber spawns to record time
        if (hasBobber && !hadBobberLastTick) {
            bobberSpawnTime = System.currentTimeMillis();
        }
        hadBobberLastTick = hasBobber;

        long now = System.currentTimeMillis();

        if (!hasBobber) {
            // Not currently fishing. If we're waiting on a cast delay (reeling animation finish), count down.
            if (castDelayTicks > 0) {
                castDelayTicks--;
                return;
            }

            if (castDelayTicks == 0) {
                // Cast the rod!
                rightClick();
                lastRightClickTime = now;
                castDelayTicks = -1;
            } else if (now - lastRightClickTime > 2000L) {
                // Fallback cast if we got stuck
                rightClick();
                lastRightClickTime = now;
            }
        } else {
            // Bobber is out. Check for timeout (stuck on block / missed splash)
            if (now - bobberSpawnTime > 40000L) { // 40 seconds max
                rightClick(); // reel in
                lastRightClickTime = now;
                castDelayTicks = 20; // 1 second recast delay
            }
        }
    }

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        if (!CelleScannerMod.config.autoFishEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || event.name == null || event.sound == null) {
            return;
        }

        if ("random.splash".equals(event.name)) {
            EntityFishHook bobber = mc.thePlayer.fishEntity;
            if (bobber != null) {
                double dx = event.sound.getXPosF() - bobber.posX;
                double dy = event.sound.getYPosF() - bobber.posY;
                double dz = event.sound.getZPosF() - bobber.posZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                if (dist < 2.0) {
                    // Bite detected! Reel in immediately
                    rightClick();
                    lastRightClickTime = System.currentTimeMillis();
                    castDelayTicks = 20; // Wait 20 ticks (1 second) before recasting
                }
            }
        }
    }
}
