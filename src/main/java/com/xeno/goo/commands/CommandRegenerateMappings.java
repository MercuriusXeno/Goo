package com.xeno.goo.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.xeno.goo.GooMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

class CommandRegenerateEntries implements Command<CommandSource>
{

    private static final CommandRegenerateEntries CMD = new CommandRegenerateEntries();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("regenerate")
                .requires(cs -> cs.hasPermissionLevel(0))
                .executes(CMD);
    }
    @Override
    public int run(CommandContext<CommandSource> context) throws CommandSyntaxException
    {
        ServerWorld world = context.getSource().getServer().getWorld(World.field_234918_g_);
        if (world == null) {
            return 0;
        }
        GooMod.handler.reloadEntries(world, false, true);
        return 0;
    }
}
