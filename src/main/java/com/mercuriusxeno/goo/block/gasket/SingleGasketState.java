package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Single-role gasket state. Used by crucible (TRANSMITTER) and hub (RECEIVER).
 * Stores one UUID and one partner ref for the configured role.
 */
final class SingleGasketState extends GasketState {

    /**
     * NBT key for the gasket UUID.
     */
    private static final String TAG_GASKET_ID = "GasketId";
    /**
     * NBT key for the gasket partner.
     */
    private static final String TAG_PARTNER = "GasketPartner";

    private final GasketRole role;
    private final String label;
    private @Nullable UUID gasketId;
    private @Nullable GasketPartner partner;

    /**
     * Creates a single-role gasket state.
     *
     * @param role  the one supported role
     * @param label the face label returned for this role
     */
    SingleGasketState(GasketRole role, String label) {
        this.role = role;
        this.label = label;
    }

    @Override
    public @Nullable UUID getId(GasketRole role) {
        return role == this.role ? gasketId : null;
    }

    @Override
    public @Nullable UUID ensureId(GasketRole role, Runnable syncCallback) {
        if (role != this.role) {
            return null;
        }
        if (gasketId == null) {
            gasketId = UUID.randomUUID();
            syncCallback.run();
        }
        return gasketId;
    }

    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == this.role ? partner : null;
    }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner,
                           Runnable syncCallback) {
        if (role != this.role) {
            return;
        }
        this.partner = partner;
        syncCallback.run();
    }

    @Override
    public void setId(GasketRole role, @Nullable UUID id) {
        if (role == this.role) {
            gasketId = id;
        }
    }

    @Override
    public void clear(GasketRole role, Runnable syncCallback) {
        if (role != this.role) {
            return;
        }
        gasketId = null;
        partner = null;
        syncCallback.run();
    }

    @Override
    public boolean supportsRole(GasketRole role) {
        return role == this.role;
    }

    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return role == this.role ? label : null;
    }

    @Override
    public void save(ValueOutput output) {
        if (gasketId != null) {
            output.store(TAG_GASKET_ID, UUIDUtil.STRING_CODEC, gasketId);
        }
        if (partner != null) {
            output.store(TAG_PARTNER, GasketPartner.CODEC, partner);
        }
    }

    @Override
    public void load(ValueInput input) {
        gasketId = input.read(TAG_GASKET_ID, UUIDUtil.STRING_CODEC).orElse(null);
        partner = input.read(TAG_PARTNER, GasketPartner.CODEC).orElse(null);
    }
}
