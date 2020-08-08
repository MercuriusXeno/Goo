package com.xeno.goo.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.xeno.goo.GooMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class GoopCommands
{

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> cmdGoo = dispatcher.register(
                Commands.literal(GooMod.MOD_ID)
                    .then(CommandRegenerateMappings.register(dispatcher))
                    .then(CommandRestoreDefaultMappings.register(dispatcher))
        );

        // dispatcher.register(Commands.literal("goo").redirect(cmdGoo));
    }
}
