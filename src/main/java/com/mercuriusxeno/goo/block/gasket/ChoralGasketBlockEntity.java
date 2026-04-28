package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

/**
 * Block entity for the world-placed choral gasket. When waterlogged,
 * acts as an infinite water source pushing 1000^0.75 mB/tick through
 * the gasket network. No internal storage - the water source is the
 * water block itself.
 */
public class ChoralGasketBlockEntity extends BlockEntity implements IGasketHolder {

    /**
     * NBT face label for the gasket attachment.
     */
    private static final String TAG_GASKET = "gasket";

    /**
     * Infinite water source - always reports 1000 mB, never depletes.
     */
    private final InfiniteWaterSource waterSource = new InfiniteWaterSource();

    /**
     * Composed gasket integration: TRANSMITTER-only.
     */
    private final GasketAttachment gasket = GasketAttachment.single(this, GasketRole.TRANSMITTER, TAG_GASKET);

    /**
     * Pushes water to gasket partners. Constructed in the BE constructor so it can
     * see the attachment's stable callbacks; assigned final via constructor.
     */
    private final IGasketPusher gasketPusher;

    /**
     * Creates a choral gasket block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChoralGasketBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHORAL_GASKET.get(), pos, state);
        this.gasketPusher = new GasketPusher(
                waterSource,
                () -> gasket.state().getId(GasketRole.TRANSMITTER),
                () -> gasket.state().getPartner(GasketRole.TRANSMITTER),
                this::getLevel, this::getBlockPos,
                gasket.syncCallback(),
                () -> gasket.registryAccess() != null ? gasket.registryAccess().get() : null);
        gasket.rebuildPushers(gasketPusher::rebuildCache);
        gasket.afterLoad(this::forceTransmitterChunkOnLoad);
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

    /**
     * Returns the water source handler for capability exposure.
     *
     * @return the water source handler
     */
    public InfiniteWaterSource getWaterSource() {
        return waterSource;
    }

    @Override
    public GasketAttachment gasket() {
        return gasket;
    }

    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        gasket.onSetLevel(level);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        gasket.onLoad();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        gasket.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        gasket.loadAdditional(input);
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return gasket.getUpdateTag(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return gasket.getUpdatePacket();
    }

    /**
     * BE-side post-load action: force-load the transmitter's destination chunk so the
     * pusher can resolve partners on first tick.
     */
    private void forceTransmitterChunkOnLoad() {
        if (level instanceof ServerLevel serverLevel) {
            GasketPusher.forceTransmitterChunk(
                    gasket.state().getId(GasketRole.TRANSMITTER),
                    gasket.registryAccess(),
                    serverLevel,
                    worldPosition);
        }
    }
}
