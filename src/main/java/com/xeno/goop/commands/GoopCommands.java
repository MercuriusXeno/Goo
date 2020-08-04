package com.xeno.goop.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.xeno.goop.GoopMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class GoopCommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> cmdGoop = dispatcher.register(
                Commands.literal(GoopMod.MOD_ID)
                        .then(CommandEvaluate.register(dispatcher))

        );

        dispatcher.register(Commands.literal("goop").redirect(cmdGoop));
    }
}
