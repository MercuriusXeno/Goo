package com.xeno.goo.setup;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.*;
import com.xeno.goo.client.render.GooBulbRenderer;
import com.xeno.goo.client.render.GooEntityRenderer;
import com.xeno.goo.client.render.SolidifierTileRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientSetup
{
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB.get(), RenderType.getCutout());
        GooBulbRenderer.register();
        GooEntityRenderer.register();
        SolidifierTileRenderer.register();
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "crucible"), CrucibleModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "mobius_crucible"), MobiusCrucibleModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "gauntlet"), GauntletModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "combo_gauntlet"), ComboGauntletModelLoader.INSTANCE);
    }
}
