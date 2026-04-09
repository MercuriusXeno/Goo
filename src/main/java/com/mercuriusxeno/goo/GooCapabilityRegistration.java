package com.mercuriusxeno.goo;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.HubBlockEntity;
import com.mercuriusxeno.goo.block.ISlottedGooContainer;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.fluid.PlayerInventorySlotHandler;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.fluid.BucketGooFluidHandler;
import com.mercuriusxeno.goo.item.fluid.CanisterFluidHandler;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Centralizes all NeoForge capability registrations for the Goo mod.
 *
 * <p>Covers fluid handlers (item and block), gasket block endpoints,
 * and gasket entity scanning. Called once from the mod event bus
 * during construction.</p>
 */
final class GooCapabilityRegistration {

    private GooCapabilityRegistration() { }

    /**
     * Registers capabilities: fluid handlers and gasket endpoint resolution.
     *
     * @param event the capability registration event
     */
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
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
    static void registerItemFluidCapabilities(RegisterCapabilitiesEvent event) {
        registerBucketFluidCapability(event);
        registerCanisterFluidCapability(event);
    }

    /**
     * Registers fluid handler for goo buckets.
     *
     * @param event the capability registration event
     */
    private static void registerBucketFluidCapability(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM,
            (stack, ctx) -> new BucketGooFluidHandler(ctx), GooItems.BUCKET_OF_GOO.get());
    }

    /**
     * Registers fluid handler for canisters.
     *
     * @param event the capability registration event
     */
    private static void registerCanisterFluidCapability(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.Fluid.ITEM,
            (stack, ctx) -> new CanisterFluidHandler(ctx), GooItems.CANISTER.get());
    }

    /**
     * Registers block-level fluid handlers for vat and hub (local adjacency for tap/pipes).
     *
     * @param event the capability registration event
     */
    static void registerBlockFluidCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GooBlockEntities.VAT.get(),
            (be, side) -> isVatFluidSide(side) ? be.getFluidHandler() : null);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, GooBlockEntities.HUB.get(),
            (be, side) -> isHubFluidSide(side) ? be.getFluidHandler() : null);
    }

    /**
     * Returns true if the vat exposes a fluid handler on this side (null, UP, DOWN).
     *
     * @param side the queried direction, or null for internal
     * @return true if the side is valid for fluid access
     */
    private static boolean isVatFluidSide(@Nullable Direction side) {
        return side == null || side == Direction.UP || side == Direction.DOWN;
    }

    /**
     * Returns true if the hub exposes a fluid handler on this side (null, UP).
     *
     * @param side the queried direction, or null for internal
     * @return true if the side is valid for fluid access
     */
    private static boolean isHubFluidSide(@Nullable Direction side) {
        return side == null || side == Direction.UP;
    }

    /**
     * Registers GASKET_BLOCK capabilities for all machine block entities.
     *
     * @param event the capability registration event
     */
    static void registerGasketBlockCapabilities(RegisterCapabilitiesEvent event) {
        registerCanisterGasketBlock(event);
        registerHubGasketBlock(event);
        registerSimpleGasketBlocks(event);
    }

    /**
     * Registers GASKET_BLOCK for canister: scans slots for gasket UUID match.
     *
     * @param event the capability registration event
     */
    static void registerCanisterGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.CANISTER.get(),
            (be, gasketId) -> findSlotForGasket(be, CanisterBlockEntity.MAX_SLOTS, gasketId));
    }

    /**
     * Registers GASKET_BLOCK for hub: checks intake gasket then scans canister slots.
     *
     * @param event the capability registration event
     */
    static void registerHubGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.HUB.get(), (be, gasketId) -> resolveHubGasket(be, gasketId));
    }

    /**
     * Checks the hub's own receiver gasket first, then scans canister slots.
     *
     * @param be the hub block entity
     * @param gasketId the gasket UUID to resolve
     * @return the matching fluid handler, or null
     */
    @Nullable
    private static ResourceHandler<FluidResource> resolveHubGasket(HubBlockEntity be, UUID gasketId) {
        if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))) {
            return be.getFluidHandler();
        }
        return findSlotForGasket(be, HubBlockEntity.MAX_CANISTERS, gasketId);
    }

    /**
     * Registers GASKET_BLOCK for vat, tap, and plexer (simple gasket ID checks).
     *
     * @param event the capability registration event
     */
    static void registerSimpleGasketBlocks(RegisterCapabilitiesEvent event) {
        registerVatGasketBlock(event);
        registerTapGasketBlock(event);
        registerPlexerGasketBlock(event);
    }

    /**
     * Registers GASKET_BLOCK for vat: matches receiver or transmitter gasket.
     *
     * @param event the capability registration event
     */
    private static void registerVatGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.VAT.get(), (be, gasketId) -> {
                if (gasketId.equals(be.getGasketId(GasketRole.RECEIVER))
                        || gasketId.equals(be.getGasketId(GasketRole.TRANSMITTER))) {
                    return be.getFluidHandler();
                }
                return null;
            });
    }

    /**
     * Registers GASKET_BLOCK for tap (stub - taps drip, not receive).
     *
     * @param event the capability registration event
     */
    private static void registerTapGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.TAP.get(), (be, gasketId) -> null);
    }

    /**
     * Registers GASKET_BLOCK for plexer (stub - fluid routing TBD).
     *
     * @param event the capability registration event
     */
    private static void registerPlexerGasketBlock(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(GooCapabilities.GASKET_BLOCK,
            GooBlockEntities.PLEXER.get(), (be, gasketId) -> null);
    }

    /**
     * Registers GASKET_ENTITY capability for player inventory canister scanning.
     *
     * @param event the capability registration event
     */
    static void registerGasketEntityCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(GooCapabilities.GASKET_ENTITY,
            net.minecraft.world.entity.EntityType.PLAYER,
            (player, gasketId) -> findPlayerCanisterForGasket(player, gasketId));
    }

    /**
     * Scans canister slots in a slotted container for a matching gasket UUID.
     *
     * @param container the slotted goo container (canister shelf or hub)
     * @param slotCount the number of slots to scan
     * @param gasketId the gasket UUID to match
     * @return the slot's fluid handler if found, or null
     */
    @Nullable
    private static GooFluidHandler findSlotForGasket(
            ISlottedGooContainer container, int slotCount, UUID gasketId) {
        for (int i = 0; i < slotCount; i++) {
            if (container.getCanister(i).isEmpty()) { continue; }
            CanisterMetadata meta = container.getSlotMetadata(i);
            if (gasketMatchesSlot(gasketId, meta)) {
                return container.containerState().getSlotFluidHandler(i);
            }
        }
        return null;
    }

    /**
     * Returns true if the gasket UUID matches either gasket on a canister's metadata.
     *
     * @param gasketId the gasket UUID to test
     * @param meta the canister metadata
     * @return true if either top or bottom gasket matches
     */
    private static boolean gasketMatchesSlot(UUID gasketId, CanisterMetadata meta) {
        return gasketId.equals(meta.topGasketId())
                || gasketId.equals(meta.bottomGasketId());
    }

    /**
     * Scans the player's inventory for a canister matching the given gasket UUID.
     *
     * @param player the player whose inventory to scan
     * @param gasketId the gasket UUID to match
     * @return a slot handler for the matching canister, or null
     */
    @Nullable
    private static PlayerInventorySlotHandler findPlayerCanisterForGasket(Player player, UUID gasketId) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(GooItems.CANISTER.get())) { continue; }
            if (gasketMatchesSlot(gasketId, CanisterItem.getMetadata(stack))) {
                return new PlayerInventorySlotHandler(player, i);
            }
        }
        return null;
    }
}
