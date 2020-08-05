package com.xeno.goop.setup;

import com.xeno.goop.client.render.GoopBulbTileRenderer;
import com.xeno.goop.client.render.SolidifierTileRenderer;
import com.xeno.goop.network.Networking;
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
