package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRegionResolver;
import com.mercuriusxeno.goo.item.GasketRole;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Block entity that owns one or more gasket slots for the transport network.
 * Each slot is identified by {@link GasketRole}: RECEIVER (top/cap) or
 * TRANSMITTER (bottom/base). Machines expose only the roles they support.
 *
 * <p>Slot-aware overloads support multi-slot machines (canister grid, hub).
 * When the implementor also implements {@link ISlottedGooContainer} and
 * slot >= 0, the defaults route through the slot's {@link CanisterMetadata}.
 * Non-slotted machines (vat, crucible) inherit defaults that ignore the slot.</p>
 *
 * <p>Tuner dispatch methods let {@code ChoralTunerItem} resolve machine-specific
 * behavior polymorphically instead of instanceof branching. Machines override
 * the defaults that don't match their behavior.</p>
 */
public interface IGasketHolder {

    /** Sentinel: hitSlot returned a coordinate outside any slot. */
    int SLOT_MISS = Integer.MIN_VALUE;

    /**
     * Returns the gasket UUID for the given role, or null if this machine
     * doesn't support that role or no gasket has been assigned yet.
     */
    @Nullable UUID getGasketId(GasketRole role);

    /**
     * Ensures a gasket UUID exists for the given role, generating one if absent.
     * Returns null if this machine doesn't support the given role.
     */
    @Nullable UUID ensureGasketId(GasketRole role);

    /**
     * Returns the linked partner for the given role, or null if unlinked
     * or the role is unsupported.
     */
    @Nullable GasketPartner getPartner(GasketRole role);

    /**
     * Sets the linked partner for the given role (null to clear).
     * No-op if the machine doesn't support the given role.
     */
    void setPartner(GasketRole role, @Nullable GasketPartner partner);

    /**
     * Clears the gasket UUID and partner for the given role. Used when a
     * gasket is popped off due to mutual exclusivity with copper fittings.
     * Default no-op; implementors with removable gaskets should override.
     */
    default void clearGasket(GasketRole role) {
        setPartner(role, null);
    }

    // --- Slot-aware overloads ---

    /** Returns the gasket UUID for the given role and slot. Routes through slot metadata when applicable. */
    default @Nullable UUID getGasketId(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ISlottedGooContainer container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            return role == GasketRole.RECEIVER ? meta.topGasketId() : meta.bottomGasketId();
        }
        return getGasketId(role);
    }

    /** Ensures a gasket UUID exists for the given role and slot, generating if absent. */
    default @Nullable UUID ensureGasketId(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ISlottedGooContainer container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            CanisterMetadata ensured = meta.withGasketIds();
            if (ensured != meta) container.setSlotMetadata(slot, ensured);
            return role == GasketRole.RECEIVER ? ensured.topGasketId() : ensured.bottomGasketId();
        }
        return ensureGasketId(role);
    }

    /** Returns the linked partner for the given role and slot. */
    default @Nullable GasketPartner getPartner(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ISlottedGooContainer container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            return role == GasketRole.RECEIVER ? meta.topPartner() : meta.bottomPartner();
        }
        return getPartner(role);
    }

    /** Sets the linked partner for the given role and slot (null to clear). */
    default void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        if (slot >= 0 && this instanceof ISlottedGooContainer container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            CanisterMetadata updated = role == GasketRole.RECEIVER
                ? meta.withTopPartner(partner)
                : meta.withBottomPartner(partner);
            container.setSlotMetadata(slot, updated);
            return;
        }
        setPartner(role, partner);
    }

    // --- Tuner dispatch defaults ---

    /**
     * Resolves the targeted slot from the hit location.
     * Slotted machines (canister, hub) override to call their block's hitSlot.
     * Returns {@link GasketPartner#NO_SLOT} for non-slotted machines (vat, crucible).
     */
    default int resolveSlot(BlockHitResult hit) {
        return GasketPartner.NO_SLOT;
    }

    /**
     * Resolves the gasket role from the hit location.
     * Default: vertical midpoint split (canister/hub pattern).
     * Vat and crucible override with machine-specific logic.
     */
    default GasketRole resolveRole(BlockHitResult hit) {
        if (this instanceof BlockEntity be) {
            double localY = hit.getLocation().y - be.getBlockPos().getY();
            return GasketRegionResolver.resolveCanisterSlotRole(localY, 0.0, 1.0);
        }
        return GasketRole.TRANSMITTER;
    }

    /**
     * Returns true if this machine supports the given gasket role.
     * Default: true (canister/hub always have gaskets in both directions).
     */
    default boolean supportsRole(GasketRole role) { return true; }

    /**
     * Returns a human-readable label for the gasket face (e.g. "cap", "base", "crucible").
     * Default: null (canister/hub have no face labels).
     */
    default @Nullable String getFaceLabel(GasketRole role) { return null; }

    /**
     * Returns the machine's label at the given slot, or null if unnamed.
     * Default: routes through {@link ISlottedGooContainer#getSlotMetadata(int)} for slotted machines.
     */
    default @Nullable String getMachineLabel(int slot) {
        if (slot >= 0 && this instanceof ISlottedGooContainer container) {
            return container.getSlotMetadata(slot).label();
        }
        return null;
    }

    /**
     * Returns true if the given tuner owner is allowed to tune this machine.
     * Default: true (most machines are unowned).
     *
     * @param tunerOwner the UUID of the tuner's owner, or null if unowned
     */
    default boolean allowsTuning(@Nullable UUID tunerOwner) { return true; }

    /** Returns true if this machine has a central intake gasket (hub only). */
    default boolean hasIntake() { return false; }
}
