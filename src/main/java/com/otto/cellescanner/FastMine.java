package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Fast Mine addon: When enabled and the player is holding the attack key (left-click) to mine a block,
 * the mod damages the block simultaneously so the player's mouse and the mod mine at the same time.
 */
public class FastMine {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        CelleConfig config = CelleScannerMod.config;
        if (config == null || !config.fastMineEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) {
            return;
        }

        // Check if the player is holding down the mining/attack key
        if (mc.gameSettings.keyBindAttack.isKeyDown()) {
            MovingObjectPosition mop = mc.objectMouseOver;
            if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                BlockPos targetPos = mop.getBlockPos();
                EnumFacing side = mop.sideHit;
                if (targetPos != null && side != null && !mc.theWorld.isAirBlock(targetPos)) {
                    mc.playerController.onPlayerDamageBlock(targetPos, side);
                    mc.thePlayer.swingItem();
                }
            }
        }
    }
}
