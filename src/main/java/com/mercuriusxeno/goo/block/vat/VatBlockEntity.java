package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.IGooReservoir;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Block entity for the Vat. Multi-type bulk goo storage with compression
 * enchantment scaling. Base capacity 2^25 mB, doubled per compression level
 * up to 5 (2^30 mB max).
 *
 * <p>Internal logic delegated to:
 * {@link VatSerialization} (NBT + data components),
 * {@link VatGasketOps} (gasket face resolution, stacking, drops).
 * Gasket field storage owned by {@link GasketState#dual}.</p>
 */
public class VatBlockEntity extends BlockEntity implements IGasketHolder, IGooReservoir {

    /**
     * Composed gasket state for dual roles: cap (RECEIVER) and base (TRANSMITTER).
     */
    private final GasketState gasketState = GasketState.dual("cap", "base");    // Package-private fields accessed by VatSerialization, VatStackRedistributor.
    final GooFluidHandler fluidHandler = new GooFluidHandler(
            ContainerCapacity.vatCapacity(0), this::onFluidChanged,
            () -> level != null ? level.getGameTime() : 0);
    int compressionLevel;
    @Nullable String label;
    @Nullable IGasketRegistryAccess gasketRegistryAccess;
    /**
     * Re-entrance guard for {@link VatStackRedistributor}.
     */
    boolean redistributing;
    // --- Stream state (synced to client for BER rendering) ---
    // Package-private: accessed by VatSerialization for snapshot and save/load.
    @Nullable GooType vatStreamType;
    int vatStreamRate;
    long vatStreamTick;
    /**
     * Creates a new vat block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public VatBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VAT.get(), pos, state);
    }

    /**
     * Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VatBlockEntity be) {
        be.gasketPusher.tick();
    }    /**
     * Pushes reservoir goo to the base gasket partner on a timed interval.
     */
    @SuppressWarnings("PMD.LambdaCanBeMethodReference") // field is null at construction; lambda defers read
    final IGasketPusher gasketPusher = new GasketPusher(
            fluidHandler, () -> gasketState.getId(GasketRole.TRANSMITTER),
            () -> gasketState.getPartner(GasketRole.TRANSMITTER),
            this::getLevel, this::getBlockPos, this::markDirtyAndSync,
            () -> gasketRegistryAccess.get());

    /**
     * {@inheritDoc}
     */
    @Override
    public GooFluidHandler reservoirHandler() {
        return fluidHandler;
    }

    /**
     * Returns the current capacity.
     *
     * @return the capacity
     */
    public int getCapacity() {
        return ContainerCapacity.vatCapacity(compressionLevel);
    }

    // --- IGooReservoir ---

    /**
     * Returns the current goo contents.
     *
     * @return the contents
     */
    public GooContents getContents() {
        return fluidHandler.toGooContents();
    }

    // --- Public accessors ---

    /**
     * Returns the fluid handler for Transfer API.
     *
     * @return the fluid handler
     */
    public GooFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    /**
     * Returns the compression level (0-5).
     *
     * @return the compression level
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Returns true if the vat has room for more goo.
     *
     * @return true if accept
     */
    public boolean canAccept() {
        return fluidHandler.totalVolume() < getCapacity();
    }

    /**
     * Returns the dominant goo type, or null.
     *
     * @return the dominant type
     */
    @Nullable
    public GooType getDominantType() {
        return fluidHandler.largestType();
    }

    /**
     * Returns true if the vat contains no goo.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return fluidHandler.isEmpty();
    }

    /**
     * Returns the player-assigned label, or null.
     *
     * @return the label
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /**
     * Sets the player-assigned label (null to clear).
     *
     * @param label the label
     */
    public void setLabel(@Nullable String label) {
        this.label = label;
        markDirtyAndSync();
    }

    /**
     * Returns the stream goo type, or null if no active stream.
     *
     * @param currentTick the current game tick
     * @return the vat stream type
     */
    public @Nullable GooType getVatStreamType(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamType : null;
    }

    /**
     * Returns the stream rate in mB/tick, or 0.
     *
     * @param currentTick the current game tick
     * @return the vat stream rate
     */
    public int getVatStreamRate(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamRate : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GasketState gasketState() {
        return gasketState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        double localY = hit.getLocation().y - getBlockPos().getY();
        return GasketRegionResolver.resolveVatRole(hit.getDirection(), localY);
    }

    // --- IGasketHolder ---

    /**
     * {@inheritDoc} Checks per-role blockstate properties.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        BlockState state = getBlockState();
        return role == GasketRole.RECEIVER
                ? state.getValue(VatBlock.GASKET_CAP)
                : state.getValue(VatBlock.GASKET_BASE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getMachineLabel(int slot) {
        return label;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable gasketSyncCallback() {
        return this::markDirtyAndSync;
    }

    /**
     * {@inheritDoc} Rebuilds pusher cache for TRANSMITTER role.
     */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        gasketState.setPartner(role, partner, () -> {
            if (role == GasketRole.TRANSMITTER) {
                gasketPusher.rebuildCache();
            }
            markDirtyAndSync();
        });
    }

    /**
     * {@inheritDoc} Rebuilds pusher cache for TRANSMITTER role.
     */
    @Override
    public void clearGasket(GasketRole role) {
        gasketState.clear(role, () -> {
            if (role == GasketRole.TRANSMITTER) {
                gasketPusher.rebuildCache();
            }
            markDirtyAndSync();
        });
    }

    /**
     * Snapshots stream state, syncs to clients, and redistributes if stacked.
     */
    private void onFluidChanged() {
        VatSerialization.snapshotStream(this);
        markDirtyAndSync();
        VatSerialization.tryRedistribute(this);
    }

    /**
     * Marks dirty and sends sync packet to tracking clients.
     */
    void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    // --- Internal ---

    /**
     * Called by {@link VatStackRedistributor} after bulk-loading.
     */
    void syncAfterRedistribution() {
        markDirtyAndSync();
    }

    /**
     * Updates the fluid handler's capacity to match the current compression level.
     */
    void syncCapacity() {
        fluidHandler.setCapacity(getCapacity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        VatSerialization.saveFields(this, output);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        VatSerialization.loadFields(this, input);
    }

    // --- Framework lifecycle ---

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
        }
        gasketPusher.rebuildCache();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            gasketPusher.rebuildCache();
            GasketPusher.forceTransmitterChunk(
                    gasketState.getId(GasketRole.TRANSMITTER),
                    () -> GasketRegistry.get(serverLevel), serverLevel, worldPosition);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        VatSerialization.collectCompression(builder, compressionLevel, level);
        VatSerialization.collectContents(builder, fluidHandler.toGooContents());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        int applied = VatSerialization.applyCompression(getter);
        if (applied > 0) {
            compressionLevel = applied;
            syncCapacity();
        }
        GooContents contents = VatSerialization.applyContents(getter);
        if (!contents.isEmpty()) {
            fluidHandler.loadFrom(contents);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }




}
