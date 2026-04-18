package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Block entity for the world-placed choral gasket. When waterlogged,
 * acts as an infinite water source pushing 1000^0.75 mB/tick through
 * the gasket network. No internal storage - the water source is the
 * water block itself.
 */
public class ChoralGasketBlockEntity extends BlockEntity implements IGasketHolder {

    /** NBT face label for the gasket state. */
    private static final String TAG_GASKET = "gasket";

    /** Infinite water source - always reports 1000 mB, never depletes. */
    private final InfiniteWaterSource waterSource = new InfiniteWaterSource();

    /** Gasket state: transmitter only. */
    private final GasketState gasketState =
            GasketState.single(GasketRole.TRANSMITTER, TAG_GASKET);

    /** Registry access, captured in setLevel. */
    @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Pushes water to gasket partners. */
    @SuppressWarnings("PMD.LambdaCanBeMethodReference")
    final IGasketPusher gasketPusher = new GasketPusher(
            waterSource, () -> gasketState.getId(GasketRole.TRANSMITTER),
            () -> gasketState.getPartner(GasketRole.TRANSMITTER),
            this::getLevel, this::getBlockPos,
            this::syncToClients, () -> gasketRegistryAccess.get());

    /**
     * Creates a choral gasket block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChoralGasketBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHORAL_GASKET.get(), pos, state);
    }

    /**
     * Returns the water source handler for capability exposure.
     *
     * @return the water source handler
     */
    public InfiniteWaterSource getWaterSource() { return waterSource; }

    @Override
    public GasketState gasketState() { return gasketState; }

    @Override
    public Runnable gasketSyncCallback() { return this::syncToClients; }

    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        gasketState.setPartner(role, partner, () -> {
            gasketPusher.rebuildCache();
            syncToClients();
        });
    }

    @Override
    public void clearGasket(GasketRole role) {
        gasketState.clear(role, () -> {
            gasketPusher.rebuildCache();
            syncToClients();
        });
    }

    /**
     * Server tick: push water through the network when waterlogged.
     *
     * @param level the server level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos,
            BlockState state, ChoralGasketBlockEntity be) {
        if (state.getValue(ChoralGasketBlock.WATERLOGGED)) {
            be.gasketPusher.tick();
        }
    }

    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
        }
        gasketPusher.rebuildCache();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        gasketPusher.rebuildCache();
        GasketPusher.forceTransmitterChunk(
                gasketState.getId(GasketRole.TRANSMITTER),
                () -> GasketRegistry.get(serverLevel), serverLevel, worldPosition);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        gasketState.save(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        gasketState.load(input);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(
            HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Marks dirty and syncs to clients. */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(),
                    getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
