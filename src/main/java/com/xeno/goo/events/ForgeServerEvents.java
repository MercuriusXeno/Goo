package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.network.Networking;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.DEDICATED_SERVER, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeServerEvents
{
    @SubscribeEvent
    public static void onJoinWorldEvent(EntityJoinWorldEvent e) {
        if (e.getEntity() instanceof ServerPlayerEntity) {
            Networking.syncGooValuesForPlayer((ServerPlayerEntity)e.getEntity());
        }
    }
}
