package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Dual-role gasket state. Used by vat (RECEIVER=cap, TRANSMITTER=base).
 * Stores independent UUID and partner for each role.
 */
final class DualGasketState extends GasketState {

    /**
     * NBT key for cap (RECEIVER) gasket UUID.
     */
    private static final String TAG_CAP_ID = "CapGasketId";
    /**
     * NBT key for base (TRANSMITTER) gasket UUID.
     */
    private static final String TAG_BASE_ID = "BaseGasketId";
    /**
     * NBT key for cap partner.
     */
    private static final String TAG_CAP_PARTNER = "CapPartner";
    /**
     * NBT key for base partner.
     */
    private static final String TAG_BASE_PARTNER = "BasePartner";

    private final String receiverLabel;
    private final String transmitterLabel;

    private @Nullable UUID capId;
    private @Nullable UUID baseId;
    private @Nullable GasketPartner capPartner;
    private @Nullable GasketPartner basePartner;

    /**
     * Creates a dual-role gasket state.
     *
     * @param receiverLabel    label for the RECEIVER face (e.g. "cap")
     * @param transmitterLabel label for the TRANSMITTER face (e.g. "base")
     */
    DualGasketState(String receiverLabel, String transmitterLabel) {
        this.receiverLabel = receiverLabel;
        this.transmitterLabel = transmitterLabel;
    }

    @Override
    public @Nullable UUID getId(GasketRole role) {
        return role == GasketRole.RECEIVER ? capId : baseId;
    }

    @Override
    public @Nullable UUID ensureId(GasketRole role, Runnable syncCallback) {
        if (role == GasketRole.RECEIVER) {
            return ensureCapId(syncCallback);
        }
        return ensureBaseId(syncCallback);
    }

    /**
     * Lazily initializes and returns the cap (RECEIVER) gasket UUID.
     *
     * @param syncCallback called after generating a new UUID to persist the change
     * @return the cap gasket UUID (created if absent)
     */
    private UUID ensureCapId(Runnable syncCallback) {
        if (capId == null) {
            capId = UUID.randomUUID();
            syncCallback.run();
        }
        return capId;
    }

    /**
     * Lazily initializes and returns the base (TRANSMITTER) gasket UUID.
     *
     * @param syncCallback called after generating a new UUID to persist the change
     * @return the base gasket UUID (created if absent)
     */
    private UUID ensureBaseId(Runnable syncCallback) {
        if (baseId == null) {
            baseId = UUID.randomUUID();
            syncCallback.run();
        }
        return baseId;
    }

    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? capPartner : basePartner;
    }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner,
                           Runnable syncCallback) {
        if (role == GasketRole.RECEIVER) {
            capPartner = partner;
        } else {
            basePartner = partner;
        }
        syncCallback.run();
    }

    @Override
    public void setId(GasketRole role, @Nullable UUID id) {
        if (role == GasketRole.RECEIVER) {
            capId = id;
        } else {
            baseId = id;
        }
    }

    @Override
    public void clear(GasketRole role, Runnable syncCallback) {
        if (role == GasketRole.RECEIVER) {
            capId = null;
            capPartner = null;
        } else {
            baseId = null;
            basePartner = null;
        }
        syncCallback.run();
    }

    @Override
    public boolean supportsRole(GasketRole role) {
        return true;
    }

    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return role == GasketRole.RECEIVER ? receiverLabel : transmitterLabel;
    }

    @Override
    public void save(ValueOutput output) {
        if (capId != null) {
            output.store(TAG_CAP_ID, UUIDUtil.STRING_CODEC, capId);
        }
        if (baseId != null) {
            output.store(TAG_BASE_ID, UUIDUtil.STRING_CODEC, baseId);
        }
        if (capPartner != null) {
            output.store(TAG_CAP_PARTNER, GasketPartner.CODEC, capPartner);
        }
        if (basePartner != null) {
            output.store(TAG_BASE_PARTNER, GasketPartner.CODEC, basePartner);
        }
    }

    @Override
    public void load(ValueInput input) {
        capId = input.read(TAG_CAP_ID, UUIDUtil.STRING_CODEC).orElse(null);
        baseId = input.read(TAG_BASE_ID, UUIDUtil.STRING_CODEC).orElse(null);
        capPartner = input.read(TAG_CAP_PARTNER, GasketPartner.CODEC).orElse(null);
        basePartner = input.read(TAG_BASE_PARTNER, GasketPartner.CODEC).orElse(null);
    }
}
