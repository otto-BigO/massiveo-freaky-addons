package com.otto.cellescanner;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;

public class CommandClearLogouts extends CommandBase {

    @Override
    public String getCommandName() {
        return "clearlogouts";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/clearlogouts";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        PlayerLogger.clearLogouts();
        CelleActions.message("Slettede alle logout-markører.");
    }
}
