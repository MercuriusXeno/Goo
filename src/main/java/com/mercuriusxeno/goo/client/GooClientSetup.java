package com.mercuriusxeno.goo.client;

import com.google.common.reflect.TypeToken;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.client.ber.*;
import com.mercuriusxeno.goo.client.machine.FuelRemainingProperty;
import com.mercuriusxeno.goo.client.machine.TunerAwaitState;
import com.mercuriusxeno.goo.client.model.*;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.particle.*;
import com.mercuriusxeno.goo.client.throwing.BlobFlightManager;
import com.mercuriusxeno.goo.client.throwing.BlobSizeProperty;
import com.mercuriusxeno.goo.client.throwing.BlobVolumeDecorator;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.registry.*;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Client-side setup: entity renderers and network event handling.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooClientSetup {
    /**
     * Property name for blob size range select.
     */
    private static final String PROP_BLOB_SIZE = "blob_size";
    /**
     * Property name for fuel remaining range select.
     */
    private static final String PROP_FUEL_REMAINING = "fuel_remaining";
    /**
     * Special renderer key for canister goo.
     */
    private static final String RENDERER_CANISTER = "canister_goo";
    /**
     * Special renderer key for vat goo.
     */
    private static final String RENDERER_VAT = "vat_goo";
    /**
     * Special renderer key for glove goo.
     */
    private static final String RENDERER_GLOVE = "glove_goo";
    /**
     * Fluid texture path prefix.
     */
    private static final String FLUID_TEX_PREFIX = "fluid/";
    /**
     * Fluid texture path suffix.
     */
    private static final String FLUID_TEX_SUFFIX = "_fluid";
    /**
     * Opaque alpha OR-mask for fluid tint color.
     */
    private static final int OPAQUE_ALPHA = 0xFF000000;
    /**
     * SuppressWarnings annotation value for unchecked casts.
     */
    private static final String SUPPRESS_UNCHECKED = "unchecked";

    static {
        ISidedProxy.INSTANCE[0] = new ClientProxy();
    }

    private GooClientSetup() {
    }

    /**
     * Registers entity and block entity renderers.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        registerMachineRenderers(event);
        registerEffectRenderers(event);
    }

    /**
     * Registers block entity renderers for machine blocks.
     *
     * @param event the renderer registration event
     */
    private static void registerMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        registerFluidMachineRenderers(event);
        registerLogisticMachineRenderers(event);
    }

    /**
     * Registers renderers for crucible, hub, and canister block entities.
     *
     * @param event the renderer registration event
     */
    private static void registerFluidMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.CRUCIBLE.get(),
                CrucibleBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.HUB.get(),
                HubBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.CANISTER.get(),
                CanisterBlockEntityRenderer::new);
    }

    /**
     * Registers renderers for vat, plexer, and tap block entities.
     *
     * @param event the renderer registration event
     */
    private static void registerLogisticMachineRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.VAT.get(),
                VatBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.PLEXER.get(),
                PlexerBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.REACTOR.get(),
                ReactorBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(GooBlockEntities.TAP.get(),
                TapBlockEntityRenderer::new);
    }

    /**
     * Registers block entity renderers for world effect blocks.
     *
     * @param event the renderer registration event
     */
    private static void registerEffectRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GooBlockEntities.CHAIN_MARKER.get(),
                ChainMarkerBlockEntityRenderer::new);
    }

    /**
     * Registers the goo type icon decorator for all blob and omniblob items.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        BlobVolumeDecorator decorator = new BlobVolumeDecorator();
        for (GooType type : GooType.values()) {
            event.register(GooItems.BLOBS.get(type).get(), decorator);
            event.register(GooItems.OMNIBLOBS.get(type).get(), decorator);
        }
    }

    /**
     * Registers range_dispatch properties for blob size and fuel depletion.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, PROP_BLOB_SIZE),
                BlobSizeProperty.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, PROP_FUEL_REMAINING),
                FuelRemainingProperty.MAP_CODEC
        );
    }

    /**
     * Registers the goo bubble particle provider with its sprite set.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GooParticles.GOO_BUBBLE.get(), GooBubbleParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_SPARK.get(), GooSparkParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_DRIP.get(), GooDripParticle.Provider::new);
        event.registerSpriteSet(GooParticles.GOO_DRIP_LAND.get(), GooDripParticle.LandProvider::new);
        event.registerSpriteSet(GooParticles.GOO_FOG.get(), GooFogParticle.Provider::new);
        event.registerSpriteSet(GooParticles.ORIENTED_BOOM.get(), OrientedBoomParticle.Provider::new);
    }

    /**
     * Registers a render state modifier that injects goo-colored outlineColor
     * onto entities targeted by the glove, producing the spectral glow outline.
     *
     * @param event the event instance
     */
    @SuppressWarnings(SUPPRESS_UNCHECKED)
    @SubscribeEvent
    public static void registerRenderStateModifiers(
            RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
                },
                GooTargetHighlighter::modifyEntityRenderState);
    }

    /**
     * Registers custom render pipelines (additive glow lines, etc.).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerRenderPipelines(RegisterRenderPipelinesEvent event) {
        GooRenderTypes.registerPipelines(event);
    }

    /**
     * Registers standalone baked models for canister and vat item rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        CanisterBodyModels.register(event);
        VatBodyModels.register(event);
        GloveBodyModels.register(event);
    }

    /**
     * Registers special model renderer types for canister and vat item rendering.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_CANISTER),
                CanisterSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_VAT),
                VatSpecialRenderer.Unbaked.MAP_CODEC
        );
        event.register(
                Identifier.fromNamespaceAndPath(Goo.MODID, RENDERER_GLOVE),
                GloveSpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    /**
     * Registers FluidModel for each goo type so fluids render in-world.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (GooType type : GooType.values()) {
            String id = type.getId();
            Material texture = new Material(
                    Identifier.fromNamespaceAndPath(Goo.MODID, FLUID_TEX_PREFIX + id + FLUID_TEX_SUFFIX), true);
            FluidModel.Unbaked model = new FluidModel.Unbaked(
                    texture, texture, null,
                    FluidTintSources.constant(OPAQUE_ALPHA | type.getColor()));
            event.register(model,
                    GooFluids.SOURCES.get(type),
                    GooFluids.FLOWING.get(type));
        }
    }

    /**
     * Registers client-side fluid rendering extensions for all goo fluid types.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        for (GooType type : GooType.values()) {
            FluidType fluidType = GooFluidTypes.TYPES.get(type).get();
            event.registerFluidType(createFluidExtensions(type), fluidType);
        }
    }

    /**
     * Creates the client fluid extensions for a goo type (fog/overlay only in 26.1).
     *
     * @param type the goo type
     * @return a new IClientFluidTypeExtensions instance
     */
    private static IClientFluidTypeExtensions createFluidExtensions(GooType type) {
        return new IClientFluidTypeExtensions() {
        };
    }

    /**
     * Clears cached goo values and tuner await state on disconnect.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        Goo.GOO_VALUES.clearAll();
        TunerAwaitState.clear();
        BlobFlightManager.clear();
        ThrowFreezeState.clear();
    }

    /**
     * Clears tuner await state on login (dimension change).
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        TunerAwaitState.clear();
    }

}
