package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.client.models.*;
import com.xeno.goo.client.particle.GooParticle;
import com.xeno.goo.client.render.*;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GooMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModClientEvents
{
    @SubscribeEvent
    public static void onModelRegistration(final ModelRegistryEvent event) {
        // model loaders
        setModelLoaders();
    }

    @SubscribeEvent
    public static void init(final FMLClientSetupEvent event)
    {
        // rendering stuff
        setRenderLayers();

        // tile entity renders
        setTileEntityRenderers();

        // entity renderers
        setEntityRenderers();
    }

    private static void setEntityRenderers()
    {
        GooBlobRenderer.register();
        GooSplatRenderer.register();
    }

    private static void setRenderLayers()
    {
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Bulb.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Pump.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Mixer.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Crucible.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Solidifier.get(), RenderType.getSolid());
    }

    private static void setTileEntityRenderers()
    {
        GooBulbRenderer.register();
        GooPumpRenderer.register();
        MixerRenderer.register();
        CrucibleRenderer.register();
        SolidifierRenderer.register();
    }

    private static void setModelLoaders()
    {
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "basin"), BasinModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "gauntlet"), GauntletModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "sliver"), SliverModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "shard"), ShardModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "crystal"), CrystalModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "chunk"), ChunkModel.Loader.INSTANCE);
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "slab"), SlabModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap().getTextureLocation().equals(PlayerContainer.LOCATION_BLOCKS_TEXTURE)) {
            addBasinMaskingTexture(event);
            addGauntletMaskingTexture(event);
            addUnmappedOverlayTextures(event);
        }
    }

    private static void addGauntletMaskingTexture(TextureStitchEvent.Pre event)
    {
        // event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/gauntlet"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/gauntlet_fluid"));
    }

    private static void addUnmappedOverlayTextures(TextureStitchEvent.Pre event)
    {
        event.addSprite(Resources.Still.OVERLAY);
        event.addSprite(Resources.Flowing.OVERLAY);
    }

    private static void addBasinMaskingTexture(TextureStitchEvent.Pre event)
    {
        // event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/basin"));
        event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/mask/basin_fluid"));
    }

    @SubscribeEvent
    public static void onParticleRegistration(final ParticleFactoryRegisterEvent event) {
        Minecraft.getInstance().particles.registerFactory(Registry.AQUATIC_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.AQUATIC_GOO.get(), Registry.AQUATIC_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.AQUATIC_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.AQUATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CHROMATIC_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.CHROMATIC_GOO.get(), Registry.CHROMATIC_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CHROMATIC_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.CHROMATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CRYSTAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.CRYSTAL_GOO.get(), Registry.CRYSTAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CRYSTAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.CRYSTAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.DECAY_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.DECAY_GOO.get(), Registry.DECAY_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.DECAY_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.DECAY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.EARTHEN_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.EARTHEN_GOO.get(), Registry.EARTHEN_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.EARTHEN_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.EARTHEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.ENERGETIC_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.ENERGETIC_GOO.get(), Registry.ENERGETIC_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.ENERGETIC_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.ENERGETIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FAUNAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.FAUNAL_GOO.get(), Registry.FAUNAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FAUNAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.FAUNAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FLORAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.FLORAL_GOO.get(), Registry.FLORAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FLORAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.FLORAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FUNGAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.FUNGAL_GOO.get(), Registry.FUNGAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FUNGAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.FUNGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.HONEY_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.HONEY_GOO.get(), Registry.HONEY_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.HONEY_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.HONEY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.LOGIC_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.LOGIC_GOO.get(), Registry.LOGIC_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.LOGIC_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.LOGIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.METAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.METAL_GOO.get(), Registry.METAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.METAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.METAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.MOLTEN_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.MOLTEN_GOO.get(), Registry.MOLTEN_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.MOLTEN_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.MOLTEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.OBSIDIAN_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.OBSIDIAN_GOO.get(), Registry.OBSIDIAN_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.OBSIDIAN_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.OBSIDIAN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.REGAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.REGAL_GOO.get(), Registry.REGAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.REGAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.REGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SLIME_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.SLIME_GOO.get(), Registry.SLIME_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SLIME_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.SLIME_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SNOW_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.SNOW_GOO.get(), Registry.SNOW_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SNOW_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.SNOW_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.VITAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.VITAL_GOO.get(), Registry.VITAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.VITAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.VITAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.WEIRD_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.WEIRD_GOO.get(), Registry.WEIRD_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.WEIRD_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.WEIRD_GOO.get()));
    }
}
