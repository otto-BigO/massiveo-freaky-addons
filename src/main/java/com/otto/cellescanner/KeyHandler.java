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

        if (MassiveOsFreakyAddons.openMenuKey.isPressed()) {
            mc.displayGuiScreen(new GuiAddonsHub());
        }
        if (MassiveOsFreakyAddons.autoMineKey != null && MassiveOsFreakyAddons.autoMineKey.isPressed()) {
            CelleActions.toggleAutoMine();
        }
        if (MassiveOsFreakyAddons.armorKey != null && MassiveOsFreakyAddons.armorKey.isPressed()) {
            if (MassiveOsFreakyAddons.config != null && MassiveOsFreakyAddons.config.autoArmorEnabled) {
                AutoArmor.toggleArmor();
            }
        }
        if (MassiveOsFreakyAddons.debugOverlayKey != null && MassiveOsFreakyAddons.debugOverlayKey.isPressed()) {
            if (MassiveOsFreakyAddons.config != null) {
                MassiveOsFreakyAddons.config.debugOverlayEnabled = !MassiveOsFreakyAddons.config.debugOverlayEnabled;
                MassiveOsFreakyAddons.config.save();
                if (mc.thePlayer != null) {
                    mc.thePlayer.addChatMessage(new net.minecraft.util.ChatComponentText(
                            net.minecraft.util.EnumChatFormatting.AQUA + "[Debug Overlay] " + (MassiveOsFreakyAddons.config.debugOverlayEnabled ? "TIL" : "FRA")));
                }
            }
        }
        if (MassiveOsFreakyAddons.phoneKey != null && MassiveOsFreakyAddons.phoneKey.isPressed()) {
            CelleActions.openPhoneGui();
        }
        // if (MassiveOsFreakyAddons.majesticaKey != null && MassiveOsFreakyAddons.majesticaKey.isPressed()) {
        //     mc.displayGuiScreen(new GuiWeaponSelector());
        // }
        // if (MassiveOsFreakyAddons.freecamKey != null && MassiveOsFreakyAddons.freecamKey.isPressed()) {
        //     Freecam.INSTANCE.toggle();
        // }
    }
}
