package com.xeno.goop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xeno.goop.GoopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class CommandEvaluate implements Command<CommandSource> {
    private static final CommandEvaluate CMD = new CommandEvaluate();
    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("eval")
                .requires(cs -> cs.hasPermissionLevel(0))
                .executes(CMD);
    }

    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }
        GoopMod.mappingHandler.reloadMappings(Minecraft.getInstance().world);
        return 0;
    }
}
