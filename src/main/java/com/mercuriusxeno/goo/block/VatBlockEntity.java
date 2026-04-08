package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GasketPartner;
import com.mercuriusxeno.goo.item.GasketRegionResolver;
import com.mercuriusxeno.goo.item.GasketRole;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Block entity for the Vat. Multi-type bulk goo storage with compression
 * enchantment scaling. Base capacity 2^25 mB, doubled per compression level
 * up to 5 (2^30 mB max).
 *
 * <p>Internal storage uses {@link GooFluidHandler} for NeoForge Transfer API
 * compatibility. Pipe mods can insert/extract via {@code Capabilities.Fluid.BLOCK}
 * on the top (input) and bottom (output) faces.</p>
 */
public class VatBlockEntity extends BlockEntity implements ISlottedGooContainer, IGasketHolder, IGooReservoir {

    /** Vat is a single-slot container; only slot 0 is valid. */
    private static final int SOLE_SLOT = 0;
    /** Sentinel value indicating an invalid NBT ordinal. */
    private static final int INVALID_ORDINAL = -1;

    // --- NBT tag keys ---
    /** NBT key for the vat face label: cap or base. */
    private static final String TAG_CAP = "cap";
    /** NBT key for the vat face label: base. */
    private static final String TAG_BASE = "base";
    /** NBT key for compression level. */
    private static final String TAG_COMPRESSION = "Compression";
    /** Legacy NBT key for compression level. */
    private static final String TAG_MATRICES = "Matrices";
    /** NBT key for goo contents. */
    private static final String TAG_CONTENTS = "Contents";
    /** NBT key for the player-assigned label. */
    private static final String TAG_LABEL = "Label";
    /** NBT key for stream goo type ordinal. */
    private static final String TAG_STREAM_TYPE = "StreamType";
    /** NBT key for stream transfer rate. */
    private static final String TAG_STREAM_RATE = "StreamRate";
    /** NBT key for stream start tick. */
    private static final String TAG_STREAM_TICK = "StreamTick";
    /** NBT key for cap gasket UUID. */
    private static final String TAG_CAP_GASKET_ID = "CapGasketId";
    /** NBT key for base gasket UUID. */
    private static final String TAG_BASE_GASKET_ID = "BaseGasketId";
    /** NBT key for cap gasket partner. */
    private static final String TAG_CAP_PARTNER = "CapPartner";
    /** NBT key for base gasket partner. */
    private static final String TAG_BASE_PARTNER = "BasePartner";

    private final GooFluidHandler fluidHandler = new GooFluidHandler(
        (int) ContainerCapacity.vatCapacity(0), this::onFluidChanged,
        () -> level != null ? level.getGameTime() : 0L);
    private int compressionLevel;
    private @Nullable UUID capGasketId;
    private @Nullable UUID baseGasketId;
    private @Nullable String label;
    private @Nullable GasketPartner capPartner;
    private @Nullable GasketPartner basePartner;

    /** Decoupled registry access, captured in setLevel to avoid direct GasketRegistry.get calls. */
    private @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /**
     * Re-entrance guard set by {@link VatStackRedistributor} while it is
     * bulk-loading fluid across the stack. Prevents onFluidChanged from
     * triggering another redistribution cycle.
     */
    boolean redistributing;

    // --- Stream state (synced to client for BER rendering) ---
    private @Nullable GooType vatStreamType;
    private int vatStreamRate;
    private long vatStreamTick;

    /** Pushes reservoir goo to the base gasket partner on a timed interval. */
    private final IGasketPusher gasketPusher = new GasketPusher(
        fluidHandler, () -> baseGasketId, () -> basePartner,
        this::getLevel, this::getBlockPos, this::markDirtyAndSync,
        () -> gasketRegistryAccess.get());

    /** Creates a new vat block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public VatBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VAT.get(), pos, state);
    }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VatBlockEntity be) {
        be.gasketPusher.tick();
    }

    /** Vats don't hold canister items; always returns EMPTY.
     *
     * @param slot the slot index
     * @return the canister
     */
    @Override
    public ItemStack getCanister(int slot) {
        return ItemStack.EMPTY;
    }

    /** {@inheritDoc} */
    @Override
    public void onSlotChanged() {
        markDirtyAndSync();
    }

    /** Returns the current capacity based on compression level.
     *
     * @return the capacity
     */
    public long getCapacity() {
        return ContainerCapacity.vatCapacity(compressionLevel);
    }

    /** Returns the current goo contents as an immutable snapshot.
     *
     * @return the contents
     */
    public GooContents getContents() {
        return fluidHandler.toGooContents();
    }

    /** {@inheritDoc}
     *
     * @return the reservoir
     */
    @Override
    public GooContents getReservoir() { return getContents(); }

    /** Returns the fluid handler for Transfer API capability registration.
     *
     * @return the fluid handler
     */
    public GooFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    /** Returns the compression enchantment level (0-5).
     *
     * @return the compression level
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /** Returns true if the vat has room for more goo.
     *
     * @return true if accept
     */
    public boolean canAccept() {
        return fluidHandler.totalVolume() < getCapacity();
    }

    /** {@inheritDoc} Delegates to {@link #canAccept()} for slot 0.
     *
     * @param slot the slot index
     * @return true if accept
     */
    @Override
    public boolean canAccept(int slot) {
        return slot == SOLE_SLOT && canAccept();
    }

    /** {@inheritDoc} Caps at vat capacity.
     *
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the long value
     */
    @Override
    public long insertGoo(GooType type, long volume) {
        int clamped = (int) Math.min(volume, Integer.MAX_VALUE);
        return fluidHandler.insertGoo(type, clamped, false);
    }

    /** {@inheritDoc} Delegates to {@link #insertGoo(GooType, long)} for slot 0.
     *
     * @param slot         the slot index
     * @param incomingType the goo type to insert
     * @param volume       volume in microblobs
     * @return the long value
     */
    @Override
    public long insertGoo(int slot, GooType incomingType, long volume) {
        return slot == SOLE_SLOT ? insertGoo(incomingType, volume) : 0L;
    }

    /** {@inheritDoc}
     *
     * @param type      the goo type
     * @param requested volume in microblobs to extract
     * @return the long value
     */
    @Override
    public long extractGoo(GooType type, long requested) {
        int clamped = (int) Math.min(requested, Integer.MAX_VALUE);
        return fluidHandler.extractGoo(type, clamped, false);
    }

    /** {@inheritDoc} Delegates to {@link #extractGoo(GooType, long)} for slot 0.
     *
     * @param slot      the slot index
     * @param type      the goo type
     * @param requested volume in microblobs to extract
     * @return the long value
     */
    @Override
    public long extractGoo(int slot, GooType type, long requested) {
        return slot == SOLE_SLOT ? extractGoo(type, requested) : 0L;
    }

    // --- ISlottedGooContainer metadata ---

    /** {@inheritDoc} Returns metadata synthesized from vat state for slot 0.
     *
     * @param slot the slot index
     * @return the slot metadata
     */
    @Override
    public CanisterMetadata getSlotMetadata(int slot) {
        if (slot != SOLE_SLOT) { return CanisterMetadata.EMPTY; }
        return new CanisterMetadata(
            capGasketId, baseGasketId, label, capPartner, basePartner);
    }

    /** {@inheritDoc} Applies metadata fields back to vat state for slot 0.
     *
     * @param slot     the slot index
     * @param metadata the canister metadata to apply
     */
    @Override
    public void setSlotMetadata(int slot, CanisterMetadata metadata) {
        if (slot != SOLE_SLOT) { return; }
        applyMetadata(metadata);
        syncCapacity();
        markDirtyAndSync();
    }

    /** Unpacks metadata fields into vat state.
     *
     * @param metadata the canister metadata to apply
     */
    private void applyMetadata(CanisterMetadata metadata) {
        capGasketId = metadata.topGasketId();
        baseGasketId = metadata.bottomGasketId();
        label = metadata.label();
        capPartner = metadata.topPartner();
        basePartner = metadata.bottomPartner();
    }

    /** {@inheritDoc} Returns vat contents for slot 0.
     *
     * @param slot the slot index
     * @return the slot goo contents
     */
    @Override
    public GooContents getSlotGooContents(int slot) {
        return slot == SOLE_SLOT ? getContents() : GooContents.EMPTY;
    }


    /** Updates the fluid handler's capacity to match the current matrix count. */
    private void syncCapacity() {
        fluidHandler.setCapacity((int) getCapacity());
    }

    /** Returns the dominant goo type (largest volume), or null if empty.
     *
     * @return the dominant type
     */
    @Nullable
    public GooType getDominantType() {
        return fluidHandler.largestType();
    }

    /** Returns true if the vat contains no goo.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return fluidHandler.isEmpty();
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Delegates to {@link GasketRegionResolver#resolveVatRole}.
     *
     * @param hit the ray trace hit result
     * @return the resolved gasket role
     */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        double localY = hit.getLocation().y - getBlockPos().getY();
        return GasketRegionResolver.resolveVatRole(hit.getDirection(), localY);
    }

    /** {@inheritDoc} Checks blockstate for GASKET_CAP/GASKET_BASE presence.
     *
     * @param role the gasket role
     * @return true if the condition is met
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        BlockState state = getBlockState();
        return role == GasketRole.RECEIVER
            ? state.getValue(VatBlock.GASKET_CAP)
            : state.getValue(VatBlock.GASKET_BASE);
    }

    /** {@inheritDoc} Returns "cap" for RECEIVER, "base" for TRANSMITTER.
     *
     * @param role the gasket role
     * @return the face label
     */
    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return role == GasketRole.RECEIVER ? TAG_CAP : TAG_BASE;
    }

    /** {@inheritDoc} Returns the vat's player-assigned label.
     *
     * @param slot the slot index
     * @return the machine label
     */
    @Override
    public @Nullable String getMachineLabel(int slot) {
        return label;
    }

    // -- IGasketHolder (cap = RECEIVER, base = TRANSMITTER) --

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the gasket id
     */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? capGasketId : baseGasketId;
    }

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the UUID, or null
     */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER
            ? ensureOrGenerate(capGasketId, id -> capGasketId = id)
            : ensureOrGenerate(baseGasketId, id -> baseGasketId = id);
    }

    /** Returns the existing UUID or generates a new one, syncing if created.
     *
     * @param current the current value
     * @param setter  the UUID setter callback
     * @return the UUID, or null
     */
    private UUID ensureOrGenerate(@Nullable UUID current, Consumer<UUID> setter) {
        if (current != null) { return current; }
        UUID generated = UUID.randomUUID();
        setter.accept(generated);
        markDirtyAndSync();
        return generated;
    }

    /** {@inheritDoc}
     *
     * @param role the gasket role
     * @return the partner
     */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? capPartner : basePartner;
    }

    /** {@inheritDoc}
     *
     * @param role    the gasket role
     * @param partner the gasket partner, or null to clear
     */
    @Override
    public void setPartner(GasketRole role, @Nullable GasketPartner partner) {
        if (role == GasketRole.RECEIVER) {
            capPartner = partner;
        } else {
            basePartner = partner;
            gasketPusher.rebuildCache();
        }
        markDirtyAndSync();
    }

    /** Clears the gasket UUID and partner for the given role.
     *
     * @param role the gasket role
     */
    @Override
    public void clearGasket(GasketRole role) {
        if (role == GasketRole.RECEIVER) {
            capGasketId = null;
            capPartner = null;
        } else {
            baseGasketId = null;
            basePartner = null;
            gasketPusher.rebuildCache();
        }
        markDirtyAndSync();
    }

    // -- Label --

    /** Returns the player-assigned label, or null if unnamed.
     *
     * @return the label
     */
    @Nullable
    public String getLabel() {
        return label;
    }

    /** Sets the player-assigned label (null to clear).
     *
     * @param label the player-assigned label, or null
     */
    public void setLabel(@Nullable String label) {
        this.label = label;
        markDirtyAndSync();
    }

    /** Snapshots stream state from the handler, syncs to clients, and redistributes if stacked. */
    private void onFluidChanged() {
        long tick = level != null ? level.getGameTime() : 0L;
        vatStreamType = fluidHandler.getStreamType(tick);
        vatStreamRate = fluidHandler.getStreamRate(tick);
        vatStreamTick = tick;
        markDirtyAndSync();
        if (!redistributing && level != null && !level.isClientSide() && isInStack()) {
            VatStackRedistributor.redistribute(level, worldPosition);
        }
    }

    /** Returns true if this vat is vertically connected to at least one other vat.
     *
     * @return true if in stack
     */
    private boolean isInStack() {
        BlockState state = getBlockState();
        return state.getValue(VatBlock.VAT_ABOVE) || state.getValue(VatBlock.VAT_BELOW);
    }

    /** Marks dirty and sends sync packet to tracking clients. */
    void markDirtyAndSync() {
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Called by {@link VatStackRedistributor} after bulk-loading new contents
     * via {@code loadFrom}. Skips stream tracking (redistribution is not a
     * visible stream) but still syncs to clients.
     */
    void syncAfterRedistribution() {
        markDirtyAndSync();
    }

    /** Returns the stream goo type, or null if no active stream.
     *
     * @param currentTick the current game tick
     * @return the vat stream type
     */
    public @Nullable GooType getVatStreamType(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamType : null;
    }

    /** Returns the stream rate in mB/tick, or 0 if no active stream.
     *
     * @param currentTick the current game tick
     * @return the vat stream rate
     */
    public int getVatStreamRate(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamRate : 0;
    }

    // --- Serialization ---

    /** Persists compression level, goo contents, label, and gasket state.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_COMPRESSION, compressionLevel);
        GooContents contents = fluidHandler.toGooContents();
        if (!contents.isEmpty()) {
            output.store(TAG_CONTENTS, GooContents.CODEC, contents);
        }
        if (label != null) {
            output.putString(TAG_LABEL, label);
        }
        saveGasketFields(output);
        if (vatStreamType != null) {
            output.putInt(TAG_STREAM_TYPE, vatStreamType.ordinal());
            output.putInt(TAG_STREAM_RATE, vatStreamRate);
            output.putLong(TAG_STREAM_TICK, vatStreamTick);
        }
    }

    /** Saves gasket UUIDs and partner references.
     *
     * @param output the value output to write to
     */
    private void saveGasketFields(ValueOutput output) {
        if (capGasketId != null) {
            output.store(TAG_CAP_GASKET_ID, UUIDUtil.STRING_CODEC, capGasketId);
        }
        if (baseGasketId != null) {
            output.store(TAG_BASE_GASKET_ID, UUIDUtil.STRING_CODEC, baseGasketId);
        }
        if (capPartner != null) {
            output.store(TAG_CAP_PARTNER, GasketPartner.CODEC, capPartner);
        }
        if (basePartner != null) {
            output.store(TAG_BASE_PARTNER, GasketPartner.CODEC, basePartner);
        }
    }

    /** Restores compression level, goo contents, label, and gasket state.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        compressionLevel = Math.max(0, Math.min(
                input.getIntOr(TAG_COMPRESSION, input.getIntOr(TAG_MATRICES, 0)),
                ContainerCapacity.MAX_COMPRESSION));
        syncCapacity();
        GooContents contents = input.read(TAG_CONTENTS, GooContents.CODEC)
            .orElse(GooContents.EMPTY);
        fluidHandler.loadFrom(contents);
        label = input.getString(TAG_LABEL).orElse(null);
        loadGasketFields(input);
        int streamOrdinal = input.getIntOr(TAG_STREAM_TYPE, INVALID_ORDINAL);
        GooType[] gooTypes = GooType.values();
        vatStreamType = streamOrdinal >= 0 && streamOrdinal < gooTypes.length ? gooTypes[streamOrdinal] : null;
        vatStreamRate = input.getIntOr(TAG_STREAM_RATE, 0);
        vatStreamTick = input.getLongOr(TAG_STREAM_TICK, 0);
    }

    /** Loads gasket UUIDs and partner references.
     *
     * @param input the value input to read from
     */
    private void loadGasketFields(ValueInput input) {
        capGasketId = input.read(TAG_CAP_GASKET_ID, UUIDUtil.STRING_CODEC).orElse(null);
        baseGasketId = input.read(TAG_BASE_GASKET_ID, UUIDUtil.STRING_CODEC).orElse(null);
        capPartner = input.read(TAG_CAP_PARTNER, GasketPartner.CODEC).orElse(null);
        basePartner = input.read(TAG_BASE_PARTNER, GasketPartner.CODEC).orElse(null);
    }

    /** Captures gasket registry access and rebuilds the push cache when the level is assigned.
     *
     * @param level the current level
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
        }
        gasketPusher.rebuildCache();
    }

    /** Syncs blockstate, rebuilds pusher cache (cascade), and forces transmitter chunk (bidirectional). */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            gasketPusher.rebuildCache();
            GasketPusher.forceTransmitterChunk(capGasketId, () -> GasketRegistry.get(serverLevel), serverLevel, worldPosition);
        }
    }

    // --- Item component bridge (enchantment preservation across place/break) ---

    /** Writes compression enchantment and goo contents to the item when the block is broken.
     *
     * @param builder the state definition builder
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        if (compressionLevel > 0 && level != null) {
            level.registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(reg -> reg.get(GooEnchantments.COMPRESSION))
                .ifPresent(holder -> {
                    ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                    mutable.set(holder, compressionLevel);
                    builder.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
                });
        }
        GooContents contents = fluidHandler.toGooContents();
        if (!contents.isEmpty()) {
            builder.set(com.mercuriusxeno.goo.registry.GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }

    /** Reads compression enchantment and goo contents from the placed item.
     *
     * @param getter the data component getter
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        ItemEnchantments enchants = getter.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchants.entrySet()) {
            if (entry.getKey().is(GooEnchantments.COMPRESSION)) {
                compressionLevel = entry.getIntValue();
                syncCapacity();
                break;
            }
        }
        GooContents contents = getter.getOrDefault(
            com.mercuriusxeno.goo.registry.GooDataComponents.GOO_CONTENTS.get(), GooContents.EMPTY);
        if (!contents.isEmpty()) {
            fluidHandler.loadFrom(contents);
        }
    }

    /** Returns full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Returns the sync packet sent when block entity data changes.
     *
     * @return the update packet
     */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
