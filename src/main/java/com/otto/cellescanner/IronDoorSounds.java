package com.otto.cellescanner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IWorldAccess;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Listens to client-side world block updates to detect open/close transitions
 * of iron doors and play spatial open/close door sounds on the client.
 */
public class IronDoorSounds implements IWorldAccess {

    private final Map<BlockPos, Boolean> doorStates = new HashMap<BlockPos, Boolean>();
    private final Random rand = new Random();

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world.isRemote) {
            event.world.addWorldAccess(this);
            doorStates.clear();
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.world.isRemote) {
            doorStates.clear();
        }
    }

    @Override
    public void markBlockForUpdate(BlockPos pos) {
        if (!CelleScannerMod.config.ironDoorSoundsEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }

        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (block == Blocks.iron_door) {
            BlockDoor.EnumDoorHalf half = (BlockDoor.EnumDoorHalf) state.getValue(BlockDoor.HALF);
            BlockPos lowerPos = (half == BlockDoor.EnumDoorHalf.UPPER) ? pos.down() : pos;

            IBlockState lowerState = mc.theWorld.getBlockState(lowerPos);
            if (lowerState.getBlock() == Blocks.iron_door) {
                boolean isOpen = (boolean) lowerState.getValue(BlockDoor.OPEN);
                Boolean wasOpen = doorStates.get(lowerPos);

                if (wasOpen == null) {
                    // Store the initial observed state without playing any sound
                    doorStates.put(lowerPos, isOpen);
                } else if (wasOpen != isOpen) {
                    doorStates.put(lowerPos, isOpen);

                    // Limit cache size to avoid memory leaks
                    if (doorStates.size() > 500) {
                        pruneCache(mc.thePlayer);
                    }

                    // Play spatial door sound on the client world
                    double x = lowerPos.getX() + 0.5D;
                    double y = lowerPos.getY() + 0.5D;
                    double z = lowerPos.getZ() + 0.5D;
                    float pitch = 0.9F + rand.nextFloat() * 0.15F;
                    String soundName = isOpen ? "random.door_open" : "random.door_close";

                    mc.theWorld.playSound(x, y, z, soundName, 1.0F, pitch, false);
                }
            }
        }
    }

    private void pruneCache(EntityPlayer player) {
        if (player == null) {
            doorStates.clear();
            return;
        }
        double maxDistSq = 64.0D * 64.0D;
        java.util.Iterator<Map.Entry<BlockPos, Boolean>> it = doorStates.entrySet().iterator();
        while (it.hasNext()) {
            BlockPos p = it.next().getKey();
            if (player.getDistanceSq(p) > maxDistSq) {
                it.remove();
            }
        }
    }

    @Override
    public void notifyLightSet(BlockPos pos) {}

    @Override
    public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}

    @Override
    public void playSound(String soundName, double x, double y, double z, float volume, float pitch) {}

    @Override
    public void playSoundToNearExcept(EntityPlayer player, String soundName, double x, double y, double z, float volume, float pitch) {}

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters) {}

    @Override
    public void onEntityAdded(Entity entityIn) {}

    @Override
    public void onEntityRemoved(Entity entityIn) {}

    @Override
    public void playRecord(String recordName, BlockPos pos) {}

    @Override
    public void playAuxSFX(EntityPlayer player, int sfxType, BlockPos blockPosIn, int p_180440_4_) {}

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {}

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {}
}
