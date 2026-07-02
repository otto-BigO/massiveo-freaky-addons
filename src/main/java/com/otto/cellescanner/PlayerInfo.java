package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Player Info addon: shift + right-click another player to open a menu showing
 * their equipped armor (with enchants), and later the info reachable through the
 * server's player commands. Purely client-side - the armor is read straight off
 * the target entity, no commands needed for that part.
 */
public class PlayerInfo {

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (!CelleScannerMod.config.playerInfoEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Only react to our own interactions, while sneaking (shift), on a player.
        if (event.entityPlayer != mc.thePlayer || !event.entityPlayer.isSneaking()) {
            return;
        }
        if (!(event.target instanceof EntityPlayer) || event.target == mc.thePlayer) {
            return;
        }
        event.setCanceled(true);
        mc.displayGuiScreen(new GuiPlayerInfo((EntityPlayer) event.target));
    }
}
