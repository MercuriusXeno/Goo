package com.xeno.goop.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.xeno.goop.GoopMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> cmdGoop = dispatcher.register(
                Commands.literal(GoopMod.MOD_ID)
                        .then(CommandBulb.register(dispatcher))
                        .then(CommandGoopifier.register(dispatcher))
                        .then(CommandSolidifier.register(dispatcher))
        );

        dispatcher.register(Commands.literal("goop").redirect(cmdGoop));
    }
}
