package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

/**
 * Opens the Massiveo's Freaky Addons hub (GuiAddonsHub) when the configured
 * keybind is pressed, so the player never has to type a chat command at all.
 */
public class KeyHandler {

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }

        if (CelleScannerMod.openMenuKey.isPressed()) {
            mc.displayGuiScreen(new GuiAddonsHub());
        }
        if (CelleScannerMod.autoMineKey != null && CelleScannerMod.autoMineKey.isPressed()) {
            CelleActions.toggleAutoMine();
        }
        if (CelleScannerMod.phoneKey != null && CelleScannerMod.phoneKey.isPressed()) {
            CelleActions.openPhoneGui();
        }
    }
}
