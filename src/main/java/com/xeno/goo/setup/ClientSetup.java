package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.*;
import com.xeno.goo.client.render.GooBulbRenderer;
import com.xeno.goo.client.render.GooRenderer;
import com.xeno.goo.client.render.SolidifierTileRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup
{
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB.get(), RenderType.getCutout());
        GooBulbRenderer.register();
        registerGooEntityRenderers();
        // StupidRenderer.register();
        SolidifierTileRenderer.register();
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "crucible"), CrucibleModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "gauntlet"), GauntletModel.Loader.INSTANCE);
    }

    private static void registerGooEntityRenderers()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.CRYSTAL.get(), GooRenderer::new);
    }
}
