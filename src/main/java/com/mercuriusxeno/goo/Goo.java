package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.PlayerInventorySlotHandler;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.item.BucketGooFluidHandler;
import com.mercuriusxeno.goo.item.CanisterFluidHandler;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooBlocks;
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

        GOO_VALUES.setDerivedCachePath(
            FMLPaths.CONFIGDIR.get().resolve("goo_derived_values.json"));

        LOGGER.info("Goo mod initialized");
    }

    /** Common setup - registers chain profiles for world effects. */
    private static void commonSetup(FMLCommonSetupEvent event) {
        com.mercuriusxeno.goo.effect.ChainProfiles.registerAll();
    }

    /** Registers capabilities: fluid handlers and gasket endpoint resolution. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Item fluid handlers
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

        // Block fluid handlers (local adjacency for tap/pipes)
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,
            GooBlockEntities.VAT.get(),
            (be, side) -> (side == null || side == net.minecraft.core.Direction.UP
                || side == net.minecraft.core.Direction.DOWN)
                ? be.getFluidHandler() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK,
            GooBlockEntities.HUB.get(),
            (be, side) -> (side == null || side == net.minecraft.core.Direction.UP)
                ? be.getFluidHandler() : null);

        // GASKET_BLOCK: canister shelf scans slots for gasket UUID match
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.CANISTER.get(), (be, gasketId) -> {
                for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
                    if (be.getCanister(i).isEmpty()) continue;
                    CanisterMetadata meta = be.getSlotMetadata(i);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return be.getSlotFluidHandler(i);
                    }
                }
                return null;
            });

        // GASKET_BLOCK: hub scans canister slots for gasket UUID match
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.HUB.get(), (be, gasketId) -> {
                // Check hub intake gasket first
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))) {
                    return be.getFluidHandler();
                }
                for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
                    if (be.getCanister(i).isEmpty()) continue;
                    CanisterMetadata meta = be.getSlotMetadata(i);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return be.getSlotFluidHandler(i);
                    }
                }
                return null;
            });

        // GASKET_BLOCK: vat checks cap/base gasket IDs
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.VAT.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))
                        || gasketId.equals(be.getGasketId(GasketRole.TRANSMITTER))) {
                    return be.getFluidHandler();
                }
                return null;
            });

        // GASKET_BLOCK: tap checks its single receiver gasket
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.TAP.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))) {
                    // Tap doesn't have a fluid handler - it drips, not receives.
                    // Return null; remote delivery to a tap is not supported yet.
                    return null;
                }
                return null;
            });

        // GASKET_BLOCK: plexer checks its single receiver gasket
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.PLEXER.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))) {
                    // Plexer receives goo into its external canisters (above).
                    // Return null for now - fluid routing TBD.
                    return null;
                }
                return null;
            });

        // GASKET_ENTITY: player inventory scans for canister with matching gasket
        event.registerEntity(GooCapabilities.GASKET_ENTITY,
            net.minecraft.world.entity.EntityType.PLAYER, (player, gasketId) -> {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.is(GooItems.CANISTER.get())) continue;
                    CanisterMetadata meta = CanisterItem.getMetadata(stack);
                    if (gasketId.equals(meta.topGasketId())
                            || gasketId.equals(meta.bottomGasketId())) {
                        return new PlayerInventorySlotHandler(player, i);
                    }
                }
                return null;
            });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        GOO_VALUES.loadBaseValues();
        GOO_VALUES.loadDerivedCache();
        LOGGER.info("Goo values loaded: {} base, {} derived",
            GOO_VALUES.baseSize(), GOO_VALUES.derivedSize());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        GooCommand.register(event.getDispatcher());
    }

    /** Sends the full goo value map to a player when they log in. */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            GooValueSync.sendToPlayer(serverPlayer);
        }
    }

    /** Ticks pending blob effects so they apply on arrival. */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        com.mercuriusxeno.goo.network.BlobThrowHandler.onServerTick(event);
    }
}
