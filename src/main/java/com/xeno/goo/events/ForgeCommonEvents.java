package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.commands.GooCommands;
import com.xeno.goo.entries.*;
import com.xeno.goo.entries.pushers.*;
import com.xeno.goo.entries.pushers.impl.*;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {
    // the server starting event unlocks the mapping loader for a one-time use
    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event)
    {
        GooCommands.register(event.getServer().getCommandManager().getDispatcher());
        serverStartEntries(event);
    }

    private static void serverStartEntries(FMLServerStartingEvent event)
    {
        ServerWorld world = event.getServer().getWorld(World.field_234918_g_);
        if (world == null) {
            return;
        }
        GooMod.mappingHandler = new EntryHandler();

        // register pushers
        // initialization routine, sets all items to unknown so their are no missing mappings.
        GooMod.mappingHandler.register(new UnknownPusher(world));
        // originating values for most things
        GooMod.mappingHandler.register(new BasePusher(world));
        // items we want to deny being solidified/reproduced for any reason
        GooMod.mappingHandler.register(new DenialPusher(world));
        // items that are the combination of a container and an arbitrary value
        // which cannot stand as a baseline alone (because containers are recipe-derived)
        GooMod.mappingHandler.register(new ContainerPusher(world));
        // items which don't have a recipe, or whose recipe equivalency we wish to override
        // by setting it as equal to another mapping, rather than an explicit value.
        GooMod.mappingHandler.register(new SimpleExchangePusher(world));
        // items which have a non-traditional route to obtaining value, usually
        // by reverse mapping an output to an input; we can specify "complex" equivalencies.
        GooMod.mappingHandler.register(new ComplexEquivalencyPusher(world));
        // a semi-automatic recipe-scraping algorithm which analyzes baseline values
        // and other recipes to derive the values of recipe outputs.
        GooMod.mappingHandler.register(new RecipePusher(world));
        // a recipe-scraping algorithm which denies any output mapping which contains
        // an input for which there is only a denied mapping and no alternative.
        GooMod.mappingHandler.register(new ExchangeDenialPusher(world));
        // pusher which sets any remaining unknown mappings to be denied instead.
        // also reports the denials to a debug logger so that the user or packer can review them.
        GooMod.mappingHandler.register(new FinalDenialPusher(world));

        GooMod.mappingHandler.reloadEntries(world, false, false);
    }
}
