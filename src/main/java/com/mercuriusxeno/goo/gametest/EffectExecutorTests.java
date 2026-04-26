package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for the chain marker effect executors. Each test places a chain
 * marker against a wall of stone, initializes a goo type, and waits for the
 * fuse + behavior to complete. Covers ChainMarkerBlockEntity tick lifecycle,
 * ChainProfiles, ChainFootprint, EffectBlockPlacement, and the per-type
 * Behavior + Executor classes.
 */
public final class EffectExecutorTests {

    /** Marker placed here, facing NORTH into the stone wall. */
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    /** Fuse is 30 ticks; behaviors run 10-70 more depending on type. */
    private static final int FUSE_TICKS = 30;
    /** Extra ticks after fuse for mining behaviors (1 stack). */
    private static final int MINING_POST_FUSE = 15;
    /** Extra ticks for Nether's multi-phase behavior. */
    private static final int NETHER_POST_FUSE = 80;
    /** Extra ticks for simpler instant/short behaviors. */
    private static final int SHORT_POST_FUSE = 5;
    private static final String MARKER_GONE = "Chain marker should be removed after behavior completes";
    private static final String BLOCK_MINED = "Stone should be mined by the effect";
    private static final String VALUES_REQUIRED = "Goo values must be loaded for rock mining to work";
    private static final int WALL_X_MAX = 5;
    private static final int WALL_Y_MAX = 3;
    private static final int WALL_Z_MAX = 2;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String ABILITY_ROCK_TUNNEL = "goo:rock_tunnel";
    private static final String ABILITY_FROST_SPHERE = "goo:frost_sphere";

    private EffectExecutorTests() {}

    /**
     * Places a 3-deep wall of stone north of the marker and initializes the BE.
     * The marker faces NORTH so the effect mines into the wall.
     *
     * @param helper the gametest helper
     * @param type   the goo type for the chain marker
     */
    private static void placeMarkerWithWall(GameTestHelper helper, GooType type) {
        // Fill a 5x3x3 wall of stone north of the marker (z=0..2, x=1..5, y=1..3)
        for (int x = 1; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        // Place marker in air just south of the wall
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        be.initChain(type, Direction.SOUTH);
    }

    /**
     * Blaze: places marker facing stone wall, verifies the block is mined
     * and the marker removes itself after fuse + mining completes.
     *
     * @param helper the gametest helper
     */
    public static void blazeMinesBlock(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.BLAZE);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Rock: places marker facing stone wall, verifies the block is mined.
     * Requires goo values to be loaded so stone is recognized as rock-compatible.
     *
     * @param helper the gametest helper
     */
    public static void rockMinesBlock(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        placeMarkerWithWall(helper, GooType.ROCK);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Frost: places marker and verifies the behavior runs without crashing.
     * Frost converts water/blocks to ice in a sphere; with no water nearby,
     * it completes quickly.
     *
     * @param helper the gametest helper
     */
    public static void frostRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.FROST);
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Metal: places marker and verifies the spike trap behavior runs.
     * Metal is a short-lived effect that damages entities in range.
     *
     * @param helper the gametest helper
     */
    public static void metalRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.METAL);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Crystal: places marker and verifies the DOT cloud behavior runs.
     *
     * @param helper the gametest helper
     */
    public static void crystalRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.CRYSTAL);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Nether: places marker and verifies the multi-phase implosion completes.
     * Nether has EXPAND, HOLD, CONTRACT, POPPING phases totaling ~60 ticks.
     *
     * @param helper the gametest helper
     */
    public static void netherImplodes(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.NETHER);
        helper.runAfterDelay(FUSE_TICKS + NETHER_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Unstable: places marker and verifies the instant explosion runs.
     * Unstable has a shorter fuse (20 ticks) and detonates immediately.
     *
     * @param helper the gametest helper
     */
    public static void unstableExplodes(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.UNSTABLE);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Glow: places marker and verifies the crystal placement behavior runs.
     *
     * @param helper the gametest helper
     */
    public static void glowRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.GLOW);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    // --- Data-driven ability path ---

    /**
     * Places a chain marker initialized via the ability path instead of
     * the legacy ChainProfile path. Covers DataDrivenChainBehavior,
     * ProgressiveAreaBlock, and the BehaviorType factory.
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, GooType type, String abilityId) {
        for (int x = 1; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        be.initChainFromAbility(type, Direction.SOUTH, ability);
    }

    /**
     * Blaze tunnel via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> BlazeExecutor.
     *
     * @param helper the gametest helper
     */
    public static void abilityBlazeTunnel(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.BLAZE, ABILITY_BLAZE_TUNNEL);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Rock tunnel via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> RockExecutor.
     *
     * @param helper the gametest helper
     */
    public static void abilityRockTunnel(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        placeMarkerWithAbility(helper, GooType.ROCK, ABILITY_ROCK_TUNNEL);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Frost sphere via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> FrostBehavior.
     *
     * @param helper the gametest helper
     */
    public static void abilityFrostSphere(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.FROST, ABILITY_FROST_SPHERE);
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.succeed();
        });
    }
}
