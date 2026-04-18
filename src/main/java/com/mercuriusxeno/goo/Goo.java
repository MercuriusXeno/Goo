package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.registry.GooFluidTypes;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.registry.GooPotions;
import com.mercuriusxeno.goo.registry.GooSounds;
import com.mercuriusxeno.goo.registry.GooTickets;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
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
        registerDeferredRegistries(modEventBus);
        registerModListeners(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, GooConfig.SPEC);

        NeoForge.EVENT_BUS.register(this);

        GOO_VALUES.setEffectiveCachePath(
            FMLPaths.CONFIGDIR.get().resolve("goo_derived_values.json"));

        GooColors.load(FMLPaths.CONFIGDIR.get());

        LOGGER.info("Goo mod initialized");
    }

    /**
     * Registers all deferred registries with the mod event bus.
     *
     * @param modEventBus the mod event bus
     */
    private static void registerDeferredRegistries(IEventBus modEventBus) {
        registerCoreRegistries(modEventBus);
        registerContentRegistries(modEventBus);
    }

    /**
     * Registers fluid, block, item, and entity registries.
     * @param modEventBus the mod event bus to register on
     */
    private static void registerCoreRegistries(IEventBus modEventBus) {
        GooFluidTypes.FLUID_TYPES.register(modEventBus);
        GooFluids.FLUIDS.register(modEventBus);
        GooBlocks.BLOCKS.register(modEventBus);
        GooItems.ITEMS.register(modEventBus);
        GooBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        GooEntities.ENTITIES.register(modEventBus);
    }

    /**
     * Registers data component, potion, particle, and creative tab registries.
     * @param modEventBus the mod event bus to register on
     */
    private static void registerContentRegistries(IEventBus modEventBus) {
        GooDataComponents.DATA_COMPONENTS.register(modEventBus);
        GooPotions.POTIONS.register(modEventBus);
        GooParticles.PARTICLE_TYPES.register(modEventBus);
        GooSounds.SOUND_EVENTS.register(modEventBus);
        GooCreativeTabs.TABS.register(modEventBus);
    }

    /**
     * Registers mod event bus listeners for capabilities, setup, and tickets.
     *
     * @param modEventBus the mod event bus
     */
    private static void registerModListeners(IEventBus modEventBus) {
        modEventBus.addListener(GooCapabilityRegistration::registerCapabilities);
        modEventBus.addListener(Goo::commonSetup);
        modEventBus.addListener(GooTickets::register);
    }

    /**
     * Common setup - registers chain profiles for world effects.
     *
     * @param event the common setup event
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        com.mercuriusxeno.goo.effect.ChainProfiles.registerAll();
        com.mercuriusxeno.goo.item.gasket.ChoralGasketItem.setGasketBlockSupplier(
                GooBlocks.CHORAL_GASKET_BLOCK::get);
    }

    /**
     * Registers the reaction datapack reload listener.
     *
     * @param event the reload listener registration event
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(GooReactionLoader.LISTENER_ID, new GooReactionLoader());
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
        if (com.mercuriusxeno.goo.block.ChainMarkerFallScheduler.hasPending()) {
            com.mercuriusxeno.goo.block.ChainMarkerFallScheduler
                    .drainArrivedFalls(event.getServer().getTickCount());
        }
    }
}
