package com.mercuriusxeno.goo.block.hub;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.*;
import com.mercuriusxeno.goo.block.gasket.GasketAttachment;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
 * <p>Slot state delegated to {@link SlottedCanisterData}. Internal logic
 * delegated to: {@link HubSlotLifecycle} (handler/pusher/shape),
 * {@link HubSerialization} (stream state + chunk forcing).
 * Intake gasket field storage owned by {@link GasketState#single}.</p>
 */
public class HubBlockEntity extends BlockEntity implements ICanisterHolder, IGasketHolder, ICanisterAttachable {

    public static final int MAX_CANISTERS = 8;

    /**
     * Face label for the intake gasket.
     */
    private static final String FACE_LABEL = "hub";
    /**
     * NBT tag for the slot grid (per-slot child compounds).
     */
    private static final String TAG_SLOTS = "Slots";
    /**
     * Block update flags: notify neighbours + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /**
     * Composed gasket integration: RECEIVER intake, slot-level pushers managed by HubSlotLifecycle.
     */
    private final GasketAttachment gasket = GasketAttachment.single(this, GasketRole.RECEIVER, FACE_LABEL);

    /**
     * Behavioral component owning slot arrays, handlers, and stream state.
     */
    private final SlottedCanisterData state;

    /**
     * Creates a hub block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.HUB.get(), pos, state);
        this.state = new SlottedCanisterData(
                MAX_CANISTERS,
                HubBlock::slotShape,
                HubSlotLifecycle::computeShape,
                gasket.syncCallback());
        gasket.rebuildPushers(() -> {
            if (level instanceof ServerLevel) {
                HubSlotLifecycle.rebuildAllSlotPushers(this);
            }
        });
        gasket.afterLoad(() -> {
            if (level instanceof ServerLevel serverLevel) {
                HubSerialization.forceAllTransmitterChunks(this, serverLevel, worldPosition);
            }
        });
    }

    /**
     * Static tick entrypoint for the block entity ticker.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HubBlockEntity be) {
        be.state.tickPushers();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    // --- ICanisterAttachable ---

    /**
     * {@inheritDoc}
     */
    @Override
    public int currentTopAttachments() {
        if (level == null) {
            return 0;
        }
        BlockPos above = worldPosition.above();
        if (level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe) {
            return canisterBe.getCanister(CanisterBlock.CENTER_SLOT).isEmpty() ? 0 : 1;
        }
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public java.util.Set<Integer> allowedSlots() {
        return java.util.Set.of(CanisterBlock.CENTER_SLOT);
    }

    /**
     * Returns the block-level fluid handler for Transfer API.
     *
     * @return the fluid handler
     */
    public HubFluidHandler getFluidHandler() {
        return new HubFluidHandler(this);
    }

    // --- IGasketHolder ---

    @Override
    public GasketAttachment gasket() {
        return gasket;
    }

    @Override
    public int resolveSlot(BlockHitResult hit) {
        int slot = HubBlock.hitSlot(hit, getBlockPos());
        return slot < 0 ? SLOT_MISS : slot;
    }

    @Override
    public boolean hasIntake() {
        return true;
    }

    /**
     * {@inheritDoc} Clears intake-only state. Does not rebuild slot pushers since the
     * intake gasket is independent of slot topology. Also flips the HAS_GASKET blockstate.
     */
    @Override
    public void clearGasket(GasketRole role) {
        gasket.state().clear(role, this::onGasketCleared);
    }

    /**
     * Clears the HAS_GASKET blockstate flag and syncs to client.
     */
    private void onGasketCleared() {
        if (getLevel() != null) {
            BlockState bs = getLevel().getBlockState(getBlockPos());
            if (bs.getValue(HubBlock.HAS_GASKET)) {
                getLevel().setBlock(getBlockPos(),
                        bs.setValue(HubBlock.HAS_GASKET, false), BLOCK_UPDATE_FLAGS);
            }
        }
        BlockEntitySync.markDirtyAndSync(this);
    }

    @Override
    public void setPartner(GasketRole role, int slot, @Nullable GasketPartner partner) {
        IGasketHolder.super.setPartner(role, slot, partner);
        if (role == GasketRole.TRANSMITTER && slot >= 0 && slot < MAX_CANISTERS) {
            HubSlotLifecycle.rebuildSlotPusher(this, slot);
        }
    }

    // --- Framework lifecycle ---

    @Override
    public void setLevel(@NonNull Level level) {
        super.setLevel(level);
        gasket.onSetLevel(level);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        gasket.onLoad();
        BlockEntitySync.kickLightingOnLoad(this);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
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
        HubSlotLifecycle.rebuildAllSlotHandlers(this);
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
     * {@inheritDoc}
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.@NonNull Builder builder) {
        super.collectImplicitComponents(builder);
        List<ItemStack> nonEmpty = new java.util.ArrayList<>();
        for (CanisterSlot slot : state.slots) {
            if (!slot.isEmpty()) {
                nonEmpty.add(slot.canister());
            }
        }
        if (!nonEmpty.isEmpty()) {
            builder.set(GooDataComponents.HUB_CANISTERS.get(), nonEmpty);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void applyImplicitComponents(@NonNull DataComponentGetter getter) {
        super.applyImplicitComponents(getter);
        List<ItemStack> fromItem = getter.get(GooDataComponents.HUB_CANISTERS.get());
        if (fromItem != null) {
            for (int i = 0; i < MAX_CANISTERS; i++) {
                state.slots[i].setCanister(i < fromItem.size() ? fromItem.get(i) : ItemStack.EMPTY);
            }
        }
    }
}
