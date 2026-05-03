package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooBlockInteraction;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.GasketInstallation;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRegionResolver;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Block entity for the multi-canister block. Holds up to 9 canister items
 * in a 3x3 grid within a single block space. Each sub-canister retains its
 * own gasket UUIDs for the transport network.
 *
 * <p>Per-slot state lives on {@link CanisterSlot}. This class owns the BE-
 * coordinated work: handler/pusher creation, gasket registry membership,
 * NBT save/load, framework lifecycle, and player interaction dispatch.</p>
 */
public class CanisterBlockEntity extends BlockEntity implements ICanisterHolder, IGasketHolder {

    /**
     * Maximum number of canister slots in the 3x3 grid.
     */
    public static final int MAX_SLOTS = 9;
    /**
     * Center slot index in the 3x3 grid (default placement target).
     */
    static final int CENTER_SLOT = 4;

    private static final String TAG_OWNER_UUID = "OwnerUuid";
    private static final String TAG_SLOTS = "Slots";
    private static final String ERR_TUNER_PASS = "TUNER_PASS handled in validate";
    private static final String ERR_UNHANDLED = "Unhandled interaction: ";

    /**
     * Composed gasket integration: roleless (per-slot canister metadata holds gasket UUIDs).
     * Provides the registry access and sync surface; slot pushers are managed locally.
     */
    private final GasketAttachment gasket = GasketAttachment.none(this);

    private final SlottedCanisterData state;

    private @Nullable UUID ownerUuid;

    /**
     * Creates a new canister block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public CanisterBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CANISTER.get(), pos, state);
        this.state = new SlottedCanisterData(MAX_SLOTS,
                CanisterBlock::slotShape,
                CanisterBlockEntity::buildCompositeShape,
                gasket.syncCallback());
        gasket.rebuildPushers(() -> {
            if (level instanceof ServerLevel) {
                rebuildAllSlotPushers();
            }
        });
        gasket.afterLoad(() -> {
            if (level instanceof ServerLevel serverLevel) {
                forceAllTransmitterChunks(serverLevel);
            }
        });
    }

    /**
     * Composite shape builder: union of occupied slot shapes, falling back
     * to the center slot when nothing is occupied (placeholder visual).
     *
     * @param slots the live slot array
     * @return the composite voxel shape
     */
    private static VoxelShape buildCompositeShape(CanisterSlot[] slots) {
        VoxelShape result = Shapes.empty();
        boolean any = false;
        for (CanisterSlot slot : slots) {
            VoxelShape shape = slot.shape();
            if (shape != null) {
                result = Shapes.or(result, shape);
                any = true;
            }
        }
        return any ? result : CanisterBlock.slotShape(CENTER_SLOT);
    }

    /**
     * Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the canister block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, CanisterBlockEntity be) {
        be.state.tickPushers();
    }

    private static void applyFluidAndMetadata(ItemStack stack,
                                              CanisterFluidContent content,
                                              CanisterMetadata meta) {
        if (content != null && !content.isEmpty()) {
            CanisterItem.setFluidContent(stack, content);
        }
        if (meta != null && meta.hasData()) {
            CanisterItem.setMetadata(stack, meta);
        }
    }

    private static boolean slotNeedsPusher(CanisterSlot slot) {
        if (slot.handler() == null || slot.isEmpty()) {
            return false;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
        return meta.bottomGasketId() != null && meta.bottomPartner() != null;
    }

    private static void registerFace(GasketRegistry registry, @Nullable UUID id, GasketLocation location) {
        if (id != null) {
            registry.updateLocation(id, location);
        }
    }

    private static void deregisterFace(GasketRegistry registry, @Nullable UUID id) {
        if (id != null) {
            registry.updateLocation(id, null);
        }
    }

    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    /**
     * @return the owner UUID, or null if unowned
     */
    @Nullable
    public UUID getOwner() {
        return ownerUuid;
    }

    /**
     * Sets the owner UUID (called when placed by a player).
     *
     * @param owner the player UUID to set as owner
     */
    public void setOwner(UUID owner) {
        ownerUuid = owner;
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Inserts a canister into the slot, optionally stripping gasket UUIDs.
     * Validates placement, builds the slot's handler and pusher, registers
     * gaskets, invalidates capabilities, and syncs.
     *
     * @param slotIndex     the slot index
     * @param canisterStack the canister item stack to insert
     * @param stripGaskets  true to clear gasket UUIDs (creative duplication)
     * @return true if inserted
     */
    public boolean insertCanister(int slotIndex, ItemStack canisterStack, boolean stripGaskets) {
        CanisterSlot slot = resolveInsertableSlot(slotIndex, canisterStack);
        if (slot == null) {
            return false;
        }
        slot.setCanister(canisterStack.copyWithCount(1));
        if (stripGaskets) {
            slot.stripGaskets();
        }
        slot.buildHandler(this::gameTime);
        rebuildSlotPusher(slotIndex);
        BlockEntitySync.invalidateCapabilities(this);
        registerSlotGaskets(slotIndex);
        return true;
    }

    /**
     * Returns the slot at {@code slotIndex} if it is a valid insertion target
     * for {@code canisterStack}, otherwise null. Combines the placement-rule,
     * empty-slot, and item-class checks into one resolver.
     *
     * @param slotIndex     the slot index
     * @param canisterStack the candidate canister stack
     * @return the slot ready to receive the canister, or null
     */
    private @Nullable CanisterSlot resolveInsertableSlot(int slotIndex, ItemStack canisterStack) {
        if (level != null && !CanisterPlacementValidator.isSlotAllowed(level, worldPosition, slotIndex)) {
            return null;
        }
        if (!(canisterStack.getItem() instanceof CanisterItem)) {
            return null;
        }
        CanisterSlot slot = slot(slotIndex);
        if (slot == null || !slot.isEmpty()) {
            return null;
        }
        return slot;
    }

    /**
     * Removes the canister from the slot, disposing handler/pusher and
     * deregistering gaskets.
     *
     * @param slotIndex the slot index
     * @return the removed canister stack, or EMPTY
     */
    public ItemStack removeCanister(int slotIndex) {
        CanisterSlot slot = slot(slotIndex);
        if (slot == null || slot.isEmpty()) {
            return ItemStack.EMPTY;
        }
        slot.disposePusher();
        slot.syncHandlerToStack();
        ItemStack removed = slot.canister().copy();
        deregisterSlotGaskets(slotIndex);
        slot.clear();
        BlockEntitySync.invalidateCapabilities(this);
        return removed;
    }

    /**
     * Assigns canister contents from a source ItemStack during initial placement.
     *
     * @param targetSlot   target grid slot (clamped to center if out of range)
     * @param source       the held canister ItemStack being placed
     * @param stripGaskets true to clear gasket UUIDs (creative-mode duplication)
     */
    public void assignFromItemStack(int targetSlot, ItemStack source, boolean stripGaskets) {
        int target = state.inRange(targetSlot) ? targetSlot : CENTER_SLOT;
        ItemStack built = new ItemStack(GooItems.CANISTER.get());
        applyFluidAndMetadata(built, CanisterItem.getFluidContent(source), CanisterItem.getMetadata(source));
        CanisterSlot slot = state.slots[target];
        slot.setCanister(built);
        if (stripGaskets) {
            slot.stripGaskets();
        }
        slot.buildHandler(this::gameTime);
        BlockEntitySync.invalidateCapabilities(this);
    }

    /**
     * Rebuilds slot fluid handlers for all occupied slots (after deserialization).
     */
    private void rebuildAllSlotHandlers() {
        for (CanisterSlot slot : state.slots) {
            slot.buildHandler(this::gameTime);
        }
    }

    /**
     * Rebuilds gasket pushers for all occupied slots.
     */
    private void rebuildAllSlotPushers() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            rebuildSlotPusher(i);
        }
    }

    // --- Gasket ops ---

    /**
     * Rebuilds the gasket pusher for a slot based on its bottom gasket state.
     *
     * @param slotIndex the slot index
     */
    private void rebuildSlotPusher(int slotIndex) {
        CanisterSlot slot = slot(slotIndex);
        if (slot == null) {
            return;
        }
        slot.disposePusher();
        if (!slotNeedsPusher(slot)) {
            return;
        }
        slot.setPusher(buildPusher(slot));
    }

    private GasketPusher buildPusher(CanisterSlot slot) {
        GasketPusher pusher = new GasketPusher(slot.handler(),
                () -> CanisterItem.getMetadata(slot.canister()).bottomGasketId(),
                () -> CanisterItem.getMetadata(slot.canister()).bottomPartner(),
                this::getLevel, this::getBlockPos,
                slot::syncHandlerToStack, gasket.registryAccess());
        pusher.rebuildCache();
        return pusher;
    }

    /**
     * Registers gasket locations for all occupied slots.
     */
    private void registerAllGaskets() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!state.slots[i].isEmpty()) {
                registerSlotGaskets(i);
            }
        }
    }

    /**
     * Registers gasket locations for a slot's canister in the gasket registry.
     *
     * @param slotIndex the slot index
     */
    private void registerSlotGaskets(int slotIndex) {
        if (gasket.registryAccess() == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CanisterSlot slot = state.slots[slotIndex];
        if (slot.isEmpty()) {
            return;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
        GasketRegistry registry = gasket.registryAccess().get();
        ResourceKey<Level> dimension = serverLevel.dimension();
        registerFace(registry, meta.topGasketId(),
                new GasketLocation(dimension, worldPosition, true, slotIndex));
        registerFace(registry, meta.bottomGasketId(),
                new GasketLocation(dimension, worldPosition, false, slotIndex));
    }

    private void deregisterSlotGaskets(int slotIndex) {
        if (gasket.registryAccess() == null) {
            return;
        }
        CanisterSlot slot = state.slots[slotIndex];
        if (slot.isEmpty()) {
            return;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
        GasketRegistry registry = gasket.registryAccess().get();
        deregisterFace(registry, meta.topGasketId());
        deregisterFace(registry, meta.bottomGasketId());
    }

    private void deregisterAllGaskets() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!state.slots[i].isEmpty()) {
                deregisterSlotGaskets(i);
            }
        }
    }

    private void forceAllTransmitterChunks(ServerLevel serverLevel) {
        for (CanisterSlot slot : state.slots) {
            if (slot.isEmpty()) {
                continue;
            }
            CanisterMetadata meta = CanisterItem.getMetadata(slot.canister());
            GasketPusher.forceTransmitterChunk(meta.topGasketId(), gasket.registryAccess(),
                    serverLevel, worldPosition);
        }
    }

    // --- IGasketHolder ---

    @Override
    public GasketAttachment gasket() {
        return gasket;
    }

    /**
     * Slot-level gaskets always support both transmitter and receiver roles.
     */
    @Override
    public boolean supportsRole(GasketRole role) {
        return true;
    }

    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = CanisterBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    @Override
    public boolean allowsTuning(@Nullable UUID tunerOwner) {
        return ownerUuid == null || tunerOwner == null || tunerOwner.equals(ownerUuid);
    }

    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && state.inRange(slot)) {
            rebuildSlotPusher(slot);
        }
    }

    // --- Player interaction (formerly CanisterBlockHandlers) ---

    /**
     * Picks up the targeted canister, removing it from the grid and giving
     * it to the player. Removes the block if no canisters remain.
     *
     * @param player    the interacting player
     * @param hitResult the ray trace hit result
     * @return SUCCESS if a canister was picked up, PASS otherwise
     */
    public InteractionResult handleCanisterPickup(Player player, BlockHitResult hitResult) {
        int slotIndex = CanisterBlock.hitSlot(hitResult, worldPosition);
        if (slotIndex < 0 || level == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        CanisterSlot slot = slot(slotIndex);
        if (slot == null || slot.isEmpty()) {
            return InteractionResult.PASS;
        }
        return pickupFromSlot(player, slotIndex);
    }

    private InteractionResult pickupFromSlot(Player player, int slotIndex) {
        boolean lastCanister = countOccupied() == 1;
        if (lastCanister) {
            // Take the stack directly and remove the block in one step to
            // avoid an out-of-order BE-data packet vs. block-state-change-to-air.
            ItemStack taken = state.slots[slotIndex].canister().copy();
            PlayerUtils.addOrDrop(player, taken);
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_HIT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            level.removeBlock(worldPosition, false);
        } else {
            ItemStack removed = removeCanister(slotIndex);
            PlayerUtils.addOrDrop(player, removed);
            level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_HIT,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private int countOccupied() {
        int count = 0;
        for (CanisterSlot slot : state.slots) {
            if (!slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Attempts fluid container interaction (bucket fill/drain) on the targeted slot.
     *
     * @param player    the interacting player
     * @param hand      the hand holding the fluid container
     * @param hitResult the ray trace hit result
     * @return SUCCESS if fluid transferred, null if not applicable
     */
    public @Nullable InteractionResult tryFluidInteraction(Player player, InteractionHand hand,
                                                           BlockHitResult hitResult) {
        int slotIndex = CanisterBlock.hitSlot(hitResult, worldPosition);
        CanisterSlot slot = slot(slotIndex);
        if (slot == null || slot.handler() == null) {
            return null;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, worldPosition, slot.handler())) {
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    /**
     * Dispatches a validated interaction to the appropriate handler method.
     *
     * @param interaction the classified interaction type
     * @param stack       the held item stack
     * @param player      the interacting player
     * @param hitResult   the ray trace hit result
     * @return the interaction result
     */
    public InteractionResult dispatch(GooInteractionType interaction, ItemStack stack,
                                      Player player, BlockHitResult hitResult) {
        if (interaction == GooInteractionType.TUNER_PASS) {
            throw new IllegalStateException(ERR_TUNER_PASS);
        }
        return switch (interaction) {
            case CANISTER_INSERT -> handleCanisterInsert(hitResult, stack, player);
            case BLOB_INSERT -> handleBlobInsert(hitResult, stack, player);
            default -> throw new IllegalStateException(ERR_UNHANDLED + interaction);
        };
    }

    /**
     * Removes the gasket on the targeted face of a canister slot, if installed.
     *
     * @param slotIndex the targeted slot index
     * @param hitResult the ray trace hit result
     * @return SUCCESS if a gasket was removed, PASS otherwise
     */
    public InteractionResult handleSlotGasketRemove(int slotIndex, BlockHitResult hitResult) {
        GasketRole role = resolveSlotGasketRole(hitResult);
        CanisterMetadata meta = getSlotMetadata(slotIndex);
        UUID gasketId = role == GasketRole.RECEIVER ? meta.topGasketId() : meta.bottomGasketId();
        if (gasketId == null) {
            return InteractionResult.PASS;
        }
        GasketInstallation.popGasket(level, worldPosition, gasketId);
        CanisterMetadata cleared = role == GasketRole.RECEIVER
                ? meta.withoutTopGasket() : meta.withoutBottomGasket();
        setSlotMetadata(slotIndex, cleared);
        return InteractionResult.SUCCESS;
    }

    private GasketRole resolveSlotGasketRole(BlockHitResult hitResult) {
        double localY = hitResult.getLocation().y - worldPosition.getY();
        return GasketRegionResolver.resolveCanisterSlotRole(localY, 0.0, 1.0);
    }

    private InteractionResult handleCanisterInsert(BlockHitResult hitResult, ItemStack stack, Player player) {
        if (!tryInsertCanister(hitResult, stack, player.isCreative())) {
            return InteractionResult.PASS;
        }
        stack.consume(1, player);
        level.playSound(null, worldPosition, SoundEvents.DECORATED_POT_INSERT,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private boolean tryInsertCanister(BlockHitResult hitResult, ItemStack stack, boolean stripGaskets) {
        int slot = CanisterSlotResolver.resolveAndConstrain(
                hitResult.getLocation(), worldPosition, hitResult.getDirection(), this);
        return slot >= 0 && insertCanister(slot, stack, stripGaskets);
    }

    private InteractionResult handleBlobInsert(BlockHitResult hitResult, ItemStack stack, Player player) {
        GooType type = BlobStacks.gooTypeOf(stack);
        if (type == null) {
            return InteractionResult.PASS;
        }
        int hitSlot = CanisterBlock.hitSlot(hitResult, worldPosition);
        int accepted = tryInsertBlobGoo(hitSlot, type, BlobStacks.volumeOf(stack));
        if (accepted <= 0) {
            return InteractionResult.PASS;
        }
        BlobStacks.deplete(stack, accepted, player);
        level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY,
                SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private int tryInsertBlobGoo(int hitSlot, GooType type, int volume) {
        int slot = GooBlockInteraction.findSlot(hitSlot, MAX_SLOTS, this::canAccept);
        if (slot < 0) {
            return 0;
        }
        return insertGoo(slot, type, volume);
    }

    // --- Framework lifecycle ---

    @Override
    public void setRemoved() {
        state.disposeAllPushers();
        deregisterAllGaskets();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (ownerUuid != null) {
            output.store(TAG_OWNER_UUID, UUIDUtil.STRING_CODEC, ownerUuid);
        }
        CompoundTag root = new CompoundTag();
        for (CanisterSlot slot : state.slots) {
            CompoundTag slotTag = new CompoundTag();
            slot.save(slotTag);
            if (!slotTag.isEmpty()) {
                root.put(String.valueOf(slot.index()), slotTag);
            }
        }
        if (!root.isEmpty()) {
            output.store(TAG_SLOTS, CompoundTag.CODEC, root);
        }
        gasket.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read(TAG_OWNER_UUID, UUIDUtil.STRING_CODEC).ifPresent(u -> ownerUuid = u);
        input.read(TAG_SLOTS, CompoundTag.CODEC).ifPresent(root -> {
            for (CanisterSlot slot : state.slots) {
                String key = String.valueOf(slot.index());
                CompoundTag slotTag = root.contains(key) ? root.getCompoundOrEmpty(key) : new CompoundTag();
                slot.load(slotTag);
            }
            // slot.load() skips structure-changed callbacks; rebuild now so
            // raycasting + outline rendering see the loaded slot occupancy.
            state.rebuildCompositeShape();
        });
        gasket.loadAdditional(input);
        rebuildAllSlotHandlers();
    }

    @Override
    public void setLevel(@NonNull Level newLevel) {
        super.setLevel(newLevel);
        gasket.onSetLevel(newLevel);
        if (newLevel instanceof ServerLevel) {
            registerAllGaskets();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        gasket.onLoad();
    }

    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return gasket.getUpdateTag(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return gasket.getUpdatePacket();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        int singleSlot = findSingleOccupiedSlot();
        if (singleSlot != GooConstants.NO_SLOT) {
            state.slots[singleSlot].exportComponents(builder);
        }
    }

    private int findSingleOccupiedSlot() {
        int found = GooConstants.NO_SLOT;
        for (CanisterSlot slot : state.slots) {
            if (!slot.isEmpty()) {
                if (found != GooConstants.NO_SLOT) {
                    return GooConstants.NO_SLOT;
                }
                found = slot.index();
            }
        }
        return found;
    }

    /**
     * @return the level's current game tick, or 0 if no level
     */
    private long gameTime() {
        return level != null ? level.getGameTime() : 0L;
    }
}
