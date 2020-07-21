package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.commands.GoopCommands;
import com.xeno.goop.network.Networking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModSetup {
    public static final ItemGroup ITEM_GROUP = new ItemGroup("goop")
    {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Registration.SOLIDIFIER.get());
        }
    };

    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerMessages();
    }

    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event)
    {
        GoopCommands.register(event.getCommandDispatcher());
    }

    @SubscribeEvent
    public static void onWorldLoad(WorldEvent.Load loadEvent) {
        if (!(loadEvent.getWorld() instanceof World)) {
            return;
        }
        World world = (World)loadEvent.getWorld();
        MappingHandler.reloadMappings(world);
    }
}
