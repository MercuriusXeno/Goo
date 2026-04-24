package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.WorldEffects;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for EffectBlockPlacement via the WorldEffects dispatch.
 * Covers the blob-impact placement path: WorldEffects -> *Effect ->
 * EffectBlockPlacement -> ChainPlacementRules -> chain marker creation.
 */
public final class PlacementTests {

    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos AIR_POS = new BlockPos(1, 1, 2);
    private static final String MARKER_PLACED = "Chain marker should be placed";
    private static final String MARKER_STACKED = "Chain marker should stack on second hit";
    /** Z spacing between consecutive type tests in otherTypesPlaceMarker. */
    private static final int TYPE_Z_SPACING = 2;

    private PlacementTests() {}

    /**
     * Hitting a stone block with blaze goo places a chain marker in the
     * adjacent air block. Exercises the full placement dispatch path.
     *
     * @param helper the gametest helper
     */
    public static void blazePlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooType.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting a stone block with rock goo places a chain marker.
     *
     * @param helper the gametest helper
     */
    public static void rockPlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooType.ROCK, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting a stone block with frost goo places a chain marker.
     *
     * @param helper the gametest helper
     */
    public static void frostPlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooType.FROST, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting the same position twice with blaze stacks the existing marker
     * instead of placing a second one. Exercises the STACK decision path.
     *
     * @param helper the gametest helper
     */
    public static void doubleHitStacks(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        BlockPos abs = helper.absolutePos(WALL_POS);
        WorldEffects.apply(helper.getLevel(), abs, GooType.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        WorldEffects.apply(helper.getLevel(), abs, GooType.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Crystal, metal, nether, unstable, glow all place markers via the
     * same path. A single combined test verifies they all succeed.
     *
     * @param helper the gametest helper
     */
    public static void otherTypesPlaceMarker(GameTestHelper helper) {
        GooType[] types = {
            GooType.CRYSTAL, GooType.METAL, GooType.NETHER,
            GooType.UNSTABLE, GooType.GLOW
        };
        int z = TYPE_Z_SPACING;
        for (GooType type : types) {
            BlockPos wall = new BlockPos(1, 1, z);
            BlockPos air = new BlockPos(1, 1, z + 1);
            helper.setBlock(wall, Blocks.STONE);
            WorldEffects.apply(helper.getLevel(), helper.absolutePos(wall),
                type, Direction.SOUTH);
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), air);
            z += TYPE_Z_SPACING;
        }
        helper.succeed();
    }
}
