package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeClientEvents
{
    @SubscribeEvent
    public static void onDrawTooltip(ItemTooltipEvent event) {
        TooltipHandler.onDraw(event);
    }

    @SubscribeEvent
    public static void postDrawTooltip(RenderTooltipEvent.PostText event) {
        TooltipHandler.postDraw(event);
    }

    @SubscribeEvent
    public static void onGameOverlay(RenderGameOverlayEvent.Post event) {
        TooltipHandler.onGameOverlay(event);
    }
}
