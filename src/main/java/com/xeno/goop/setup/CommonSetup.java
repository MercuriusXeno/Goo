package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.commands.GoopCommands;
import com.xeno.goop.network.Networking;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerMultiWorld;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonSetup {
    public static final ItemGroup ITEM_GROUP = new ItemGroup("goop")
    {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(Registry.SOLIDIFIER.get());
        }
    };

    public static void init(final FMLCommonSetupEvent event)
    {
        Networking.registerMessages();
    }

    // the server starting event unlocks the mapping loader for a one-time use
    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event)
    {
        GoopCommands.register(event.getCommandDispatcher());
        GoopMod.mappingHandler.reloadMappings(event.getServer().getWorld(DimensionType.OVERWORLD));
    }
}
