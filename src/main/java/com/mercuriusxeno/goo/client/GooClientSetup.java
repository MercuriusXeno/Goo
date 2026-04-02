package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.client.particle.GooBubbleParticle;
import com.mercuriusxeno.goo.client.particle.GooDripParticle;
import com.mercuriusxeno.goo.client.particle.GooFogParticle;
import com.mercuriusxeno.goo.client.particle.GooSparkParticle;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooFluidTypes;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.fluids.FluidType;

import com.google.common.reflect.TypeToken;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

/**
 * Client-side setup: entity renderers and network event handling.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public class GooClientSetup {

    static {
        ISidedProxy.INSTANCE[0] = new ClientProxy();
    }

    /** Registers entity and block entity renderers. */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.CRUCIBLE.get(),
            CrucibleBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.HUB.get(),
            HubBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.CANISTER.get(),
            CanisterBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.VAT.get(),
            VatBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.PLEXER.get(),
            PlexerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.TAP.get(),
            TapBlockEntityRenderer::new);
    }

    /** Registers the goo type icon decorator for all blob and omniblob items. */
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        BlobVolumeDecorator decorator = new BlobVolumeDecorator();
        for (GooType type : GooType.values()) {
            event.register(GooItems.BLOBS.get(type).get(), decorator);
            event.register(GooItems.OMNIBLOBS.get(type).get(), decorator);
        }
    }

    /** Registers range_dispatch properties for blob size and fuel depletion. */
    @SubscribeEvent
    public static void registerRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
            Identifier.fromNamespaceAndPath(Goo.MODID, "blob_size"),
            BlobSizeProperty.MAP_CODEC
        );
        event.register(
            Identifier.fromNamespaceAndPath(Goo.MODID, "fuel_remaining"),
            FuelRemainingProperty.MAP_CODEC
        );
    }

    /** Registers the goo bubble particle provider with its sprite set. */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GooParticles.GOO_BUBBLE.get(), GooBubbleParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_SPARK.get(), GooSparkParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_DRIP.get(), GooDripParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_DRIP_LAND.get(), GooDripParticle.LandProvider::new);
        event.registerSpriteSet(GooParticles.GOO_FOG.get(), GooFogParticle.Provider::new);
    }

    /**
     * Registers a render state modifier that injects goo-colored outlineColor
     * onto entities targeted by the glove, producing the spectral glow outline.
     */
    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void registerRenderStateModifiers(
            RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {},
                GooTargetHighlighter::modifyEntityRenderState);
    }

    /** Registers standalone baked models for canister and vat item rendering. */
    @SubscribeEvent
    public static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        CanisterBodyModels.register(event);
        VatBodyModels.register(event);
        GloveBodyModels.register(event);
    }

    /** Registers special model renderer types for canister and vat item rendering. */
    @SubscribeEvent
    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
            Identifier.fromNamespaceAndPath(Goo.MODID, "canister_goo"),
            CanisterSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
            Identifier.fromNamespaceAndPath(Goo.MODID, "vat_goo"),
            VatSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
            Identifier.fromNamespaceAndPath(Goo.MODID, "glove_goo"),
            GloveSpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    /** Registers FluidModel for each goo type so fluids render in-world. */
    @SubscribeEvent
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (GooType type : GooType.values()) {
            String id = type.getId();
            Material texture = new Material(
                Identifier.fromNamespaceAndPath(Goo.MODID, "fluid/" + id + "_fluid"), true);
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                texture, texture, null,
                FluidTintSources.constant(0xFF000000 | type.getColor()));
            event.register(model,
                GooFluids.SOURCES.get(type),
                GooFluids.FLOWING.get(type));
        }
    }

    /** Registers client-side fluid rendering extensions for all goo fluid types. */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        for (GooType type : GooType.values()) {
            FluidType fluidType = GooFluidTypes.TYPES.get(type).get();
            event.registerFluidType(createFluidExtensions(type), fluidType);
        }
    }

    /** Creates the client fluid extensions for a goo type (fog/overlay only in 26.1). */
    private static IClientFluidTypeExtensions createFluidExtensions(GooType type) {
        return new IClientFluidTypeExtensions() {};
    }

    /** Clears cached goo values and tuner await state on disconnect. */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Goo.GOO_VALUES.clearAll();
        TunerAwaitState.clear();
        BlobFlightManager.clear();
    }

    /** Clears tuner await state on login (dimension change). */
    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        TunerAwaitState.clear();
    }
}
