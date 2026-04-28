package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Stateful gasket integration packaged as a component a block entity owns at construction.
 * Encapsulates the gasket-protocol concern: registry access capture, partner/clear closures
 * with sync, NBT serialization slot, and the synced-BE packet machinery shared by every
 * gasket-capable BE. The owner BE forwards Minecraft lifecycle hooks (setLevel/onLoad/save/
 * load/getUpdatePacket/getUpdateTag) into here.
 *
 * <p>Pushers vary per BE (one BE-level pusher, or many slot-level pushers, or none). The
 * attachment exposes two callback hooks the BE wires at construction:
 * {@link #rebuildPushers(Runnable)} runs when registry access appears or partner topology
 * changes; {@link #afterLoad(Runnable)} runs after onLoad rebuild for chunk-forcing.</p>
 */
public final class GasketAttachment {

    private final BlockEntity owner;
    private final GasketState state;
    @Nullable private IGasketRegistryAccess registryAccess;
    private Runnable rebuildPushers = () -> { };
    private Runnable afterLoad = () -> { };

    private GasketAttachment(BlockEntity owner, GasketState state) {
        this.owner = owner;
        this.state = state;
    }

    /**
     * Single-role attachment (TRANSMITTER-only or RECEIVER-only machines).
     *
     * @param owner the block entity that owns this attachment
     * @param role  the supported role
     * @param label face label exposed by tuner display
     * @return a new attachment
     */
    public static GasketAttachment single(BlockEntity owner, GasketRole role, String label) {
        return new GasketAttachment(owner, GasketState.single(role, label));
    }

    /**
     * Dual-role attachment (machines with both RECEIVER cap and TRANSMITTER base).
     *
     * @param owner     the block entity that owns this attachment
     * @param capLabel  RECEIVER face label
     * @param baseLabel TRANSMITTER face label
     * @return a new attachment
     */
    public static GasketAttachment dual(BlockEntity owner, String capLabel, String baseLabel) {
        return new GasketAttachment(owner, GasketState.dual(capLabel, baseLabel));
    }

    /**
     * Roleless attachment for slotted machines that delegate role/UUID storage to per-slot
     * canister metadata rather than to the BE-level gasket state.
     *
     * @param owner the block entity that owns this attachment
     * @return a new attachment
     */
    public static GasketAttachment none(BlockEntity owner) {
        return new GasketAttachment(owner, GasketState.none());
    }

    /**
     * Wires the BE's pusher-rebuild logic. Invoked when registry access appears
     * (setLevel) and when partner topology changes (setPartner/clearGasket).
     *
     * @param hook the BE-side rebuild action
     */
    public void rebuildPushers(Runnable hook) {
        this.rebuildPushers = hook;
    }

    /**
     * Wires the BE's post-load action (typically forceTransmitterChunk).
     *
     * @param hook the BE-side post-load action
     */
    public void afterLoad(Runnable hook) {
        this.afterLoad = hook;
    }

    /**
     * @return the underlying gasket state
     */
    public GasketState state() {
        return state;
    }

    /**
     * @return the captured registry access, or null on the client
     */
    public @Nullable IGasketRegistryAccess registryAccess() {
        return registryAccess;
    }

    /**
     * @return a sync callback suitable for closures (e.g. pusher onChange)
     */
    public Runnable syncCallback() {
        return this::syncToClients;
    }

    /**
     * Marks the owner dirty and sends a block update to tracking clients.
     */
    public void syncToClients() {
        BlockEntitySync.markDirtyAndSync(owner);
    }

    /**
     * Sets the partner for the given role and triggers rebuild + sync.
     *
     * @param role    the gasket role
     * @param partner the partner, or null to clear
     */
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        state.setPartner(role, partner, () -> {
            rebuildPushers.run();
            syncToClients();
        });
    }

    /**
     * Clears the partner and UUID for the given role and triggers rebuild + sync.
     *
     * @param role the gasket role
     */
    public void clearGasket(GasketRole role) {
        state.clear(role, () -> {
            rebuildPushers.run();
            syncToClients();
        });
    }

    /**
     * Forwarded from the BE's {@code setLevel}. Captures registry access on server
     * and runs the BE-side rebuild hook.
     *
     * @param level the level being set
     */
    public void onSetLevel(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            registryAccess = () -> GasketRegistry.get(serverLevel);
        }
        rebuildPushers.run();
    }

    /**
     * Forwarded from the BE's {@code onLoad}. On server, rebuilds pushers and runs
     * the BE-side post-load action.
     */
    public void onLoad() {
        if (!(owner.getLevel() instanceof ServerLevel)) {
            return;
        }
        rebuildPushers.run();
        afterLoad.run();
    }

    /**
     * Forwarded from the BE's {@code saveAdditional}. Writes the gasket NBT slot.
     *
     * @param output the value output
     */
    public void saveAdditional(ValueOutput output) {
        state.save(output);
    }

    /**
     * Forwarded from the BE's {@code loadAdditional}. Reads the gasket NBT slot.
     *
     * @param input the value input
     */
    public void loadAdditional(ValueInput input) {
        state.load(input);
    }

    /**
     * Forwarded from the BE's {@code getUpdateTag}. Standard full-metadata snapshot.
     *
     * @param registries the holder lookup provider
     * @return the update tag
     */
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return owner.saveWithFullMetadata(registries);
    }

    /**
     * Forwarded from the BE's {@code getUpdatePacket}. Standard BE data packet.
     *
     * @return the update packet
     */
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(owner);
    }
}
