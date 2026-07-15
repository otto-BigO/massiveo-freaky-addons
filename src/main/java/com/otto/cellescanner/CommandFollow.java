package com.otto.cellescanner;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import java.util.Arrays;
import java.util.List;

/**
 * Command: /følg <player|stop>
 * Aliased to: /follow <player|stop>
 * Starts auto-following a player, keeping the bot roughly 1 block to their side.
 */
public class CommandFollow extends CommandBase {

    @Override
    public String getCommandName() {
        return "følg";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("follow");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/følg <spillernavn|stop>";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            if (AutoFollow.isActive()) {
                sender.addChatMessage(new ChatComponentText("§eAuto-Følg er i gang. Fælger: " + AutoFollow.getTargetName()));
            } else {
                sender.addChatMessage(new ChatComponentText("§eAuto-Følg er ikke aktiv. Brug: /følg <navn>"));
            }
            return;
        }

        String target = args[0];
        if (target.equalsIgnoreCase("stop")) {
            AutoFollow.stop();
        } else {
            AutoFollow.start(target);
        }
    }
}
