package com.otto.cellescanner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Random;

/**
 * Automation bot that stands in place, harvests fully grown crops (wheat, carrots,
 * potatoes, nether wart) within reach, and automatically replants them.
 */
public class FarmBot {

    private final Random rng = new Random();

    private BlockPos targetCrop = null;
    private BlockPos replantPos = null;
    private net.minecraft.item.Item seedItem = null;
    private int replantTicks = 0;
    private int delayTicks = 0;

    private float tYaw = 0.0F;
    private float tPitch = 0.0F;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (!CelleScannerMod.config.farmBotEnabled) {
            cleanup();
            return;
        }

        // Only run when no menu/GUI screen is open
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) {
            cleanup();
            return;
        }

        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // 1. Replanting sequence
        if (replantPos != null) {
            replantTicks--;
            
            // Aim at the soil block
            BlockPos soil = replantPos.down();
            aimAt(mc, soil);
            applyRotation(mc);

            if (replantTicks <= 0) {
                int seedSlot = findHotbarItem(mc, seedItem);
                if (seedSlot != -1) {
                    int oldSlot = mc.thePlayer.inventory.currentItem;
                    mc.thePlayer.inventory.currentItem = seedSlot;
                    
                    ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
                    
                    // Replant right-click
                    mc.playerController.onPlayerRightClick(
                            mc.thePlayer,
                            mc.theWorld,
                            stack,
                            soil,
                            EnumFacing.UP,
                            new Vec3(soil.getX() + 0.5, soil.getY() + 1.0, soil.getZ() + 0.5)
                    );
                    mc.thePlayer.swingItem();
                    
                    mc.thePlayer.inventory.currentItem = oldSlot;
                }
                
                replantPos = null;
                seedItem = null;
                delayTicks = 3 + rng.nextInt(3); // Small human delay
            }
            return;
        }

        // 2. Harvesting sequence
        if (targetCrop != null) {
            // Verify crop is still grown
            if (!isFullyGrownCrop(mc.theWorld, targetCrop)) {
                targetCrop = null;
                return;
            }

            aimAt(mc, targetCrop);
            applyRotation(mc);

            float diffYaw = Math.abs(MathHelper.wrapAngleTo180_float(tYaw - mc.thePlayer.rotationYaw));
            float diffPitch = Math.abs(tPitch - mc.thePlayer.rotationPitch);

            // Break if aimed close enough
            if (diffYaw < 8.0F && diffPitch < 8.0F) {
                IBlockState state = mc.theWorld.getBlockState(targetCrop);
                seedItem = getSeedItem(state.getBlock());

                mc.thePlayer.swingItem();
                mc.playerController.clickBlock(targetCrop, EnumFacing.UP);

                replantPos = targetCrop;
                replantTicks = 2 + rng.nextInt(2); // Wait 2-3 ticks to let block update clear
                targetCrop = null;
            }
            return;
        }

        // 3. Scan for new crop
        if (targetCrop == null && replantPos == null) {
            BlockPos crop = findBestCrop(mc);
            if (crop != null) {
                targetCrop = crop;
            }
        }
    }

    private void cleanup() {
        targetCrop = null;
        replantPos = null;
        seedItem = null;
        replantTicks = 0;
        delayTicks = 0;
    }

    private BlockPos findBestCrop(Minecraft mc) {
        BlockPos playerPos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        double bestDistSq = Double.MAX_VALUE;
        BlockPos bestPos = null;

        int r = 4;
        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos p = playerPos.add(x, y, z);
                    double distSq = mc.thePlayer.getDistanceSqToCenter(p);
                    
                    // Reach limit is 4.5 blocks
                    if (distSq <= 4.5 * 4.5) {
                        if (isFullyGrownCrop(mc.theWorld, p)) {
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestPos = p;
                            }
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private boolean isFullyGrownCrop(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.wheat) {
            return (Integer) state.getValue(BlockCrops.AGE) == 7;
        }
        if (block == Blocks.carrots) {
            return (Integer) state.getValue(BlockCrops.AGE) == 7;
        }
        if (block == Blocks.potatoes) {
            return (Integer) state.getValue(BlockCrops.AGE) == 7;
        }
        if (block == Blocks.nether_wart) {
            return (Integer) state.getValue(BlockNetherWart.AGE) == 3;
        }
        return false;
    }

    private net.minecraft.item.Item getSeedItem(Block block) {
        if (block == Blocks.wheat) return Items.wheat_seeds;
        if (block == Blocks.carrots) return Items.carrot;
        if (block == Blocks.potatoes) return Items.potato;
        if (block == Blocks.nether_wart) return Items.nether_wart;
        return null;
    }

    private int findHotbarItem(Minecraft mc, net.minecraft.item.Item item) {
        if (item == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    private void aimAt(Minecraft mc, BlockPos pos) {
        double dx = (pos.getX() + 0.5) - mc.thePlayer.posX;
        double dy = (pos.getY() + 0.3) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = (pos.getZ() + 0.5) - mc.thePlayer.posZ;
        double dh = Math.sqrt(dx * dx + dz * dz);
        tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        tPitch = (float) (-Math.toDegrees(Math.atan2(dy, dh)));
    }

    private void applyRotation(Minecraft mc) {
        float dy = MathHelper.wrapAngleTo180_float(tYaw - mc.thePlayer.rotationYaw);
        float dp = tPitch - mc.thePlayer.rotationPitch;
        
        float stepY = dy * (0.32F + rng.nextFloat() * 0.12F);
        float stepP = dp * (0.32F + rng.nextFloat() * 0.12F);
        float cap = 15.0F + rng.nextFloat() * 5.0F;

        stepY = MathHelper.clamp_float(stepY, -cap, cap);
        stepP = MathHelper.clamp_float(stepP, -cap, cap);

        mc.thePlayer.rotationYaw += stepY;
        mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + stepP, -90.0F, 90.0F);
    }
}
