package com.xeno.goo.events;

import com.ldtteam.aequivaleo.api.event.OnWorldDataReloadedEvent;
import com.xeno.goo.GooMod;
import com.xeno.goo.aequivaleo.*;
import com.xeno.goo.aequivaleo.bootstrap.GooValueBootstrapper;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.library.CrucibleRecipes;
import com.xeno.goo.library.MixerRecipes;
import com.xeno.goo.setup.Registry;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {

    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (event.getPlayer().getHeldItemMainhand().getItem().equals(Registry.GAUNTLET.get())) {
            if (GooChopEffects.resolve(event.getPlayer().getHeldItemMainhand(), event.getEntityLiving(), event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldDataLoadEvent(OnWorldDataReloadedEvent event)
    {
        GooValueBootstrapper.onReload(event);
        Equivalencies.resetFurnaceProducts(event.getWorld().getWorld());
    }
}
