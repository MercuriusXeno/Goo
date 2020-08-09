package com.xeno.goo.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.xeno.goo.GooMod;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class GooCommands
{
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal(GooMod.MOD_ID).then(CommandRegenerateEntries.register(dispatcher)).then(CommandRestoreDefaultEntries.register(dispatcher)));
    }
}
