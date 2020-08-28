package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.render.GooBulbRenderer;
import com.xeno.goo.client.render.GooPumpRenderer;
import com.xeno.goo.client.render.SolidifierTileRenderer;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClientEvents
{
    @SubscribeEvent
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_PUMP.get(), RenderType.getCutout());
        GooBulbRenderer.register();
        GooPumpRenderer.register();
        SolidifierTileRenderer.register();
    }
}
