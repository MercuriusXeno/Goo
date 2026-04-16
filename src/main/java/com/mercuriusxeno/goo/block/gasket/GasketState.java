package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Composed gasket field storage. Each variant owns the UUID and partner
 * fields for the roles it supports. BlockEntities hold one instance and
 * delegate IGasketHolder methods to it, removing 4-7 override methods
 * per class.
 *
 * <p>Three variants cover all machines:
 * <ul>
 *   <li>{@link SingleGasketState} - one role (crucible: TRANSMITTER, hub: RECEIVER)</li>
 *   <li>{@link DualGasketState} - two roles (vat: cap=RECEIVER, base=TRANSMITTER)</li>
 *   <li>{@link NullGasketState} - no machine-level gaskets (canister: slot-level only)</li>
 * </ul>
 */
public abstract sealed class GasketState
        permits SingleGasketState, DualGasketState, NullGasketState {

    /**
     * Returns the gasket UUID for the given role, or null if unsupported or unassigned.
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    public abstract @Nullable UUID getId(GasketRole role);

    /**
     * Ensures a UUID exists for the role, generating one if absent.
     * Returns null if this state doesn't support the role.
     *
     * @param role         the gasket role
     * @param syncCallback called when a new UUID is generated
     * @return the UUID, or null if unsupported
     */
    public abstract @Nullable UUID ensureId(GasketRole role, Runnable syncCallback);

    /**
     * Returns the partner for the given role, or null if unlinked or unsupported.
     *
     * @param role the gasket role
     * @return the partner, or null
     */
    public abstract @Nullable GasketPartner getPartner(GasketRole role);

    /**
     * Sets the partner for the given role. No-op if unsupported.
     *
     * @param role         the gasket role
     * @param partner      the partner, or null to clear
     * @param syncCallback called after the partner is updated
     */
    public abstract void setPartner(GasketRole role, @Nullable GasketPartner partner,
                                    Runnable syncCallback);

    /**
     * Directly sets the UUID for the given role without generating.
     * Used by VatBlockEntity's setSlotMetadata to restore specific IDs.
     * No-op if the role is unsupported.
     *
     * @param role the gasket role
     * @param id   the UUID to set, or null to clear
     */
    public abstract void setId(GasketRole role, @Nullable UUID id);

    /**
     * Clears the UUID and partner for the given role. No-op if unsupported.
     *
     * @param role         the gasket role
     * @param syncCallback called after clearing
     */
    public abstract void clear(GasketRole role, Runnable syncCallback);

    /**
     * Returns true if this state supports the given role.
     *
     * @param role the gasket role
     * @return true if supported
     */
    public abstract boolean supportsRole(GasketRole role);

    /**
     * Returns the human-readable face label for the role, or null.
     *
     * @param role the gasket role
     * @return the label, or null
     */
    public abstract @Nullable String getFaceLabel(GasketRole role);

    /**
     * Serializes gasket fields to NBT.
     *
     * @param output the value output
     */
    public abstract void save(ValueOutput output);

    /**
     * Deserializes gasket fields from NBT.
     *
     * @param input the value input
     */
    public abstract void load(ValueInput input);


    /**
     * Creates a state that supports no machine-level gaskets.
     * @return a no-op gasket state singleton
     */
    public static GasketState none() {
        return NullGasketState.INSTANCE;
    }

    /**
     * Creates a state that supports a single role.
     *
     * @param role  the supported role
     * @param label the face label for this role
     * @return a new single-role state
     */
    public static GasketState single(GasketRole role, String label) {
        return new SingleGasketState(role, label);
    }

    /**
     * Creates a state that supports both RECEIVER and TRANSMITTER.
     *
     * @param receiverLabel  face label for the receiver role (e.g. "cap")
     * @param transmitterLabel face label for the transmitter role (e.g. "base")
     * @return a new dual-role state
     */
    public static GasketState dual(String receiverLabel, String transmitterLabel) {
        return new DualGasketState(receiverLabel, transmitterLabel);
    }
}
