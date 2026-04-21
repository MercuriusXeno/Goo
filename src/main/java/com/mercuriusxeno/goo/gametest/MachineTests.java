package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.ReactorBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * Gametests for machine block entities. Exercises placement, slot lifecycle,
 * tick loops, and serialization on real BEs without stubs or mocks.
 */
public final class MachineTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int CENTER_SLOT = 4;
    private static final int SETTLE_TICKS = 2;
    private static final String SHOULD_INSERT = "Canister should insert into empty slot";
    private static final String SHOULD_HAVE_HANDLER = "Slot should have a fluid handler after insertion";
    private static final String SHOULD_BE_EMPTY = "Slot should be empty after removal";
    private static final String HANDLER_SHOULD_CLEAR = "Fluid handler should be null after removal";
    private static final String SHOULD_REMOVE = "removeCanister should return the canister stack";
    private static final String SHOULD_TICK = "Block entity should survive ticking";

    private MachineTests() {}

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
}
