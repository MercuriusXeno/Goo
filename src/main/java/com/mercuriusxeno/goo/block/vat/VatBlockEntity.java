package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.fluid.GooFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.gasket.IGasketPusher;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
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
public class VatBlockEntity extends BlockEntity implements IGasketHolder {

    /**
     * Composed gasket integration: dual-role (RECEIVER cap, TRANSMITTER base) with a BE-level pusher.
     */
    private final GasketAttachment gasket = GasketAttachment.dual(this, "cap", "base");

    // Package-private fields accessed by VatSerialization, VatStackRedistributor.
    final GooFluidHandler fluidHandler = new GooFluidHandler(
            ContainerCapacity.vatCapacity(0), this::onFluidChanged,
            () -> level != null ? level.getGameTime() : 0);
    int compressionLevel;
    @Nullable String label;

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
     * Pushes reservoir goo to the base gasket partner on a timed interval.
     * Final, assigned in constructor.
     */
    final IGasketPusher gasketPusher;

    /**
     * Creates a new vat block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public VatBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VAT.get(), pos, state);
        this.gasketPusher = new GasketPusher(
                fluidHandler,
                () -> gasket.state().getId(GasketRole.TRANSMITTER),
                () -> gasket.state().getPartner(GasketRole.TRANSMITTER),
                this::getLevel, this::getBlockPos,
                gasket.syncCallback(),
                () -> gasket.registryAccess() != null ? gasket.registryAccess().get() : null);
        gasket.rebuildPushers(gasketPusher::rebuildCache);
        gasket.afterLoad(this::forceTransmitterChunkOnLoad);
    }

    private void forceTransmitterChunkOnLoad() {
        if (level instanceof ServerLevel serverLevel) {
            GasketPusher.forceTransmitterChunk(
                    gasket.state().getId(GasketRole.TRANSMITTER),
                    gasket.registryAccess(),
                    serverLevel,
                    worldPosition);
        }
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
    }

    /**
     * Returns the current capacity.
     *
     * @return the capacity
     */
    public int getCapacity() {
        return ContainerCapacity.vatCapacity(compressionLevel);
    }

    /**
     * Returns the current goo contents.
     *
     * @return the contents
     */
    public GooContents getContents() {
        return fluidHandler.toGooContents();
    }

    /**
     * Inserts goo of the given type into the reservoir.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertGoo(GooType type, int volume) {
        return fluidHandler.insertGoo(type, Math.min(volume, Integer.MAX_VALUE), false);
    }

    /**
     * Extracts up to the given amount of a specific goo type.
     *
     * @param type   the goo type
     * @param amount maximum volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(GooType type, int amount) {
        return fluidHandler.extractGoo(type, Math.min(amount, Integer.MAX_VALUE), false);
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

    @Override
    public GasketAttachment gasket() {
        return gasket;
    }

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

    @Override
    public @Nullable String getMachineLabel(int slot) {
        return label;
    }

    /**
     * Snapshots stream state, syncs to clients, and redistributes if stacked.
     */
    private void onFluidChanged() {
        VatSerialization.snapshotStream(this);
        markDirtyAndSync();
        VatSerialization.tryRedistribute(this);
    }

    /** Marks dirty and syncs to clients. Delegates to the gasket attachment. */
    void markDirtyAndSync() {
        gasket.syncToClients();
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

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        VatSerialization.saveFields(this, output);
        gasket.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        VatSerialization.loadFields(this, input);
        gasket.loadAdditional(input);
    }

    // --- Framework lifecycle ---

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        gasket.onSetLevel(level);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        gasket.onLoad();
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

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return gasket.getUpdateTag(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return gasket.getUpdatePacket();
    }
}
