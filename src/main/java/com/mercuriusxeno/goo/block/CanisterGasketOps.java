package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Gasket registration, deregistration, and chunk-forcing for CanisterBlockEntity.
 * Each canister slot can have top and bottom gasket UUIDs that register their
 * physical location in the GasketRegistry for the tuner link network.
 */
final class CanisterGasketOps {

    private CanisterGasketOps() {}

    /** Registers gasket locations for all occupied slots.
     *
     * @param be the canister block entity
     */
    static void registerAllGaskets(CanisterBlockEntity be) {
        SlottedCanisterState state = be.containerState();
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisters.get(i).isEmpty()) {
                registerSlotGaskets(be, i);
            }
        }
    }

    /**
     * Registers gasket locations for a slot's canister in the gasket registry.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void registerSlotGaskets(CanisterBlockEntity be, int slot) {
        if (be.gasketRegistryAccess == null || !(be.getLevel() instanceof ServerLevel serverLevel)) { return; }
        ItemStack stack = be.containerState().canisters.get(slot);
        if (stack.isEmpty()) { return; }
        registerBothFaces(be, slot, CanisterItem.getMetadata(stack), serverLevel);
    }

    /**
     * Registers top and bottom gasket face locations for a single canister slot.
     * @param be the canister block entity
     * @param slot the slot index
     * @param meta the canister slot metadata containing gasket UUIDs
     * @param serverLevel the server level for dimension key lookup
     */
    private static void registerBothFaces(CanisterBlockEntity be, int slot,
            CanisterMetadata meta, ServerLevel serverLevel) {
        GasketRegistry registry = be.gasketRegistryAccess.get();
        ResourceKey<Level> dimension = serverLevel.dimension();
        registerFace(registry, meta.topGasketId(),
            new GasketLocation(dimension, be.getBlockPos(), true, slot));
        registerFace(registry, meta.bottomGasketId(),
            new GasketLocation(dimension, be.getBlockPos(), false, slot));
    }

    /**
     * Registers a single gasket face location if the UUID is present.
     *
     * @param registry the gasket registry
     * @param id       the gasket UUID, or null to skip
     * @param location the location to register
     */
    private static void registerFace(GasketRegistry registry, @Nullable UUID id, GasketLocation location) {
        if (id != null) { registry.updateLocation(id, location); }
    }

    /**
     * Deregisters gasket locations for a specific slot.
     *
     * @param be   the canister block entity
     * @param slot the slot index
     */
    static void deregisterSlotGaskets(CanisterBlockEntity be, int slot) {
        if (be.gasketRegistryAccess == null) { return; }
        ItemStack stack = be.containerState().canisters.get(slot);
        if (stack.isEmpty()) { return; }
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        GasketRegistry registry = be.gasketRegistryAccess.get();
        deregisterFace(registry, meta.topGasketId());
        deregisterFace(registry, meta.bottomGasketId());
    }

    /**
     * Clears the location of a single gasket face if the UUID is present.
     *
     * @param registry the gasket registry
     * @param id       the gasket UUID, or null to skip
     */
    private static void deregisterFace(GasketRegistry registry, @Nullable UUID id) {
        if (id != null) { registry.updateLocation(id, null); }
    }

    /** Deregisters all gasket locations (block broken or removed).
     *
     * @param be the canister block entity
     */
    static void deregisterAllGaskets(CanisterBlockEntity be) {
        SlottedCanisterState state = be.containerState();
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            if (!state.canisters.get(i).isEmpty()) {
                deregisterSlotGaskets(be, i);
            }
        }
    }

    /**
     * Forces the chunk of each slot's transmitter partner (receiver-side bidirectional forcing).
     *
     * @param be          the canister block entity
     * @param serverLevel the server level
     */
    static void forceAllTransmitterChunks(CanisterBlockEntity be, ServerLevel serverLevel) {
        SlottedCanisterState state = be.containerState();
        for (int i = 0; i < CanisterBlockEntity.MAX_SLOTS; i++) {
            ItemStack stack = state.canisters.get(i);
            if (stack.isEmpty()) { continue; }
            CanisterMetadata meta = CanisterItem.getMetadata(stack);
            GasketPusher.forceTransmitterChunk(
                meta.topGasketId(), be.gasketRegistryAccess, serverLevel, be.getBlockPos());
        }
    }
}
