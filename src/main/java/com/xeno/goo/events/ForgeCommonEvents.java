package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBee;
import com.xeno.goo.entities.MutantBee;
import com.xeno.goo.items.GooChopEffects;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeCommonEvents {

    @SubscribeEvent
    public static void onEntityAttacked(AttackEntityEvent event) {
        if (event.getPlayer() == null) {
            return;
        }

        if (event.getPlayer().getHeldItemMainhand().getItem().equals(ItemsRegistry.Gauntlet.get())) {
            if (GooChopEffects.tryDoingChopEffect(event.getPlayer().getHeldItemMainhand(), event.getEntityLiving(), event.getTarget())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityBreed(BabyEntitySpawnEvent event) {
        if (event.getParentA() instanceof MutantBee && event.getParentB() instanceof MutantBee) {
            event.setChild(new GooBee(Registry.GOO_BEE.get(), event.getParentA().world));
        }
    }
}
