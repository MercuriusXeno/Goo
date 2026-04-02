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
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    private final GooFluidHandler fluidHandler = new GooFluidHandler(
        (int) ContainerCapacity.vatCapacity(0), this::onFluidChanged,
        () -> level != null ? level.getGameTime() : 0L);
    private int compressionLevel = 0;
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

    /** Creates a new vat block entity at the given position. */
    public VatBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VAT.get(), pos, state);
    }

    /** Static tick entrypoint for the block entity ticker. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, VatBlockEntity be) {
        be.gasketPusher.tick();
    }

    /** Vats don't hold canister items; always returns EMPTY. */
    @Override
    public ItemStack getCanister(int slot) {
        return ItemStack.EMPTY;
    }

    /** {@inheritDoc} */
    @Override
    public void onSlotChanged() {
        markDirtyAndSync();
    }

    /** Returns the current capacity based on compression level. */
    public long getCapacity() {
        return ContainerCapacity.vatCapacity(compressionLevel);
    }

    /** Returns the current goo contents as an immutable snapshot. */
    public GooContents getContents() {
        return fluidHandler.toGooContents();
    }

    /** {@inheritDoc} */
    @Override
    public GooContents getReservoir() { return getContents(); }

    /** Returns the fluid handler for Transfer API capability registration. */
    public GooFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    /** Returns the compression enchantment level (0-5). */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /** Returns true if the vat has room for more goo. */
    public boolean canAccept() {
        return fluidHandler.totalVolume() < getCapacity();
    }

    /** {@inheritDoc} Delegates to {@link #canAccept()} for slot 0. */
    @Override
    public boolean canAccept(int slot) {
        return slot == SOLE_SLOT && canAccept();
    }

    /** {@inheritDoc} Caps at vat capacity. */
    @Override
    public long insertGoo(GooType type, long volume) {
        int clamped = (int) Math.min(volume, Integer.MAX_VALUE);
        return fluidHandler.insertGoo(type, clamped, false);
    }

    /** {@inheritDoc} Delegates to {@link #insertGoo(GooType, long)} for slot 0. */
    @Override
    public long insertGoo(int slot, GooType incomingType, long volume) {
        return slot == SOLE_SLOT ? insertGoo(incomingType, volume) : 0L;
    }

    /** {@inheritDoc} */
    @Override
    public long extractGoo(GooType type, long requested) {
        int clamped = (int) Math.min(requested, Integer.MAX_VALUE);
        return fluidHandler.extractGoo(type, clamped, false);
    }

    /** {@inheritDoc} Delegates to {@link #extractGoo(GooType, long)} for slot 0. */
    @Override
    public long extractGoo(int slot, GooType type, long requested) {
        return slot == SOLE_SLOT ? extractGoo(type, requested) : 0L;
    }

    // --- ISlottedGooContainer metadata ---

    /** {@inheritDoc} Returns metadata synthesized from vat state for slot 0. */
    @Override
    public CanisterMetadata getSlotMetadata(int slot) {
        if (slot != SOLE_SLOT) return CanisterMetadata.EMPTY;
        return new CanisterMetadata(
            capGasketId, baseGasketId, label, capPartner, basePartner);
    }

    /** {@inheritDoc} Applies metadata fields back to vat state for slot 0. */
    @Override
    public void setSlotMetadata(int slot, CanisterMetadata metadata) {
        if (slot != SOLE_SLOT) return;
        applyMetadata(metadata);
        syncCapacity();
        markDirtyAndSync();
    }

    /** Unpacks metadata fields into vat state. */
    private void applyMetadata(CanisterMetadata metadata) {
        capGasketId = metadata.topGasketId();
        baseGasketId = metadata.bottomGasketId();
        label = metadata.label();
        capPartner = metadata.topPartner();
        basePartner = metadata.bottomPartner();
    }

    /** {@inheritDoc} Returns vat contents for slot 0. */
    @Override
    public GooContents getSlotGooContents(int slot) {
        return slot == SOLE_SLOT ? getContents() : GooContents.EMPTY;
    }


    /** Updates the fluid handler's capacity to match the current matrix count. */
    private void syncCapacity() {
        fluidHandler.setCapacity((int) getCapacity());
    }

    /** Returns the dominant goo type (largest volume), or null if empty. */
    @Nullable
    public GooType getDominantType() {
        return fluidHandler.largestType();
    }

    /** Returns true if the vat contains no goo. */
    public boolean isEmpty() {
        return fluidHandler.isEmpty();
    }

    // --- IGasketHolder tuner dispatch ---

    /** {@inheritDoc} Delegates to {@link GasketRegionResolver#resolveVatRole}. */
    @Override
    public GasketRole resolveRole(BlockHitResult hit) {
        double localY = hit.getLocation().y - getBlockPos().getY();
        return GasketRegionResolver.resolveVatRole(hit.getDirection(), localY);
    }

    /** {@inheritDoc} Checks blockstate for GASKET_CAP/GASKET_BASE presence. */
    @Override
    public boolean supportsRole(GasketRole role) {
        BlockState state = getBlockState();
        return role == GasketRole.RECEIVER
            ? state.getValue(VatBlock.GASKET_CAP)
            : state.getValue(VatBlock.GASKET_BASE);
    }

    /** {@inheritDoc} Returns "cap" for RECEIVER, "base" for TRANSMITTER. */
    @Override
    public @Nullable String getFaceLabel(GasketRole role) {
        return role == GasketRole.RECEIVER ? "cap" : "base";
    }

    /** {@inheritDoc} Returns the vat's player-assigned label. */
    @Override
    public @Nullable String getMachineLabel(int slot) {
        return label;
    }

    // -- IGasketHolder (cap = RECEIVER, base = TRANSMITTER) --

    /** {@inheritDoc} */
    @Override
    public @Nullable UUID getGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER ? capGasketId : baseGasketId;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable UUID ensureGasketId(GasketRole role) {
        return role == GasketRole.RECEIVER
            ? ensureOrGenerate(capGasketId, id -> capGasketId = id)
            : ensureOrGenerate(baseGasketId, id -> baseGasketId = id);
    }

    /** Returns the existing UUID or generates a new one, syncing if created. */
    private UUID ensureOrGenerate(@Nullable UUID current, Consumer<UUID> setter) {
        if (current != null) return current;
        UUID generated = UUID.randomUUID();
        setter.accept(generated);
        markDirtyAndSync();
        return generated;
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable GasketPartner getPartner(GasketRole role) {
        return role == GasketRole.RECEIVER ? capPartner : basePartner;
    }

    /** {@inheritDoc} */
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

    /** Clears the gasket UUID and partner for the given role. */
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

    /** Returns the player-assigned label, or null if unnamed. */
    @Nullable
    public String getLabel() {
        return label;
    }

    /** Sets the player-assigned label (null to clear). */
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

    /** Returns true if this vat is vertically connected to at least one other vat. */
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

    /** Returns the stream goo type, or null if no active stream. */
    public @Nullable GooType getVatStreamType(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamType : null;
    }

    /** Returns the stream rate in mB/tick, or 0 if no active stream. */
    public int getVatStreamRate(long currentTick) {
        return (currentTick - vatStreamTick <= 1) ? vatStreamRate : 0;
    }

    // --- Serialization ---

    /** Persists compression level, goo contents, label, and gasket state. */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Compression", compressionLevel);
        GooContents contents = fluidHandler.toGooContents();
        if (!contents.isEmpty()) {
            output.store("Contents", GooContents.CODEC, contents);
        }
        if (label != null) {
            output.putString("Label", label);
        }
        saveGasketFields(output);
        if (vatStreamType != null) {
            output.putInt("StreamType", vatStreamType.ordinal());
            output.putInt("StreamRate", vatStreamRate);
            output.putLong("StreamTick", vatStreamTick);
        }
    }

    /** Saves gasket UUIDs and partner references. */
    private void saveGasketFields(ValueOutput output) {
        if (capGasketId != null) {
            output.store("CapGasketId", UUIDUtil.STRING_CODEC, capGasketId);
        }
        if (baseGasketId != null) {
            output.store("BaseGasketId", UUIDUtil.STRING_CODEC, baseGasketId);
        }
        if (capPartner != null) {
            output.store("CapPartner", GasketPartner.CODEC, capPartner);
        }
        if (basePartner != null) {
            output.store("BasePartner", GasketPartner.CODEC, basePartner);
        }
    }

    /** Restores compression level, goo contents, label, and gasket state. */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        compressionLevel = Math.max(0, Math.min(
                input.getIntOr("Compression", input.getIntOr("Matrices", 0)),
                ContainerCapacity.MAX_COMPRESSION));
        syncCapacity();
        GooContents contents = input.read("Contents", GooContents.CODEC)
            .orElse(GooContents.EMPTY);
        fluidHandler.loadFrom(contents);
        label = input.getString("Label").orElse(null);
        loadGasketFields(input);
        int streamOrdinal = input.getIntOr("StreamType", -1);
        GooType[] gooTypes = GooType.values();
        vatStreamType = streamOrdinal >= 0 && streamOrdinal < gooTypes.length ? gooTypes[streamOrdinal] : null;
        vatStreamRate = input.getIntOr("StreamRate", 0);
        vatStreamTick = input.getLongOr("StreamTick", 0);
    }

    /** Loads gasket UUIDs and partner references. */
    private void loadGasketFields(ValueInput input) {
        capGasketId = input.read("CapGasketId", UUIDUtil.STRING_CODEC).orElse(null);
        baseGasketId = input.read("BaseGasketId", UUIDUtil.STRING_CODEC).orElse(null);
        capPartner = input.read("CapPartner", GasketPartner.CODEC).orElse(null);
        basePartner = input.read("BasePartner", GasketPartner.CODEC).orElse(null);
    }

    /** Captures gasket registry access and rebuilds the push cache when the level is assigned. */
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

    /** Writes compression enchantment and goo contents to the item when the block is broken. */
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

    /** Reads compression enchantment and goo contents from the placed item. */
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

    /** Returns full NBT for initial chunk sync to clients. */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** Returns the sync packet sent when block entity data changes. */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
