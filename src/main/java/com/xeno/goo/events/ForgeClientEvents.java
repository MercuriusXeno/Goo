package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeClientEvents
{
    private static final String EVENT_PHASE_FOR_OVERLAY_INJECTION = "ALL";

    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggedInEvent event) {

        // TODO: this is part of a nicer unavailable gooification display
        TargetingHandler.isGooReady = false;
    }

    @SubscribeEvent
    public static void onDrawTooltip(ItemTooltipEvent event) {
        TargetingHandler.onDraw(event);
    }

    @SubscribeEvent
    public static void onPostTextTooltip(RenderTooltipEvent.PostText event) {

        TargetingHandler.tryDraw(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        TargetingHandler.clearStacks();
    }

    @SubscribeEvent
    public static void onGameOverlay(RenderGameOverlayEvent.Post event) {
        if (!event.getType().name().equals(EVENT_PHASE_FOR_OVERLAY_INJECTION)) {
            return;
        }
        TargetingHandler.onGameOverlay(event);
    }

    @SubscribeEvent
    public static void onLivingRenderPre(RenderLivingEvent.Pre event)
    {
        try
        {
            LivingEntity livingEntity = event.getEntity();
            if(livingEntity instanceof MobEntity)
            {
                livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
                {
                    if(iShrinkProvider.isShrunk())
                    {
                        event.getMatrixStack().push();

                        event.getMatrixStack().scale(iShrinkProvider.scale(), iShrinkProvider.scale(), iShrinkProvider.scale());
                        event.getRenderer().shadowSize = 0.08F;
                    }
                });
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onLivingRenderPost(RenderLivingEvent.Post event)
    {
        try
        {
            LivingEntity livingEntity = event.getEntity();
            if(livingEntity instanceof MobEntity)
            {
                if(livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).isPresent())
                {
                    livingEntity.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(iShrinkProvider ->
                    {
                        if(iShrinkProvider.isShrunk())
                        {
                            event.getMatrixStack().pop();
                        }
                    });
                }
            }
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
