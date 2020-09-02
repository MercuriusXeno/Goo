package com.xeno.goo.events;

import com.ldtteam.aequivaleo.api.event.OnWorldDataReloadedEvent;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.*;
import com.xeno.goo.aequivaleo.bootstrap.GooValueBootstrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {

    @SubscribeEvent
    public static void onWorldDataLoadEvent(OnWorldDataReloadedEvent event)
    {
        GooValueBootstrapper.onReload(event);
        Equivalencies.resetFurnaceProducts(event.getWorld().getWorld());
    }
}
