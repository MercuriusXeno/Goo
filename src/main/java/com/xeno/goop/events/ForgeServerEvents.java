package com.xeno.goop.events;

import com.xeno.goop.GoopMod;
import com.xeno.goop.network.Networking;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, value = Dist.DEDICATED_SERVER, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeServerEvents
{
    @SubscribeEvent
    public static void onJoinWorldEvent(EntityJoinWorldEvent e) {
        if (e.getEntity() instanceof ServerPlayerEntity) {
            Networking.syncGoopValuesForPlayer((ServerPlayerEntity)e.getEntity());
        }
    }
}
