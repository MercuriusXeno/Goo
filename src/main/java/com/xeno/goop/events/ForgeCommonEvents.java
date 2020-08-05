package com.xeno.goop.events;

import com.xeno.goop.GoopMod;
import com.xeno.goop.network.Networking;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {
    // the server starting event unlocks the mapping loader for a one-time use
    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event)
    {
        GoopMod.mappingHandler.reloadMappings(event.getServer().getWorld(DimensionType.OVERWORLD));
    }
}
