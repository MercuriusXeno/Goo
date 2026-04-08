package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.PlayerInventorySlotHandler;
import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.item.BucketGooFluidHandler;
import com.mercuriusxeno.goo.item.CanisterFluidHandler;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.registry.GooFluidTypes;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.registry.GooTickets;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

@Mod(Goo.MODID)
public class Goo {

    public static final String MODID = "goo";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final GooValueRegistry GOO_VALUES = new GooValueRegistry();
    /** Log message for startup value loading. */
    private static final String LOG_VALUES_LOADED = "Goo values loaded: {} effective values from cache";

    /**
     * Registers all deferred registries, event listeners, and config on mod construction.
     *
     * @param modEventBus  the mod event bus
     * @param modContainer the mod container
     */
    public Goo(IEventBus modEventBus, ModContainer modContainer) {
        GooFluidTypes.FLUID_TYPES.register(modEventBus);
        GooFluids.FLUIDS.register(modEventBus);
        GooBlocks.BLOCKS.register(modEventBus);
        GooItems.ITEMS.register(modEventBus);
        GooBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        GooEntities.ENTITIES.register(modEventBus);
        GooDataComponents.DATA_COMPONENTS.register(modEventBus);
        GooPotions.POTIONS.register(modEventBus);
        GooParticles.PARTICLE_TYPES.register(modEventBus);
        GooCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(Goo::registerCapabilities);
        modEventBus.addListener(Goo::commonSetup);
        modEventBus.addListener(GooTickets::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, GooConfig.SPEC);

        NeoForge.EVENT_BUS.register(this);

        GOO_VALUES.setEffectiveCachePath(
            FMLPaths.CONFIGDIR.get().resolve("goo_derived_values.json"));

        LOGGER.info("Goo mod initialized");
    }

    /**
     * Common setup - registers chain profiles for world effects.
     *
     * @param event the common setup event
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        com.mercuriusxeno.goo.effect.ChainProfiles.registerAll();
    }

    /**
     * Registers capabilities: fluid handlers and gasket endpoint resolution.
     *
     * @param event the capability registration event
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerItemFluidCapabilities(event);
        registerBlockFluidCapabilities(event);
        registerGasketBlockCapabilities(event);
        registerGasketEntityCapabilities(event);
    }

    /**
     * Registers item-level fluid handlers for buckets and canisters.
     *
     * @param event the capability registration event
     */
    private static void registerItemFluidCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
            Capabilities.Fluid.ITEM,
            (stack, ctx) -> new BucketGooFluidHandler(ctx),
            GooItems.BUCKET_OF_GOO.get()
        );
        event.registerItem(
            Capabilities.Fluid.ITEM,
            (stack, ctx) -> new CanisterFluidHandler(ctx),
            GooItems.CANISTER.get()
        );
    }

    /**
     * Registers block-level fluid handlers for vat and hub (local adjacency for tap/pipes).
     *
     * @param event the capability registration event
     */
    private static void registerBlockFluidCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,
            GooBlockEntities.VAT.get(),
            (be, side) -> (side == null || side == net.minecraft.core.Direction.UP
                || side == net.minecraft.core.Direction.DOWN)
                ? be.getFluidHandler() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,
            GooBlockEntities.HUB.get(),
            (be, side) -> (side == null || side == net.minecraft.core.Direction.UP)
                ? be.getFluidHandler() : null);
    }

    /**
     * Registers GASKET_BLOCK capabilities for all machine block entities.
     *
     * @param event the capability registration event
     */
    private static void registerGasketBlockCapabilities(RegisterCapabilitiesEvent event) {
        registerCanisterGasketBlock(event);
        registerHubGasketBlock(event);
        registerSimpleGasketBlocks(event);
    }

    /**
     * Registers GASKET_BLOCK for canister: scans slots for gasket UUID match.
     *
     * @param event the capability registration event
     */
    private static void registerCanisterGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.CANISTER.get(), (be, gasketId) -> {
                for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
                    if (be.getCanister(i).isEmpty()) { continue; }
                    CanisterMetadata meta = be.getSlotMetadata(i);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return be.getSlotFluidHandler(i);
                    }
                }
                return null;
            });
    }

    /**
     * Registers GASKET_BLOCK for hub: checks intake gasket then scans canister slots.
     *
     * @param event the capability registration event
     */
    private static void registerHubGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.HUB.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))) {
                    return be.getFluidHandler();
                }
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (be.getCanister(i).isEmpty()) { continue; }
                    CanisterMetadata meta = be.getSlotMetadata(i);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return be.getSlotFluidHandler(i);
                    }
                }
                return null;
            });
    }

    /**
     * Registers GASKET_BLOCK for vat, tap, and plexer (simple gasket ID checks).
     *
     * @param event the capability registration event
     */
    private static void registerSimpleGasketBlocks(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.VAT.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))
                        || gasketId.equals(be.getGasketId(GasketRole.TRANSMITTER))) {
                    return be.getFluidHandler();
                }
                return null;
            });

        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.TAP.get(), (be, gasketId) -> {
                // Tap doesn't have a fluid handler - it drips, not receives.
                // Remote delivery to a tap is not supported yet.
                return null;
            });

        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.PLEXER.get(), (be, gasketId) -> {
                // Plexer receives goo into its external canisters (above).
                // Fluid routing TBD.
                return null;
            });
    }

    /**
     * Registers GASKET_ENTITY capability for player inventory canister scanning.
     *
     * @param event the capability registration event
     */
    private static void registerGasketEntityCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(GooCapabilities.GASKET_ENTITY,
            net.minecraft.world.entity.EntityType.PLAYER, (player, gasketId) -> {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.is(GooItems.CANISTER.get())) { continue; }
                    CanisterMetadata meta = CanisterItem.getMetadata(stack);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return new PlayerInventorySlotHandler(player, i);
                    }
                }
                return null;
            });
    }

    /**
     * Loads goo values from the effective cache when the server starts.
     *
     * @param event the server starting event
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        GOO_VALUES.loadEffectiveCache();
        if (LOGGER.isInfoEnabled()) { LOGGER.info(LOG_VALUES_LOADED, GOO_VALUES.size()); }
    }

    /**
     * Registers /goo subcommands with the server command dispatcher.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GooCommand.register(event.getDispatcher());
    }

    /**
     * Sends the full goo value map to a player when they log in.
     *
     * @param event the player login event
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            GooValueSync.sendToPlayer(serverPlayer);
        }
    }

    /**
     * Ticks pending blob effects so they apply on arrival.
     *
     * @param event the post-tick event instance
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        com.mercuriusxeno.goo.network.BlobThrowHandler.onServerTick(event);
    }
}
