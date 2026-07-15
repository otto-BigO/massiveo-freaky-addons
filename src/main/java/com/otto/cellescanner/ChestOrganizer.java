package com.otto.cellescanner;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;

/**
 * Intercepts chest left-clicks via MouseEvent (since PlayerInteractEvent doesn't fire
 * client-side for block hits in Forge 1.8.9) and renders 3D item icon frames.
 * Supports large chests, trapped chests, and ender chests.
 */
public class ChestOrganizer {

    @SubscribeEvent
    public void onMouseClick(MouseEvent event) {
        if (!CelleScannerMod.config.chestOrganizerEnabled) {
            return;
        }
        // Button 0 is left-click, buttonstate true means key pressed
        if (event.button != 0 || !event.buttonstate) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || !mc.inGameHasFocus) {
            return;
        }

        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos pos = mc.objectMouseOver.getBlockPos();
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block instanceof BlockChest || block instanceof BlockEnderChest) {
                // Cancel mouse event to prevent block hitting/swinging
                event.setCanceled(true);
                // Open Selection GUI
                mc.displayGuiScreen(new GuiChestOrganizer(pos));
            }
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!CelleScannerMod.config.chestOrganizerEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        int activeDim = mc.thePlayer.dimension;
        Map<String, String> configuredIcons = ChestOrganizerPositions.getAll();
        if (configuredIcons.isEmpty()) {
            return;
        }

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        for (Map.Entry<String, String> entry : configuredIcons.entrySet()) {
            String key = entry.getKey();
            int dim = ChestOrganizerPositions.parseDimension(key);
            if (dim != activeDim) {
                continue;
            }

            BlockPos pos = ChestOrganizerPositions.parseBlockPos(key);
            if (pos == null) {
                continue;
            }

            // Check if chunk is loaded to prevent lag/rendering errors
            if (!mc.theWorld.isBlockLoaded(pos)) {
                continue;
            }

            IBlockState state = mc.theWorld.getBlockState(pos);
            Block block = state.getBlock();
            if (!(block instanceof BlockChest) && !(block instanceof BlockEnderChest)) {
                continue; // Chest was probably broken or replaced
            }

            String value = entry.getValue();
            String registryName = value;
            String placement = "FRONT";
            if (value.contains(";")) {
                String[] parts = value.split(";");
                registryName = parts[0];
                placement = parts[1];
            }

            Item item = Item.getByNameOrId(registryName);
            if (item == null) {
                continue;
            }

            ItemStack stack = new ItemStack(item);
            EnumFacing facing = getFacing(state);

            double renderX = pos.getX() - viewerX;
            double renderY = pos.getY() - viewerY;
            double renderZ = pos.getZ() - viewerZ;

            // Determine offsets to center the item directly onto the chest face
            double offsetX = 0.5D;
            double offsetY = 0.5D; // default vertical center for sides
            double offsetZ = 0.5D;
            float yaw = 0.0F;
            float pitch = 0.0F;

            // Offset slightly forward based on facing direction to prevent z-fighting
            double faceOffset = 0.515D;

            EnumFacing renderFace = facing;
            if ("BACK".equalsIgnoreCase(placement)) {
                renderFace = facing.getOpposite();
            } else if ("RIGHT".equalsIgnoreCase(placement)) {
                renderFace = facing.rotateY();
            } else if ("LEFT".equalsIgnoreCase(placement)) {
                renderFace = facing.rotateYCCW();
            } else if ("TOP".equalsIgnoreCase(placement)) {
                renderFace = EnumFacing.UP;
            } else if ("BOTTOM".equalsIgnoreCase(placement)) {
                renderFace = EnumFacing.DOWN;
            }

            if (renderFace == EnumFacing.NORTH) {
                offsetZ -= faceOffset;
                yaw = 180.0F;
                if ("FRONT".equalsIgnoreCase(placement)) {
                    offsetY = 0.35D; // Align with latch front lower split
                }
            } else if (renderFace == EnumFacing.SOUTH) {
                offsetZ += faceOffset;
                yaw = 0.0F;
                if ("FRONT".equalsIgnoreCase(placement)) {
                    offsetY = 0.35D;
                }
            } else if (renderFace == EnumFacing.WEST) {
                offsetX -= faceOffset;
                yaw = 90.0F;
                if ("FRONT".equalsIgnoreCase(placement)) {
                    offsetY = 0.35D;
                }
            } else if (renderFace == EnumFacing.EAST) {
                offsetX += faceOffset;
                yaw = 270.0F;
                if ("FRONT".equalsIgnoreCase(placement)) {
                    offsetY = 0.35D;
                }
            } else if (renderFace == EnumFacing.UP) {
                offsetY = 1.015D;
                pitch = 90.0F;
                // Match rotation with chest front facing direction
                if (facing == EnumFacing.NORTH) yaw = 180.0F;
                else if (facing == EnumFacing.SOUTH) yaw = 0.0F;
                else if (facing == EnumFacing.WEST) yaw = 90.0F;
                else if (facing == EnumFacing.EAST) yaw = 270.0F;
            } else if (renderFace == EnumFacing.DOWN) {
                offsetY = -0.015D;
                pitch = -90.0F;
                if (facing == EnumFacing.NORTH) yaw = 180.0F;
                else if (facing == EnumFacing.SOUTH) yaw = 0.0F;
                else if (facing == EnumFacing.WEST) yaw = 90.0F;
                else if (facing == EnumFacing.EAST) yaw = 270.0F;
            }

            GlStateManager.pushMatrix();
            GlStateManager.translate(renderX + offsetX, renderY + offsetY, renderZ + offsetZ);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            if (pitch != 0.0F) {
                GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            }
            GlStateManager.scale(0.55F, 0.55F, 0.55F);

            // Enable lighting for standard item look
            GlStateManager.enableLighting();
            RenderHelper.enableStandardItemLighting();

            // Render the item in 3D
            mc.getRenderItem().renderItem(stack, ItemCameraTransforms.TransformType.FIXED);

            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
        }
    }

    private EnumFacing getFacing(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockChest) {
            return (EnumFacing) state.getValue(BlockChest.FACING);
        } else if (block instanceof BlockEnderChest) {
            return (EnumFacing) state.getValue(BlockEnderChest.FACING);
        }
        return EnumFacing.NORTH;
    }
}
