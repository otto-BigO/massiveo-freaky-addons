package com.otto.cellescanner;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Hard licence gate (slim/public build). The mod's feature addons are NOT put on
 * the event bus at startup - they only get registered once the licence is verified
 * (via {@link CelleScannerMod#enableAddons()}). So an unlicensed client can still
 * play Minecraft normally, but none of the mod's features do anything until a valid
 * key is entered. This tick handler is always registered and flips them on as soon
 * as verification succeeds (from the startup re-check or the access-key screen).
 */
public class AccessGate {

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (AccessSystem.isVerified) {
            CelleScannerMod.enableAddons();
        }
    }
}
