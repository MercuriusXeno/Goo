package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * No machine-level gaskets. Used by canister (which has slot-level gaskets
 * stored on individual CanisterMetadata instead).
 */
final class NullGasketState extends GasketState {

    /**
     * Shared singleton - no mutable state.
     */
    static final NullGasketState INSTANCE = new NullGasketState();

    private NullGasketState() {
    }

    @Override
    public @Nullable UUID getId(GasketRole role) {
        return null;
    }

    @Override
    public @Nullable UUID ensureId(GasketRole role, Runnable syncCallback) {
        return null;
    }

    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return null;
    }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner,
                           Runnable syncCallback) { /* no-op */ }

    @Override
    public void setId(GasketRole role, @Nullable UUID id) { /* no-op */ }

    @Override
    public void clear(GasketRole role, Runnable syncCallback) { /* no-op */ }

    @Override
    public boolean supportsRole(GasketRole role) {
        return false;
    }

    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return null;
    }

    @Override
    public void save(ValueOutput output) { /* nothing to persist */ }

    @Override
    public void load(ValueInput input) { /* nothing to load */ }
}
