package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.FrostFieldBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Chain-marker and frost-field world effects extracted from WorldEffects.
 * Rock and blaze place chain markers; frost places a freeze field with stacking.
 */
final class ChainAndFrostEffects {

    /** Block update flags for setBlock calls. */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private ChainAndFrostEffects() {}

    /**
     * Rock: chain implosion. Places a chain marker on the hit face.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void rockImplosion(Level level, BlockPos pos,
                              @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeOrStackChain(level, pos, targetFace, GooType.ROCK);
    }

    /**
     * Blaze: chain explosion. Places a chain marker on the hit face.
     * Additional blobs during the fuse window stack up to 4 for 3/5/7/9 radius.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void blazeExplosion(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeOrStackChain(level, pos, targetFace, GooType.BLAZE);
    }

    /**
     * Nether: chain conversion. Places a chain marker on the hit face. On
     * fuse expiry, blocks with a registered goo value in the radius dissolve
     * into blob items. Blocks without a goo value are left untouched; the
     * goo value registry is the sole gate, with no hardness check and no
     * vanilla fallback.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void netherConvert(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        placeOrStackChain(level, pos, targetFace, GooType.NETHER);
    }

    /**
     * Frost: instant freeze + persistent melt-resist field. Executes the
     * freeze immediately, then places (or stacks onto) a FrostFieldBlock
     * that prevents packed ice from being swapped back to regular ice
     * until the field duration expires.
     *
     * @param level      the current level
     * @param pos        the target block position
     * @param targetFace the face that was hit, or null
     */
    static void frostFreeze(Level level, BlockPos pos,
                            @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel serverLevel)) { return; }

        Direction resolvedFace = targetFace == null ? Direction.UP : targetFace;
        BlockPos fieldPos = pos.relative(resolvedFace);

        if (tryStackAndRefreeze(serverLevel, pos, fieldPos)) { return; }
        freezeAndPlaceField(serverLevel, pos, fieldPos);
    }

    /**
     * Attempts to stack onto an existing frost field at either position, refreezing if found.
     *
     * @param level    the server level
     * @param pos      the hit block position
     * @param fieldPos the face-adjacent position
     * @return true if an existing field was stacked onto
     */
    private static boolean tryStackAndRefreeze(ServerLevel level, BlockPos pos, BlockPos fieldPos) {
        if (tryStackFrostField(level, pos)) {
            refreeze(level, pos);
            return true;
        }
        if (tryStackFrostField(level, fieldPos)) {
            refreeze(level, fieldPos);
            return true;
        }
        return false;
    }

    /**
     * Executes the initial freeze and places a new frost field block in air.
     *
     * @param level    the server level
     * @param pos      the hit block position
     * @param fieldPos the face-adjacent position
     */
    private static void freezeAndPlaceField(ServerLevel level, BlockPos pos, BlockPos fieldPos) {
        int radius = EffectMath.computeFreezeRadius(1);
        FrostExecutor.execute(level, pos, radius);

        BlockPos placePos = level.getBlockState(fieldPos).isAir() ? fieldPos : pos;
        if (!level.getBlockState(placePos).isAir()) { return; }
        placeFrostField(level, placePos);
    }

    /**
     * Places a frost field block and initializes its entity with stack count 1.
     *
     * @param level the server level
     * @param pos   the position to place at
     */
    private static void placeFrostField(Level level, BlockPos pos) {
        level.setBlock(pos, GooBlocks.FROST_FIELD.get().defaultBlockState(), BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be) {
            be.initField(1);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────

    /**
     * Stacks onto an existing chain marker if the hit block (or the
     * face-adjacent block) already has one of the same type. Otherwise
     * places a new marker on the hit face. Shared by all chain effects.
     *
     * @param level    the current level
     * @param hitBlock the block that was hit
     * @param face     the face that was hit, or null
     * @param type     the goo type for the chain marker
     */
    private static void placeOrStackChain(Level level, BlockPos hitBlock,
            @Nullable Direction face, GooType type) {
        if (tryStackExisting(level, hitBlock, type)) { return; }

        Direction resolvedFace = face == null ? Direction.UP : face;
        BlockPos placePos = hitBlock.relative(resolvedFace);

        if (tryStackExisting(level, placePos, type)) { return; }
        placeNewChainMarker(level, placePos, type, resolvedFace);
    }

    /**
     * Places a new chain marker in air and initializes its entity.
     *
     * @param level the current level
     * @param pos   the placement position
     * @param type  the goo type for the marker
     * @param face  the face direction for the marker
     */
    private static void placeNewChainMarker(Level level, BlockPos pos,
            GooType type, Direction face) {
        if (!level.getBlockState(pos).isAir()) { return; }
        level.setBlock(pos, GooBlocks.CHAIN_MARKER.get().defaultBlockState(), BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            be.initChain(type, face);
        }
    }

    /**
     * Tries to stack onto an existing same-type chain marker.
     *
     * @param level the current level
     * @param pos   the block position to check
     * @param type  the expected goo type
     * @return true if stacking succeeded
     */
    private static boolean tryStackExisting(Level level, BlockPos pos, GooType type) {
        if (!level.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get())) { return false; }
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && be.getGooType() == type) {
            be.tryStack();
            return true;
        }
        return false;
    }

    /**
     * Tries to stack onto an existing frost field at the given position.
     *
     * @param level the current level
     * @param pos   the block position to check
     * @return true if stacking succeeded
     */
    private static boolean tryStackFrostField(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(GooBlocks.FROST_FIELD.get())
                && level.getBlockEntity(pos) instanceof FrostFieldBlockEntity be
                && be.tryStack();
    }

    /**
     * Re-executes the freeze at the field's updated radius after stacking.
     *
     * @param level    the server level
     * @param fieldPos the frost field block position
     */
    private static void refreeze(ServerLevel level, BlockPos fieldPos) {
        if (level.getBlockEntity(fieldPos) instanceof FrostFieldBlockEntity be) {
            FrostExecutor.execute(level, fieldPos, be.getRadius());
        }
    }
}
