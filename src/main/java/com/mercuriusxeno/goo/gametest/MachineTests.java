package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.SlottedCanisterData;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

/**
 * Gametests for machine block entities. Exercises placement, slot lifecycle,
 * tick loops, and serialization on real BEs without stubs or mocks.
 */
public final class MachineTests {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos INPUT_POS = BE_POS.above();
    private static final int CENTER_SLOT = 4;
    private static final int SETTLE_TICKS = 2;
    private static final int REACTION_TICKS = 5;
    private static final int INPUT_AMOUNT = 1000;
    private static final int TEST_VOLUME = 500;
    private static final int DOUBLED_VOLUME = TEST_VOLUME * 2;
    /**
     * Corner slot index for second input canister (top-right of 3x3).
     */
    private static final int CORNER_SLOT_2 = 2;

    private static final String SHOULD_INSERT = "Canister should insert into empty slot";
    private static final String SHOULD_HAVE_HANDLER = "Slot should have a fluid handler after insertion";
    private static final String SHOULD_BE_EMPTY = "Slot should be empty after removal";
    private static final String HANDLER_SHOULD_CLEAR = "Fluid handler should be null after removal";
    private static final String SHOULD_REMOVE = "removeCanister should return the canister stack";
    private static final String SHOULD_TICK = "Block entity should survive ticking";
    private static final String OUTPUT_HANDLER_EXISTS = "Output handler should exist";
    private static final String OUTPUT_SHOULD_BE_BLAZE = "Output should be blaze goo";
    private static final String OUTPUT_AMOUNT_POSITIVE = "Output amount should be positive";
    private static final String REDSTONE_HALTS_OUTPUT = "Output should be empty when redstone halts reactor";
    private static final String INPUT_BLAZE_DRAINED = "Reactor should drain the blaze input canister";
    private static final String INPUT_LEAF_DRAINED = "Reactor should drain the leaf input canister";
    private static final String INPUT_BLAZE_STACK_DRAINED =
            "Blaze input ItemStack should reflect drain (HUD reads from stack)";
    private static final String INPUT_LEAF_STACK_DRAINED =
            "Leaf input ItemStack should reflect drain (HUD reads from stack)";
    private static final String OUTPUT_STACK_FLUID_PRESENT =
            "Output ItemStack should hold the produced fluid (BER reads from stack)";
    private static final String OUTPUT_STACK_AMOUNT_POSITIVE =
            "Output ItemStack amount should be positive";
    private static final String SHOULD_INSERT_FULL = "Should insert full volume into empty canister";
    private static final String HANDLER_EXISTS = "Handler should exist";
    private static final String HANDLER_HOLDS_BLAZE = "Handler should hold blaze goo";
    private static final String HANDLER_AMOUNT_MATCHES = "Handler amount should match inserted volume";
    private static final String SHOULD_EXTRACT_FULL = "Should extract full volume back";
    private static final String HANDLER_EMPTY_AFTER = "Handler should be empty after full extraction";
    private static final String ROUTE_ACCEPTS_FULL = "routeFluid should accept full volume";
    private static final String SLOT_0_HANDLER_EXISTS = "Slot 0 handler should exist";
    private static final String SLOT_0_HAS_ROUTED = "Slot 0 should have original + routed volume";
    private static final String SLOT_2_HANDLER_EXISTS = "Slot 2 handler should exist";
    private static final String SLOT_2_STILL_EMPTY = "Slot 2 should be empty when matching slot absorbed all";
    private static final String HANDLER_REBUILT = "Handler should be rebuilt after load";
    private static final String FLUID_SURVIVES = "Fluid type should survive round-trip";
    private static final String AMOUNT_SURVIVES = "Fluid amount should survive round-trip";

    private MachineTests() {
    }

    // --- Canister ---

    /**
     * Placing a canister block and inserting a canister item into the center
     * slot should create a fluid handler for that slot.
     *
     * @param helper the gametest helper
     */
    public static void canisterInsertCreatesHandler(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        helper.assertTrue(be.insertCanister(CENTER_SLOT, canister, false), SHOULD_INSERT);
        helper.assertTrue(
                be.containerState().getSlotFluidHandler(CENTER_SLOT) != null,
                SHOULD_HAVE_HANDLER);
        helper.succeed();
    }

    /**
     * Removing a canister from a slot should clear the handler and return
     * the canister item.
     *
     * @param helper the gametest helper
     */
    public static void canisterRemoveClearsHandler(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        ItemStack removed = be.removeCanister(CENTER_SLOT);
        helper.assertFalse(removed.isEmpty(), SHOULD_REMOVE);
        helper.assertTrue(
                be.containerState().getCanister(CENTER_SLOT).isEmpty(),
                SHOULD_BE_EMPTY);
        helper.assertTrue(
                be.containerState().getSlotFluidHandler(CENTER_SLOT) == null,
                HANDLER_SHOULD_CLEAR);
        helper.succeed();
    }

    /**
     * A canister block with an inserted canister should survive server ticks
     * without crashing (exercises tickPushers path).
     *
     * @param helper the gametest helper
     */
    public static void canisterTicksWithSlot(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(!be.isRemoved(), SHOULD_TICK);
            helper.succeed();
        });
    }

    /**
     * Breaking a canister block with occupied slots should not crash
     * (exercises setRemoved, dispose, deregister lifecycle).
     *
     * @param helper the gametest helper
     */
    public static void canisterBreakWithSlotIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
        });
    }

    // --- Reactor ---

    /**
     * Placing a reactor and ticking it should not crash. The reactor has
     * complex tick logic with no items needed to exercise the idle path.
     *
     * @param helper the gametest helper
     */
    public static void reactorIdleTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);
        helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
    }

    /**
     * Breaking an idle reactor should not crash.
     *
     * @param helper the gametest helper
     */
    public static void reactorBreakIsSafe(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        helper.runAfterDelay(1, () -> {
            helper.destroyBlock(BE_POS);
            helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
        });
    }

    /**
     * A reactor with valid inputs should consume them and produce output.
     * Uses the blaze_from_blaze_leaf reaction: 1 blaze + 1 leaf -> 2 blaze.
     *
     * @param helper the gametest helper
     */
    public static void reactorProcessesReaction(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);

        helper.setBlock(INPUT_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity inputBe = helper.getBlockEntity(INPUT_POS, CanisterBlockEntity.class);
        Fluid blazeFluid = GooFluids.SOURCES.get(GooType.BLAZE).get();
        Fluid leafFluid = GooFluids.SOURCES.get(GooType.LEAF).get();
        insertFilledCanister(inputBe, 0, blazeFluid, INPUT_AMOUNT);
        insertFilledCanister(inputBe, CORNER_SLOT_2, leafFluid, INPUT_AMOUNT);

        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));

        helper.runAfterDelay(REACTION_TICKS,
                () -> assertReactionConsumed(helper, reactor, inputBe, blazeFluid));
    }

    /**
     * Verifies output present, both inputs drained on the handler, and both
     * input ItemStacks reflect the drain (the HUD/billboard reads stack
     * NBT, not the handler).
     *
     * @param helper     the gametest helper
     * @param reactor    the reactor block entity
     * @param inputBe    the input canister block entity above
     * @param blazeFluid the expected output fluid
     */
    private static void assertReactionConsumed(GameTestHelper helper,
            ReactorBlockEntity reactor, CanisterBlockEntity inputBe, Fluid blazeFluid) {
        assertOutputHandler(helper, reactor, blazeFluid);
        assertInputsDrained(helper, inputBe);
        assertOutputStackHasFluid(helper, reactor, blazeFluid);
        helper.succeed();
    }

    /**
     * @param helper     the gametest helper
     * @param reactor    the reactor block entity
     * @param blazeFluid the expected fluid
     */
    private static void assertOutputHandler(GameTestHelper helper,
            ReactorBlockEntity reactor, Fluid blazeFluid) {
        var handler = reactor.containerState().getSlotFluidHandler(
                ReactorBlockEntity.OUTPUT_SLOT);
        helper.assertTrue(handler != null, OUTPUT_HANDLER_EXISTS);
        helper.assertTrue(handler.getFluid() == blazeFluid, OUTPUT_SHOULD_BE_BLAZE);
        helper.assertTrue(handler.getAmount() > 0, OUTPUT_AMOUNT_POSITIVE);
    }

    /**
     * @param helper  the gametest helper
     * @param inputBe the input canister BE above the reactor
     */
    private static void assertInputsDrained(GameTestHelper helper,
            CanisterBlockEntity inputBe) {
        var inBlaze = inputBe.containerState().getSlotFluidHandler(0);
        var inLeaf = inputBe.containerState().getSlotFluidHandler(CORNER_SLOT_2);
        helper.assertTrue(inBlaze != null && inBlaze.getAmount() < INPUT_AMOUNT,
                INPUT_BLAZE_DRAINED);
        helper.assertTrue(inLeaf != null && inLeaf.getAmount() < INPUT_AMOUNT,
                INPUT_LEAF_DRAINED);
        CanisterFluidContent blazeStackContent = CanisterItem.getFluidContent(
                inputBe.containerState().getCanister(0));
        CanisterFluidContent leafStackContent = CanisterItem.getFluidContent(
                inputBe.containerState().getCanister(CORNER_SLOT_2));
        helper.assertTrue(blazeStackContent.amount() < INPUT_AMOUNT,
                INPUT_BLAZE_STACK_DRAINED);
        helper.assertTrue(leafStackContent.amount() < INPUT_AMOUNT,
                INPUT_LEAF_STACK_DRAINED);
    }

    /**
     * @param helper     the gametest helper
     * @param reactor    the reactor block entity
     * @param blazeFluid the expected fluid in the output stack
     */
    private static void assertOutputStackHasFluid(GameTestHelper helper,
            ReactorBlockEntity reactor, Fluid blazeFluid) {
        CanisterFluidContent outputStackContent = CanisterItem.getFluidContent(
                reactor.getOutputCanister());
        helper.assertTrue(outputStackContent.fluid() == blazeFluid,
                OUTPUT_STACK_FLUID_PRESENT);
        helper.assertTrue(outputStackContent.amount() > 0,
                OUTPUT_STACK_AMOUNT_POSITIVE);
    }

    /**
     * A reactor receiving redstone should not process reactions even with
     * valid inputs.
     *
     * @param helper the gametest helper
     */
    public static void reactorRedstoneHalts(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.REACTOR.get());
        ReactorBlockEntity reactor = helper.getBlockEntity(BE_POS, ReactorBlockEntity.class);

        helper.setBlock(INPUT_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity inputBe = helper.getBlockEntity(INPUT_POS, CanisterBlockEntity.class);
        Fluid blazeFluid = GooFluids.SOURCES.get(GooType.BLAZE).get();
        Fluid leafFluid = GooFluids.SOURCES.get(GooType.LEAF).get();
        insertFilledCanister(inputBe, 0, blazeFluid, INPUT_AMOUNT);
        insertFilledCanister(inputBe, CORNER_SLOT_2, leafFluid, INPUT_AMOUNT);

        reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));

        // Place redstone block adjacent to power the reactor via neighborChanged
        helper.setBlock(BE_POS.east(), Blocks.REDSTONE_BLOCK);

        helper.runAfterDelay(REACTION_TICKS, () -> {
            var handler = reactor.containerState().getSlotFluidHandler(
                    ReactorBlockEntity.OUTPUT_SLOT);
            helper.assertTrue(handler != null, OUTPUT_HANDLER_EXISTS);
            helper.assertTrue(handler.getAmount() == 0, REDSTONE_HALTS_OUTPUT);
            helper.succeed();
        });
    }

    // --- Canister fluid operations ---

    /**
     * Inserting fluid into a canister slot handler and extracting it back
     * should return the same amount. Exercises SlottedCanisterState.insertFluid,
     * extractFluid, and CanisterSlotFluidHandler internals.
     *
     * @param helper the gametest helper
     */
    public static void canisterFluidInsertExtract(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);

        SlottedCanisterData state = be.containerState();
        Fluid blazeFluid = GooFluids.SOURCES.get(GooType.BLAZE).get();

        int inserted = state.insertFluid(CENTER_SLOT, blazeFluid, TEST_VOLUME);
        helper.assertTrue(inserted == TEST_VOLUME, SHOULD_INSERT_FULL);

        var handler = state.getSlotFluidHandler(CENTER_SLOT);
        helper.assertTrue(handler != null, HANDLER_EXISTS);
        helper.assertTrue(handler.getFluid() == blazeFluid, HANDLER_HOLDS_BLAZE);
        helper.assertTrue(handler.getAmount() == TEST_VOLUME, HANDLER_AMOUNT_MATCHES);

        int extracted = state.extractFluid(CENTER_SLOT, blazeFluid, TEST_VOLUME);
        helper.assertTrue(extracted == TEST_VOLUME, SHOULD_EXTRACT_FULL);
        helper.assertTrue(handler.isEmpty(), HANDLER_EMPTY_AFTER);
        helper.succeed();
    }

    /**
     * routeFluid should fill a matching slot before an empty slot.
     * Exercises SlottedCanisterState.routeFluid, distributeAcrossSlots, and
     * the two-pass distribution logic.
     *
     * @param helper the gametest helper
     */
    public static void canisterFluidRouting(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        Fluid blazeFluid = GooFluids.SOURCES.get(GooType.BLAZE).get();

        // Slot 0: already holds some blaze goo
        insertFilledCanister(be, 0, blazeFluid, TEST_VOLUME);
        // Slot 2: empty canister (can accept any fluid)
        be.insertCanister(CORNER_SLOT_2, new ItemStack(GooItems.CANISTER.get()), false);

        SlottedCanisterData state = be.containerState();
        int routed = state.routeFluid(blazeFluid, TEST_VOLUME);
        helper.assertTrue(routed == TEST_VOLUME, ROUTE_ACCEPTS_FULL);

        // Slot 0 should have received the routed fluid (matching pass first)
        var handler0 = state.getSlotFluidHandler(0);
        helper.assertTrue(handler0 != null, SLOT_0_HANDLER_EXISTS);
        helper.assertTrue(handler0.getAmount() == DOUBLED_VOLUME, SLOT_0_HAS_ROUTED);

        // Slot 2 should still be empty (matching pass consumed everything)
        var handler2 = state.getSlotFluidHandler(CORNER_SLOT_2);
        helper.assertTrue(handler2 != null, SLOT_2_HANDLER_EXISTS);
        helper.assertTrue(handler2.isEmpty(), SLOT_2_STILL_EMPTY);
        helper.succeed();
    }

    /**
     * A canister block with fluid should survive a save/load round-trip.
     * Exercises CanisterEntitySerializer.saveCanisterList, loadCanisterList,
     * saveStreamState, loadStreamState, and handler rebuild.
     *
     * @param helper the gametest helper
     */
    public static void canisterSurvivesRoundTrip(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        Fluid blazeFluid = GooFluids.SOURCES.get(GooType.BLAZE).get();
        insertFilledCanister(be, CENTER_SLOT, blazeFluid, TEST_VOLUME);

        // Snapshot the BE state as NBT
        CompoundTag saved = be.getUpdateTag(helper.getLevel().registryAccess());

        // Destroy and re-place the block to get a fresh BE
        helper.destroyBlock(BE_POS);
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity restored = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);

        // Load the saved state into the fresh BE
        try (var reporter = new ProblemReporter.ScopedCollector(
                restored.problemPath(), LOGGER)) {
            restored.loadCustomOnly(TagValueInput.create(
                    reporter, helper.getLevel().registryAccess(), saved));
        }

        // Verify the fluid survived the round-trip
        var handler = restored.containerState().getSlotFluidHandler(CENTER_SLOT);
        helper.assertTrue(handler != null, HANDLER_REBUILT);
        helper.assertTrue(handler.getFluid() == blazeFluid, FLUID_SURVIVES);
        helper.assertTrue(handler.getAmount() == TEST_VOLUME, AMOUNT_SURVIVES);
        helper.succeed();
    }

    // --- Plexer ---

    /**
     * Placing a plexer and ticking it should not crash.
     *
     * @param helper the gametest helper
     */
    public static void plexerIdleTick(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        helper.runAfterDelay(SETTLE_TICKS, helper::succeed);
    }

    /**
     * Creates a canister item with the given fluid content and inserts it
     * into a slot on a canister block entity.
     *
     * @param be     the canister block entity
     * @param slot   the slot index
     * @param fluid  the fluid to fill with
     * @param amount the amount in mB
     */
    private static void insertFilledCanister(CanisterBlockEntity be,
                                             int slot, Fluid fluid, int amount) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister, new CanisterFluidContent(fluid, amount));
        be.insertCanister(slot, canister, false);
    }
}
