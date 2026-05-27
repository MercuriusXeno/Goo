package com.mercuriusxeno.goo.block.reactor;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.canister.*;
import com.mercuriusxeno.goo.data.GooReaction;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Reactor block entity: reads fluid from 4 corner canisters on top,
 * matches a reaction recipe, and pushes output into the front canister.
 * Redstone halts processing. Throughput scales with available batches
 * via power-law: ceil(minBatches ^ 0.35).
 */
public class ReactorBlockEntity extends BlockEntity
        implements ICanisterHolder, ICanisterAttachable {

    /**
     * Output canister slot index.
     */
    public static final int OUTPUT_SLOT = 0;
    /**
     * Corner slot indices in the canister block's 3x3 grid.
     */
    private static final int[] INPUT_SLOTS = {0, 2, 6, 8};
    /**
     * Only corner slots are valid for reactor input canisters.
     */
    private static final Set<Integer> CORNER_SLOTS = Set.of(0, 2, 6, 8);
    /**
     * The output hollow holds one canister.
     */
    private static final int OUTPUT_SLOT_COUNT = 1;
    /**
     * Power-law exponent for throughput scaling.
     */
    private static final double THROUGHPUT_EXPONENT = 0.35;

    /**
     * NBT key for the output canister.
     */
    private static final String TAG_OUTPUT_CANISTER = "OutputCanister";
    /**
     * Slotted state for the single output canister.
     */
    private final SlottedCanisterData state = new SlottedCanisterData(
            OUTPUT_SLOT_COUNT,
            i -> Shapes.empty(),
            slots -> Shapes.empty(),
            () -> BlockEntitySync.markDirtyAndSync(this));
    /**
     * Client-side wheel rotation angle in degrees. Not serialized.
     */
    public float wheelAngle;
    /**
     * Client-side wheel rotation speed in degrees per tick. Not serialized.
     */
    public float wheelSpeed;

    /** Wheel cycle in degrees -- one logical revolution. Public so the BER
     * can derive sprite-swap fractions from a single source of truth. */
    public static final float WHEEL_CYCLE_PERIOD = 90f;
    /** Max wheel speed in degrees per tick at full crafting. */
    private static final float MAX_WHEEL_SPEED = 24f;
    /** Acceleration in degrees/tick/tick when crafting. */
    private static final float WHEEL_ACCEL = 0.5f;
    /** Natural deceleration rate when crafting stops (degrees/tick/tick). */
    private static final float WHEEL_DECEL = 0.3f;
    /** Speed threshold below which the wheel hard-zeroes and snaps. */
    private static final float WHEEL_SPEED_EPSILON = 0.05f;
    /** Kinematic constant: stop distance under constant decel a is v^2 / (2a). */
    private static final float KINEMATIC_HALF = 2f;

    /**
     * Creates a reactor block entity.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ReactorBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.REACTOR.get(), pos, state);
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
        if (state.getValue(ReactorBlock.TRIGGERED)) {
            be.clearCrafting(level, pos, state);
            return;
        }
        be.tickReaction(level, pos, state);
    }

    /**
     * Client tick: integrates the wheel animation once per tick (20 Hz).
     * While crafting, accelerates toward MAX_WHEEL_SPEED; otherwise
     * decelerates to a clean stop at a cycle boundary.
     *
     * <p>Lives here, not in the BER's extractRenderState, because that
     * method runs at frame rate -- ticking it there caused the wheel to
     * advance ~3x too fast on a 60 fps client.
     *
     * @param level the client level
     * @param pos   the block position
     * @param state the block state (crafting flag drives accel vs decel)
     * @param be    the reactor block entity holding wheel speed/angle
     */
    public static void clientTick(Level level, BlockPos pos,
                                  BlockState state, ReactorBlockEntity be) {
        boolean crafting = state.getValue(ReactorBlock.CRAFTING);
        if (crafting) {
            be.wheelSpeed = Math.min(MAX_WHEEL_SPEED, be.wheelSpeed + WHEEL_ACCEL);
        } else {
            decelerateWheel(be);
        }
        be.wheelAngle = (be.wheelAngle + be.wheelSpeed) % WHEEL_CYCLE_PERIOD;
    }

    /**
     * Decelerates the wheel toward the next cycle-boundary rest. Below
     * the speed epsilon, hard-zeroes and snaps. Otherwise compares the
     * natural stop distance v^2/(2a) against the remaining angle to the
     * boundary; if we'd undershoot, scales decel up to land exactly.
     *
     * @param be the block entity
     */
    private static void decelerateWheel(ReactorBlockEntity be) {
        if (be.wheelSpeed < WHEEL_SPEED_EPSILON) {
            be.wheelSpeed = 0f;
            be.wheelAngle = Math.round(be.wheelAngle / WHEEL_CYCLE_PERIOD) * WHEEL_CYCLE_PERIOD;
            return;
        }
        float dRemaining = WHEEL_CYCLE_PERIOD - (be.wheelAngle % WHEEL_CYCLE_PERIOD);
        float naturalStopDist = (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * WHEEL_DECEL);
        float decel = (naturalStopDist < dRemaining)
                ? (be.wheelSpeed * be.wheelSpeed) / (KINEMATIC_HALF * dRemaining)
                : WHEEL_DECEL;
        be.wheelSpeed = Math.max(0f, be.wheelSpeed - decel);
    }

    @Override
    public SlottedCanisterData containerState() {
        return state;
    }

    @Override
    public int maxTopAttachments() {
        return CORNER_SLOTS.size();
    }

    @Override
    public int currentTopAttachments() {
        if (level == null) {
            return 0;
        }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity cbe)) {
            return 0;
        }
        int count = 0;
        for (int slot : INPUT_SLOTS) {
            if (!cbe.getCanister(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public Set<Integer> allowedSlots() {
        return CORNER_SLOTS;
    }

    /**
     * Returns the output canister (may be EMPTY).
     *
     * @return the output canister item stack
     */
    public @NonNull ItemStack getOutputCanister() {
        return state.getCanister(OUTPUT_SLOT);
    }

    /**
     * Inserts a canister into the output slot. Two sync passes by design:
     * {@code setCanister} fires the slot's structure-changed callback (which
     * calls {@link BlockEntitySync#markDirtyAndSync}) before {@code buildHandler}
     * runs, so emission reads 0 then. The explicit second pass runs once the
     * handler is in place and propagates the correct emission.
     *
     * @param stack the canister to insert
     * @return true if inserted
     */
    public boolean insertOutputCanister(ItemStack stack) {
        if (!getOutputCanister().isEmpty()) {
            return false;
        }
        state.slots[OUTPUT_SLOT].setCanister(stack.copyWithCount(1));
        state.slots[OUTPUT_SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        BlockEntitySync.markDirtyAndSync(this);
        return true;
    }

    /**
     * Removes and returns the output canister.
     *
     * @return the removed canister, or EMPTY
     */
    public @NonNull ItemStack removeOutputCanister() {
        ItemStack current = getOutputCanister();
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Write handler state to the item stack so the returned item has
        // accurate fluid data, but don't sync yet - we clear and sync once.
        state.slots[OUTPUT_SLOT].syncHandlerToStack();
        state.slots[OUTPUT_SLOT].clear();
        BlockEntitySync.markDirtyAndSync(this);
        return current;
    }

    /**
     * Resolves the best matching reaction and executes batches.
     *
     * @param level  the server level
     * @param pos    the block position
     * @param bState the block state
     */
    private void tickReaction(Level level, BlockPos pos, BlockState bState) {
        CanisterBlockEntity inputBe = getInputCanisterBe(level, pos);
        if (inputBe == null || !hasOutputCanister()) {
            clearCrafting(level, pos, bState);
            return;
        }
        if (!tryExecuteReaction(inputBe, level, pos, bState)) {
            clearCrafting(level, pos, bState);
        }
    }

    /**
     * Attempts to resolve and execute a reaction batch; returns false if any step fails.
     *
     * @param inputBe the input canister block entity above
     * @param level   the server level
     * @param pos     the reactor block position
     * @param bState  the current block state
     * @return true if a reaction was executed
     */
    private boolean tryExecuteReaction(
            CanisterBlockEntity inputBe, Level level, BlockPos pos, BlockState bState) {
        GooReaction reaction = resolveReaction(inputBe);
        if (reaction == null) {
            return false;
        }
        if (!outputCanAcceptProducts(reaction)) {
            return false;
        }
        int batches = computeBatches(inputBe, reaction);
        if (batches <= 0) {
            return false;
        }
        consumeInputs(inputBe, reaction.inputs(), batches);
        produceOutputs(reaction.outputs(), batches, reaction.rate());
        setChanged();
        setCrafting(level, pos, bState);
        return true;
    }

    /**
     * Returns true if the output slot has a canister to receive products.
     *
     * @return true if an output canister is present
     */
    private boolean hasOutputCanister() {
        return !getOutputCanister().isEmpty();
    }

    /**
     * Returns true if the output canister can accept all products of the
     * reaction. The canister must be empty or already contain the same
     * fluid as every output entry.
     *
     * @param reaction the matched reaction
     * @return true if the output canister is compatible
     */
    private boolean outputCanAcceptProducts(GooReaction reaction) {
        CanisterFluidContent content = CanisterItem.getFluidContent(getOutputCanister());
        if (content.isEmpty()) {
            return true;
        }
        for (GooReaction.FluidEntry entry : reaction.outputs()) {
            if (content.fluid() != entry.fluid()) {
                return false;
            }
        }
        return true;
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
            if (inputsSatisfy(inputBe, reaction)) {
                return reaction;
            }
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
    private int getAvailableFluid(CanisterBlockEntity inputBe, Fluid fluid) {
        int total = 0;
        for (int slot : INPUT_SLOTS) {
            ItemStack stack = inputBe.getCanister(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CanisterFluidContent content = CanisterItem.getFluidContent(stack);
            if (content.fluid() == fluid) {
                total += content.amount();
            }
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
        int minBatches = Integer.MAX_VALUE;
        for (GooReaction.FluidEntry entry : reaction.inputs()) {
            int available = getAvailableFluid(inputBe, entry.fluid());
            int possible = available / entry.amount();
            minBatches = Math.min(minBatches, possible);
        }
        if (minBatches <= 0) {
            return 0;
        }
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
     * Drains a fluid across corner input slots via their fluid handlers.
     * Uses the slot handler's {@code extractFluid} convenience (which
     * opens, commits, and closes its own transaction) so this matches
     * every other extract call site in the canister machinery.
     *
     * @param inputBe the input canister BE
     * @param fluid   the fluid to drain
     * @param amount  total mB to drain
     */
    private void consumeFluid(CanisterBlockEntity inputBe,
                              Fluid fluid, int amount) {
        int remaining = amount;
        for (int slot : INPUT_SLOTS) {
            if (remaining <= 0) {
                break;
            }
            CanisterSlotFluidHandler handler =
                    inputBe.containerState().getSlotFluidHandler(slot);
            if (handler == null) {
                continue;
            }
            int extracted = handler.extractFluid(fluid, remaining, false);
            remaining -= extracted;
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
        CanisterSlotFluidHandler handler = state.getSlotFluidHandler(OUTPUT_SLOT);
        if (handler == null) {
            return;
        }
        for (GooReaction.FluidEntry entry : outputs) {
            int amount = entry.amount() * batches * rate;
            try (var tx = Transaction.openRoot()) {
                handler.insert(0, FluidResource.of(entry.fluid()), amount, tx);
                tx.commit();
            }
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

    /** Re-propagates goo emission after NBT load; the chunk-load light scan
     * ran before {@code loadAdditional}, so any loaded goo content would
     * otherwise stay dark. */
    @Override
    public void onLoad() {
        super.onLoad();
        BlockEntitySync.kickLightingOnLoad(this);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        // Write handler state to item stack without triggering a sync
        // (we're already inside serialization).
        state.slots[OUTPUT_SLOT].syncHandlerToStack();
        if (!getOutputCanister().isEmpty()) {
            output.store(TAG_OUTPUT_CANISTER, ItemStack.CODEC, getOutputCanister());
        }
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        ItemStack loaded = input.read(TAG_OUTPUT_CANISTER, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        state.slots[OUTPUT_SLOT].setCanister(loaded);
        if (!loaded.isEmpty()) {
            state.slots[OUTPUT_SLOT].buildHandler(() -> level != null ? level.getGameTime() : 0L);
        }
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
