package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for canister block interactions via mock player.
 * Exercises CanisterBlockHandlers, CanisterBlock.useItemOn,
 * and the full insertion/pickup interaction paths.
 */
public final class CanisterInteractionTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int CENTER_SLOT = 4;
    private static final String INSERT_SHOULD_FILL = "Shift+canister should insert into the grid";
    private static final String PICKUP_SHOULD_EMPTY = "Right-click should pick up the canister";
    private static final String HAND_SHOULD_EMPTY = "Player hand should be empty after insert";
    private static final String HAND_SHOULD_HAVE = "Player should receive the picked-up canister";
    private static final String BLOCK_SHOULD_REMAIN = "Block should remain with canisters in it";
    private static final String BLOCK_REMOVED = "Block should be removed when last canister is picked up";

    private CanisterInteractionTests() {}

    /**
     * Creates a BlockHitResult targeting the center of the block at BE_POS,
     * hitting the top face. The hit location aims at the center slot.
     *
     * @param helper the gametest helper
     * @return the hit result
     */
    private static BlockHitResult centerHit(GameTestHelper helper) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(
            Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    /**
     * Shift+right-click with a canister item inserts it into the grid
     * via the full useItemOn path through CanisterBlock and CanisterBlockHandlers.
     *
     * @param helper the gametest helper
     */
    public static void shiftClickInserts(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        // Seed the grid with one canister so the block exists
        be.insertCanister(0, new ItemStack(GooItems.CANISTER.get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CANISTER.get()));

        helper.useBlock(BE_POS, player, centerHit(helper));

        helper.assertFalse(be.containerState().getCanister(CENTER_SLOT).isEmpty(), INSERT_SHOULD_FILL);
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), HAND_SHOULD_EMPTY);
        helper.succeed();
    }

    /**
     * Right-click without shift picks up the targeted canister, giving it
     * to the player and leaving the block intact if other slots are occupied.
     *
     * @param helper the gametest helper
     */
    public static void clickPicksUp(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(0, new ItemStack(GooItems.CANISTER.get()), false);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CANISTER.get()));

        helper.useBlock(BE_POS, player, centerHit(helper));

        helper.assertTrue(be.containerState().getCanister(CENTER_SLOT).isEmpty(), PICKUP_SHOULD_EMPTY);
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), BE_POS);
        helper.succeed();
    }

    /**
     * Picking up the last canister removes the block entirely.
     *
     * @param helper the gametest helper
     */
    public static void lastPickupRemovesBlock(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(BE_POS, player, centerHit(helper));

        helper.assertBlockNotPresent(GooBlocks.CANISTER.get(), BE_POS);
        helper.succeed();
    }

    /**
     * Empty-hand right-click (useWithoutItem path) also picks up.
     *
     * @param helper the gametest helper
     */
    public static void emptyHandPicksUp(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity be = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        be.insertCanister(0, new ItemStack(GooItems.CANISTER.get()), false);
        be.insertCanister(CENTER_SLOT, new ItemStack(GooItems.CANISTER.get()), false);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(BE_POS, player, centerHit(helper));

        helper.assertTrue(be.containerState().getCanister(CENTER_SLOT).isEmpty(), PICKUP_SHOULD_EMPTY);
        helper.succeed();
    }
}
