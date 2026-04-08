package com.mercuriusxeno.goo.client.tooltip;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * Registers the mapping from GooValueTooltipComponent data to its client renderer.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTooltipComponentFactory {

    private GooTooltipComponentFactory() {}

    /**
     * Maps the goo tooltip data model to its client-side renderer.
     *
     * @param event the tooltip component factory registration event
     */
    @SubscribeEvent
    public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(GooValueTooltipComponent.class, GooValueClientTooltipComponent::new);
    }
}
