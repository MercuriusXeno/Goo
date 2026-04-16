package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Reactor block entity: reads fluid from 4 corner canisters on top,
 * matches a reaction recipe, and pushes output into the front canister.
 * Redstone halts processing. Throughput scales with available batches
 * via power-law: ceil(minBatches ^ 0.35).
 */
public class ReactorBlockEntity extends BlockEntity
        implements ICanisterHolder {

    /** Corner slot indices in the canister block's 3x3 grid. */
    private static final int[] INPUT_SLOTS = {0, 2, 6, 8};

    /** The output hollow holds one canister. */
    private static final int OUTPUT_SLOT_COUNT = 1;

    /** Output canister slot index. */
    public static final int OUTPUT_SLOT = 0;

    /** Power-law exponent for throughput scaling. */
    private static final double THROUGHPUT_EXPONENT = 0.35;

    /** NBT key for the output canister. */
    private static final String TAG_OUTPUT_CANISTER = "OutputCanister";

    /** Slotted state for the single output canister. */
    private final SlottedCanisterState state = new SlottedCanisterState(
            OUTPUT_SLOT_COUNT,
            NonNullList.withSize(OUTPUT_SLOT_COUNT, ItemStack.EMPTY),
            () -> BlockEntitySync.markDirtyAndSync(this),
            cans -> Shapes.empty());

    /**
     * Creates a reactor block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.REACTOR.get(), pos, state);
    }

    @Override
    public SlottedCanisterState containerState() { return state; }

    /**
     * Returns the output canister (may be EMPTY).
     *
     * @return the output canister item stack
     */
    public @NonNull ItemStack getOutputCanister() {
        return state.getCanister(OUTPUT_SLOT);
    }

    /**
     * Inserts a canister into the output slot.
     *
     * @param stack the canister to insert
     * @return true if inserted
     */
    public boolean insertOutputCanister(ItemStack stack) {
        if (!getOutputCanister().isEmpty()) { return false; }
        state.canisters.set(OUTPUT_SLOT, stack.copyWithCount(1));
        markDirtyAndSync();
        return true;
    }

    /**
     * Removes and returns the output canister.
     *
     * @return the removed canister, or EMPTY
     */
    public @NonNull ItemStack removeOutputCanister() {
        ItemStack current = getOutputCanister();
        if (current.isEmpty()) { return ItemStack.EMPTY; }
        state.canisters.set(OUTPUT_SLOT, ItemStack.EMPTY);
        markDirtyAndSync();
        return current;
    }

    /** Marks dirty and syncs to client. */
    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(),
                    getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Server tick: if not redstone-halted, resolve a reaction and execute it.
     *
     * @param level the server level
     * @param pos   the block position
     * @param state the block state
     * @param be    the reactor block entity
     */
    public static void serverTick(Level level, BlockPos pos,
            BlockState state, ReactorBlockEntity be) {
        if (state.getValue(ReactorBlock.TRIGGERED)) { return; }
        be.tickReaction(level, pos, state);
    }

    /**
     * Resolves the best matching reaction and executes batches.
     *
     * @param level the server level
     * @param pos   the block position
     * @param bState the block state
     */
    private void tickReaction(Level level, BlockPos pos, BlockState bState) {
        CanisterBlockEntity inputBe = getInputCanisterBe(level, pos);
        if (inputBe == null) {
            clearCrafting(level, pos, bState);
            return;
        }

        GooReaction reaction = resolveReaction(inputBe);
        if (reaction == null) {
            clearCrafting(level, pos, bState);
            return;
        }

        int batches = computeBatches(inputBe, reaction);
        if (batches <= 0) {
            clearCrafting(level, pos, bState);
            return;
        }

        consumeInputs(inputBe, reaction.inputs(), batches);
        produceOutputs(reaction.outputs(), batches, reaction.rate());
        setChanged();
        setCrafting(level, pos, bState);
    }

    /**
     * Returns the canister block entity above (input source).
     *
     * @param level the level
     * @param pos   the reactor position
     * @return the canister BE, or null
     */
    private @Nullable CanisterBlockEntity getInputCanisterBe(
            Level level, BlockPos pos) {
        BlockEntity above = level.getBlockEntity(pos.above());
        return above instanceof CanisterBlockEntity cbe ? cbe : null;
    }

    /**
     * Finds the first matching reaction (superset-first).
     *
     * @param inputBe the input canister BE
     * @return the matched reaction, or null
     */
    private @Nullable GooReaction resolveReaction(CanisterBlockEntity inputBe) {
        for (GooReaction reaction : GooReactionLoader.getReactions()) {
            if (inputsSatisfy(inputBe, reaction)) { return reaction; }
        }
        return null;
    }

    /**
     * Checks whether all reaction inputs are present in the corner slots.
     *
     * @param inputBe  the input canister BE
     * @param reaction the candidate reaction
     * @return true if all inputs are satisfied
     */
    private boolean inputsSatisfy(CanisterBlockEntity inputBe,
            GooReaction reaction) {
        for (GooReaction.FluidEntry entry : reaction.inputs()) {
            if (getAvailableFluid(inputBe, entry.fluid()) < entry.amount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sums fluid volume across the 4 corner input slots.
     *
     * @param inputBe the input canister BE
     * @param fluid   the fluid to sum
     * @return total mB available
     */
    private long getAvailableFluid(CanisterBlockEntity inputBe, Fluid fluid) {
        long total = 0;
        for (int slot : INPUT_SLOTS) {
            ItemStack stack = inputBe.getCanister(slot);
            if (stack.isEmpty()) { continue; }
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            if (content.fluid() == fluid) { total += content.amount(); }
        }
        return total;
    }

    /**
     * Computes batches per tick: ceil(minBatches ^ 0.35).
     *
     * @param inputBe  the input canister BE
     * @param reaction the matched reaction
     * @return batches to execute
     */
    private int computeBatches(CanisterBlockEntity inputBe,
            GooReaction reaction) {
        long minBatches = Long.MAX_VALUE;
        for (GooReaction.FluidEntry entry : reaction.inputs()) {
            long available = getAvailableFluid(inputBe, entry.fluid());
            long possible = available / entry.amount();
            minBatches = Math.min(minBatches, possible);
        }
        if (minBatches <= 0) { return 0; }
        return (int) Math.ceil(Math.pow(minBatches, THROUGHPUT_EXPONENT));
    }

    /**
     * Consumes input fluids from corner canister slots.
     *
     * @param inputBe the input canister BE
     * @param inputs  the reaction inputs
     * @param batches number of batches
     */
    private void consumeInputs(CanisterBlockEntity inputBe,
            List<GooReaction.FluidEntry> inputs, int batches) {
        for (GooReaction.FluidEntry entry : inputs) {
            consumeFluid(inputBe, entry.fluid(), entry.amount() * batches);
        }
    }

    /**
     * Drains a fluid across corner input slots.
     *
     * @param inputBe the input canister BE
     * @param fluid   the fluid to drain
     * @param amount  total mB to drain
     */
    private void consumeFluid(CanisterBlockEntity inputBe,
            Fluid fluid, long amount) {
        long remaining = amount;
        for (int slot : INPUT_SLOTS) {
            if (remaining <= 0) { break; }
            ItemStack stack = inputBe.getCanister(slot);
            if (stack.isEmpty()) { continue; }
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            if (content.fluid() != fluid) { continue; }
            long drain = Math.min(remaining, content.amount());
            CanisterItem.setFluidContent(stack,
                    new CanisterFluidContent(fluid, content.amount() - drain));
            remaining -= drain;
        }
    }

    /**
     * Pushes output fluids into the output canister in the hollow.
     *
     * @param outputs the reaction outputs
     * @param batches number of batches
     * @param rate    output multiplier
     */
    private void produceOutputs(List<GooReaction.FluidEntry> outputs,
            int batches, int rate) {
        ItemStack outStack = getOutputCanister();
        if (outStack.isEmpty()) { return; }
        for (GooReaction.FluidEntry entry : outputs) {
            long amount = entry.amount() * batches * rate;
            CanisterItem.addFluid(outStack, entry.fluid(), amount);
        }
    }

    /**
     * Sets CRAFTING blockstate if not already set.
     *
     * @param level  the level
     * @param pos    the position
     * @param bState the current state
     */
    private void setCrafting(Level level, BlockPos pos, BlockState bState) {
        if (!bState.getValue(ReactorBlock.CRAFTING)) {
            level.setBlock(pos, bState.setValue(ReactorBlock.CRAFTING, true),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Clears CRAFTING blockstate if currently set.
     *
     * @param level  the level
     * @param pos    the position
     * @param bState the current state
     */
    private void clearCrafting(Level level, BlockPos pos, BlockState bState) {
        if (bState.getValue(ReactorBlock.CRAFTING)) {
            level.setBlock(pos, bState.setValue(ReactorBlock.CRAFTING, false),
                    Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        ItemStack canister = getOutputCanister();
        if (!canister.isEmpty()) {
            output.store(TAG_OUTPUT_CANISTER, ItemStack.CODEC, canister);
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        state.canisters.set(OUTPUT_SLOT,
                input.read(TAG_OUTPUT_CANISTER, ItemStack.CODEC)
                        .orElse(ItemStack.EMPTY));
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
}
