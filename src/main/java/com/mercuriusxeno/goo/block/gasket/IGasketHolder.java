package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Block entity that owns one or more gasket slots for the transport network.
 * Each slot is identified by {@link GasketRole}: RECEIVER (top/cap) or
 * TRANSMITTER (bottom/base). Machines expose only the roles they support.
 *
 * <p>Slot-aware overloads support multi-slot machines (canister grid, hub).
 * When the implementor also implements {@link ICanisterHolder} and
 * slot >= 0, the defaults route through the slot's {@link CanisterMetadata}.
 * Non-slotted machines (vat, crucible) inherit defaults that ignore the slot.</p>
 *
 * <p>Tuner dispatch methods let {@code ChoralTunerItem} resolve machine-specific
 * behavior polymorphically instead of instanceof branching. Machines override
 * the defaults that don't match their behavior.</p>
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface") // not a lambda target; sole abstract is a composed-state accessor
public interface IGasketHolder {

    /**
     * Sentinel: hitSlot returned a coordinate outside any slot.
     */
    int SLOT_MISS = Integer.MIN_VALUE;

    /**
     * Applies a partner to the correct side of a canister's metadata based on role.
     *
     * @param meta    the current metadata
     * @param role    the gasket role
     * @param partner the partner to set, or null to clear
     * @return the updated metadata
     */
    private static CanisterMetadata applyPartner(CanisterMetadata meta, GasketRole role,
                                                 @Nullable GasketPartner partner) {
        return role == GasketRole.RECEIVER ? meta.withTopPartner(partner) : meta.withBottomPartner(partner);
    }

    /**
     * Returns the composed gasket state that owns UUID and partner fields.
     * Each machine provides the appropriate variant (single, dual, or null).
     *
     * @return the gasket state
     */
    GasketState gasketState();

    /**
     * Returns the sync callback invoked after gasket state changes.
     * Override to supply a real callback (typically markDirtyAndSync).
     * The default no-ops.
     *
     * @return the sync callback
     */
    default Runnable gasketSyncCallback() {
        return () -> {
        };
    }

    /**
     * Returns the gasket UUID for the given role, or null if this machine
     * doesn't support that role or no gasket has been assigned yet.
     *
     * @param role the gasket role
     * @return the gasket id
     */
    default @Nullable UUID getGasketId(GasketRole role) {
        return gasketState().getId(role);
    }

    /**
     * Ensures a gasket UUID exists for the given role, generating one if absent.
     * Returns null if this machine doesn't support the given role.
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    default @Nullable UUID ensureGasketId(GasketRole role) {
        return gasketState().ensureId(role, gasketSyncCallback());
    }

    /**
     * Returns the linked partner for the given role, or null if unlinked
     * or the role is unsupported.
     *
     * @param role the gasket role
     * @return the partner
     */
    default @Nullable GasketPartner getPartner(GasketRole role) {
        return gasketState().getPartner(role);
    }

    /**
     * Sets the linked partner for the given role (null to clear).
     * No-op if the machine doesn't support the given role.
     *
     * @param role    the gasket role
     * @param partner the gasket partner, or null to clear
     */
    default void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        gasketState().setPartner(role, partner, gasketSyncCallback());
    }

    // --- Slot-aware overloads ---

    /**
     * Clears the gasket UUID and partner for the given role.
     *
     * @param role the gasket role
     */
    default void clearGasket(GasketRole role) {
        gasketState().clear(role, gasketSyncCallback());
    }

    /**
     * Returns the gasket UUID for the given role and slot. Routes through slot metadata when applicable.
     *
     * @param role the gasket role
     * @param slot the slot index
     * @return the gasket id
     */
    default @Nullable UUID getGasketId(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ICanisterHolder container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            return role == GasketRole.RECEIVER ? meta.topGasketId() : meta.bottomGasketId();
        }
        return getGasketId(role);
    }

    /**
     * Ensures a gasket UUID exists for the given role and slot, generating if absent.
     *
     * @param role the gasket role
     * @param slot the slot index
     * @return the UUID, or null
     */
    default @Nullable UUID ensureGasketId(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ICanisterHolder container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            CanisterMetadata ensured = meta.withGasketIds();
            if (ensured != meta) {
                container.setSlotMetadata(slot, ensured);
            }
            return role == GasketRole.RECEIVER ? ensured.topGasketId() : ensured.bottomGasketId();
        }
        return ensureGasketId(role);
    }

    /**
     * Returns the linked partner for the given role and slot.
     *
     * @param role the gasket role
     * @param slot the slot index
     * @return the partner
     */
    default @Nullable GasketPartner getPartner(GasketRole role, int slot) {
        if (slot >= 0 && this instanceof ICanisterHolder container) {
            CanisterMetadata meta = container.getSlotMetadata(slot);
            return role == GasketRole.RECEIVER ? meta.topPartner() : meta.bottomPartner();
        }
        return getPartner(role);
    }

    /**
     * Sets the linked partner for the given role and slot (null to clear).
     *
     * @param role    the gasket role
     * @param slot    the slot index
     * @param partner the gasket partner, or null to clear
     */
    default void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        if (slot >= 0 && this instanceof ICanisterHolder container) {
            CanisterMetadata updated = applyPartner(container.getSlotMetadata(slot), role, partner);
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
     *
     * @param hit the ray trace hit result
     * @return the integer value
     */
    default int resolveSlot(BlockHitResult hit) {
        return NO_SLOT;
    }

    /**
     * Resolves the gasket role from the hit location.
     * Default: vertical midpoint split (canister/hub pattern).
     * Vat and crucible override with machine-specific logic.
     *
     * @param hit the ray trace hit result
     * @return the resolved gasket role
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
     *
     * @param role the gasket role
     * @return true if the condition is met
     */
    default boolean supportsRole(GasketRole role) {
        return gasketState().supportsRole(role);
    }

    /**
     * Returns a human-readable label for the gasket face (e.g. "cap", "base", "crucible").
     * Delegates to gasketState by default.
     *
     * @param role the gasket role
     * @return the face label
     */
    default @Nullable String getFaceLabel(GasketRole role) {
        return gasketState().getFaceLabel(role);
    }

    /**
     * Returns the machine's label at the given slot, or null if unnamed.
     * Default: routes through {@link ICanisterHolder#getSlotMetadata(int)} for slotted machines.
     *
     * @param slot the slot index
     * @return the machine label
     */
    default @Nullable String getMachineLabel(int slot) {
        if (slot >= 0 && this instanceof ICanisterHolder container) {
            return container.getSlotMetadata(slot).label();
        }
        return null;
    }

    /**
     * Returns true if the given tuner owner is allowed to tune this machine.
     * Default: true (most machines are unowned).
     *
     * @param tunerOwner the UUID of the tuner's owner, or null if unowned
     * @return true if tuning
     */
    default boolean allowsTuning(@Nullable UUID tunerOwner) {
        return true;
    }

    /**
     * Returns true if this machine has a central intake gasket (hub only).
     *
     * @return true if intake
     */
    default boolean hasIntake() {
        return false;
    }
}
