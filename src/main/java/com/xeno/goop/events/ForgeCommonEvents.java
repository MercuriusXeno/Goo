package com.xeno.goop.events;

import com.xeno.goop.GoopMod;
import net.minecraft.world.World;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

import java.util.Objects;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {
    // the server starting event unlocks the mapping loader for a one-time use
    @SubscribeEvent
    public static void serverLoad(FMLServerStartingEvent event)
    {
        GoopMod.mappingHandler.reloadMappings(Objects.requireNonNull(event.getServer().getWorld(World.field_234918_g_)));
    }
}
