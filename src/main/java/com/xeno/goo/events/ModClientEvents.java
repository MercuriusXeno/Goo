package com.xeno.goo.events;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.client.models.VesselModel;
import com.xeno.goo.client.particle.BubbleParticle;
import com.xeno.goo.client.particle.GooParticle;
import com.xeno.goo.client.particle.SprayParticle;
import com.xeno.goo.client.particle.VaporParticle;
import com.xeno.goo.client.render.block.*;
import com.xeno.goo.client.render.entity.*;
import com.xeno.goo.items.GauntletAbstraction;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.TextureStitchEvent.Pre;
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

        // wire up client side item properties
        setItemProperties();

        // set item transparencies
        setItemTransparency();
    }

    private static void setItemTransparency() {

    }

    private static void setItemProperties() {
        ItemModelsProperties.registerProperty(ItemsRegistry.GAUNTLET.get(), new ResourceLocation(GooMod.MOD_ID, "held_liquid"), GauntletAbstraction::getHeldLiquidOverride);
    }

    private static void setEntityRenderers()
    {
        GooBlobRenderer.register();
        GooSplatRenderer.register();
        GooBeeRenderer.register();
        GooSnailRenderer.register();
        MutantBeeRendeerer.register();
        LightingBugRenderer.register();
    }

    private static void setRenderLayers()
    {
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Bulb.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Mixer.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Pad.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Pump.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Degrader.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Solidifier.get(), (layer) -> layer == RenderType.getCutoutMipped() || layer == RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Lobber.get(), RenderType.getSolid());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Gooifier.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Trough.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(BlocksRegistry.Crucible.get(), RenderType.getCutoutMipped());
    }

    private static void setTileEntityRenderers()
    {
        BulbRenderer.register();
        PumpRenderer.register();
        MixerRenderer.register();
        DegraderRenderer.register();
        SolidifierRenderer.register();
        TroughRenderer.register();
        CrucibleRenderer.register();
        PadRenderer.register();
    }

    private static void setModelLoaders()
    {
        ModelLoaderRegistry.registerLoader(new ResourceLocation(GooMod.MOD_ID, "vessel"), VesselModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getMap().getTextureLocation().equals(PlayerContainer.LOCATION_BLOCKS_TEXTURE)) {
            addVesselMaskingTexture(event);
            addGauntletMaskingTexture(event);
            addUnmappedOverlayTextures(event);
            addSolidifierHatchTextures(event);
        }
    }

    private static void addSolidifierHatchTextures(Pre event) {
        event.addSprite(Resources.Hatch.INNER_OPEN);
        event.addSprite(Resources.Hatch.INNER_WANING);
        event.addSprite(Resources.Hatch.INNER_HALF);
        event.addSprite(Resources.Hatch.INNER_WAXING);
        event.addSprite(Resources.Hatch.INNER_CLOSED);
        event.addSprite(Resources.Hatch.OUTER_OPEN);
        event.addSprite(Resources.Hatch.OUTER_WANING);
        event.addSprite(Resources.Hatch.OUTER_HALF);
        event.addSprite(Resources.Hatch.OUTER_WAXING);
        event.addSprite(Resources.Hatch.OUTER_CLOSED);

    }

    private static void addGauntletMaskingTexture(TextureStitchEvent.Pre event)
    {
        // event.addSprite(new ResourceLocation(GooMod.MOD_ID, "item/gauntlet"));
        event.addSprite(Resources.Gauntlet.MASKING_TEXTURE);
    }

    private static void addUnmappedOverlayTextures(TextureStitchEvent.Pre event)
    {
        event.addSprite(Resources.Still.OVERLAY);
        event.addSprite(Resources.Flowing.OVERLAY);
    }

    private static void addVesselMaskingTexture(TextureStitchEvent.Pre event)
    {
        event.addSprite(Resources.Vessel.MASKING_TEXTURE);
    }

    @SubscribeEvent
    public static void onItemColor(ColorHandlerEvent.Item event) {
        for(SpawnEggItem egg : Registry.EGGS) {
            event.getItemColors().register((stack, color) -> egg.getColor(color), egg);
        };
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
        Minecraft.getInstance().particles.registerFactory(Registry.PRIMORDIAL_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.PRIMORDIAL_GOO.get(), Registry.PRIMORDIAL_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.PRIMORDIAL_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.PRIMORDIAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.RADIANT_FALLING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.FallingGooFactory(iAnimatedSprite, Registry.RADIANT_GOO.get(), Registry.RADIANT_LANDING_GOO_PARTICLE.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.RADIANT_LANDING_GOO_PARTICLE.get(), (iAnimatedSprite) -> new GooParticle.LandingGooFactory(iAnimatedSprite, Registry.RADIANT_GOO.get()));
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

        Minecraft.getInstance().particles.registerFactory(Registry.AQUATIC_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.AQUATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CHROMATIC_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.CHROMATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CRYSTAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.CRYSTAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.DECAY_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.DECAY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.EARTHEN_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.EARTHEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.ENERGETIC_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.ENERGETIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FAUNAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.FAUNAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FLORAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.FLORAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FUNGAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.FUNGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.HONEY_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.HONEY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.LOGIC_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.LOGIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.METAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.METAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.MOLTEN_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.MOLTEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.PRIMORDIAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.PRIMORDIAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.RADIANT_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.RADIANT_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.REGAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.REGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SLIME_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.SLIME_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SNOW_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.SNOW_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.VITAL_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.VITAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.WEIRD_VAPOR_PARTICLE.get(), (iAnimatedSprite) -> new VaporParticle.VaporGooFactory(iAnimatedSprite, Registry.WEIRD_GOO.get()));

        Minecraft.getInstance().particles.registerFactory(Registry.AQUATIC_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.AQUATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CHROMATIC_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.CHROMATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CRYSTAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.CRYSTAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.DECAY_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.DECAY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.EARTHEN_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.EARTHEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.ENERGETIC_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.ENERGETIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FAUNAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.FAUNAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FLORAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.FLORAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FUNGAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.FUNGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.HONEY_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.HONEY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.LOGIC_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.LOGIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.METAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.METAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.MOLTEN_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.MOLTEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.PRIMORDIAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.PRIMORDIAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.RADIANT_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.RADIANT_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.REGAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.REGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SLIME_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.SLIME_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SNOW_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.SNOW_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.VITAL_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.VITAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.WEIRD_SPRAY_PARTICLE.get(), (iAnimatedSprite) -> new SprayParticle.SprayGooFactory(iAnimatedSprite, Registry.WEIRD_GOO.get()));

        Minecraft.getInstance().particles.registerFactory(Registry.AQUATIC_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.AQUATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CHROMATIC_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.CHROMATIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.CRYSTAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.CRYSTAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.DECAY_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.DECAY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.EARTHEN_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.EARTHEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.ENERGETIC_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.ENERGETIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FAUNAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.FAUNAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FLORAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.FLORAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.FUNGAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.FUNGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.HONEY_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.HONEY_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.LOGIC_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.LOGIC_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.METAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.METAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.MOLTEN_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.MOLTEN_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.PRIMORDIAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.PRIMORDIAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.RADIANT_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.RADIANT_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.REGAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.REGAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SLIME_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.SLIME_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.SNOW_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.SNOW_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.VITAL_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.VITAL_GOO.get()));
        Minecraft.getInstance().particles.registerFactory(Registry.WEIRD_BUBBLE_PARTICLE.get(), (iAnimatedSprite) -> new BubbleParticle.BubbleFactory(iAnimatedSprite, Registry.WEIRD_GOO.get()));
    }
}
