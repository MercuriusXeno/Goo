package com.xeno.goop.setup;

import com.xeno.goop.GoopMod;
import com.xeno.goop.client.render.GoopBulbItemRenderer;
import com.xeno.goop.client.render.GoopBulbTileRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GoopMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {
    public static void init(final FMLClientSetupEvent event)
    {
        setGoopBulbTransparencyRenderLayer();
        GoopBulbTileRenderer.register();
    }

    private static void setGoopBulbTransparencyRenderLayer() {
        RenderTypeLookup.setRenderLayer(Registration.GOOP_BULB.get(), RenderType.getCutout());
    }
}
