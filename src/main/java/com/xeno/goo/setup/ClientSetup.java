package com.xeno.goo.setup;

import com.xeno.goo.client.render.GoopBulbTileRenderer;
import com.xeno.goo.client.render.SolidifierTileRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup
{
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        RenderTypeLookup.setRenderLayer(Registry.GOOP_BULB.get(), RenderType.getCutout());
        GoopBulbTileRenderer.register();
        SolidifierTileRenderer.register();
    }
}
