package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.render.*;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClientEvents
{
    @SubscribeEvent
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB_MK2.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB_MK3.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB_MK4.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_BULB_MK5.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.GOO_PUMP.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.MIXER.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.CRUCIBLE.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(Registry.SOLIDIFIER.get(), RenderType.getSolid());
        GooBulbRenderer.register();
        GooPumpRenderer.register();
        MixerRenderer.register();
        SolidifierTileRenderer.register();
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap().getTextureLocation().equals(PlayerContainer.LOCATION_BLOCKS_TEXTURE)) {
            addUnmappedPumpTextures(event);
        }
    }

    private static void addUnmappedPumpTextures(TextureStitchEvent.Pre event)
    {
        // dead code, crucible and gauntlet not implemented yet.
//        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/crucible_fluid"));
//        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/gauntlet_fluid"));

        // dead code, entities not ready for prime time
//        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "block/fluid/crystal_still"));

    }
}
