package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.block.fluid.HubFluidHandler;
import com.mercuriusxeno.goo.block.gasket.GasketState;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Hub: holds up to 8 canisters in radial slots (N, NE, E, SE, S, SW, W, NW).
 * Central input on top auto-routes goo to canisters with remaining capacity.
 *
 * <p>Slot state delegated to {@link SlottedContainerState}. Internal logic
 * delegated to: {@link HubSlotLifecycle} (handler/pusher/shape),
 * {@link HubSerialization} (stream state + chunk forcing).
 * Intake gasket field storage owned by {@link GasketState#single}.</p>
 */
public class HubBlockEntity extends BlockEntity implements ISlottedGooContainer, IGasketHolder, ICanisterAttachable {

    public static final int MAX_CANISTERS = 8;

    /** Face label for the intake gasket. */
    private static final String FACE_LABEL = "hub";
    /** NBT tag for the canister list. */
    private static final String TAG_CANISTERS = "Canisters";
    /** Block update flags: notify neighbours + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    /** Provides access to the gasket registry without a ServerLevel at call sites. */
    @Nullable IGasketRegistryAccess gasketRegistryAccess;

    /** Composed gasket state for the intake (RECEIVER) role. */
    private final GasketState gasketState = GasketState.single(GasketRole.RECEIVER, FACE_LABEL);

    /** Behavioral component owning slot arrays, handlers, and stream state. */
    private final SlottedContainerState state;

    /** Creates a hub block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.HUB.get(), pos, state);
        this.state = new SlottedContainerState(
            MAX_CANISTERS,
            NonNullList.withSize(MAX_CANISTERS, ItemStack.EMPTY),
            () -> BlockEntitySync.markDirtyAndSync(this),
            HubSlotLifecycle::computeShape);
    }

    /** {@inheritDoc} */
    @Override
    public SlottedContainerState containerState() { return state; }

    /** Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HubBlockEntity be) {
        be.state.tickPushers();
    }

    // --- ICanisterAttachable ---

    /** {@inheritDoc} */
    @Override
    public int currentTopAttachments() {
        if (level == null) { return 0; }
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe) {
            return canisterBe.getCanister(CanisterBlock.CENTER_SLOT).isEmpty() ? 0 : 1;
        }
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    public java.util.Set<Integer> allowedSlots() { return java.util.Set.of(CanisterBlock.CENTER_SLOT); }

    /** Returns the block-level fluid handler for Transfer API.
     *
     * @return the fluid handler
     */
    public HubFluidHandler getFluidHandler() { return new HubFluidHandler(this); }

    // --- IGasketHolder ---

    /** {@inheritDoc} */
    @Override
    public GasketState gasketState() { return gasketState; }

    /** {@inheritDoc} */
    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = HubBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasIntake() { return true; }

    /** {@inheritDoc} */
    @Override
    public Runnable gasketSyncCallback() {
        return () -> BlockEntitySync.markDirtyAndSync(this);
    }

    /** {@inheritDoc} Clears gasket and resets blockstate HAS_GASKET flag. */
    @Override
    public void clearGasket(GasketRole role) {
        gasketState.clear(role, () -> {
            if (getLevel() != null) {
                BlockState bs = getLevel().getBlockState(getBlockPos());
                if (bs.getValue(HubBlock.HAS_GASKET)) {
                    getLevel().setBlock(getBlockPos(),
                        bs.setValue(HubBlock.HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
                }
            }
            BlockEntitySync.markDirtyAndSync(this);
        });
    }

    /** {@inheritDoc} */
    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && slot >= 0 && slot < MAX_CANISTERS) {
            HubSlotLifecycle.rebuildSlotPusher(this, slot);
        }
    }

    // --- Framework lifecycle ---

    /** {@inheritDoc} */
    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        if (level instanceof ServerLevel serverLevel) {
            gasketRegistryAccess = () -> GasketRegistry.get(serverLevel);
            HubSlotLifecycle.rebuildAllSlotPushers(this);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) { return; }
        HubSlotLifecycle.rebuildAllSlotPushers(this);
        HubSerialization.forceAllTransmitterChunks(this, serverLevel, worldPosition);
    }

    /** {@inheritDoc} */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf(), state.canisters.stream().toList());
        gasketState.save(output);
        HubSerialization.saveStreamState(this, output);
    }

    /** {@inheritDoc} */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        input.read(TAG_CANISTERS, ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < MAX_CANISTERS && i < list.size(); i++) { state.canisters.set(i, list.get(i)); }
        });
        gasketState.load(input);
        HubSerialization.loadStreamState(this, input);
        HubSlotLifecycle.rebuildAllSlotHandlers(this);
        state.invalidateShape();
    }

    /** {@inheritDoc} */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveWithFullMetadata(registries);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** {@inheritDoc} */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        List<ItemStack> nonEmpty = state.canisters.stream().filter(s -> !s.isEmpty()).toList();
        if (!nonEmpty.isEmpty()) { builder.set(GooDataComponents.HUB_CANISTERS.get(), nonEmpty); }
    }

    /** {@inheritDoc} */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        List<ItemStack> fromItem = getter.get(GooDataComponents.HUB_CANISTERS.get());
        if (fromItem != null) {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                state.canisters.set(i, i < fromItem.size() ? fromItem.get(i) : ItemStack.EMPTY);
            }
        }
    }
}
