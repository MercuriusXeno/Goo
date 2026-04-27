package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Result of resolving the player's aim target for blob throwing.
 * Sealed hierarchy: entity hit, block face hit, or nothing in range.
 */
public sealed interface TargetResult {

    /**
     * Half-block offset for face center calculations.
     */
    double FACE_CENTER_OFFSET = 0.5;

    /**
     * Singleton for the empty/no-target case.
     */
    TargetResult NONE = new None();

    /**
     * Factory for an entity target.
     *
     * @param e the target entity
     * @return the result
     */
    static TargetResult entity(Entity e) {
        return new EntityTarget(e);
    }

    /**
     * Factory for a block face target.
     *
     * @param pos  the block position
     * @param face the block face direction
     * @return the result
     */
    static TargetResult block(BlockPos pos, Direction face) {
        return new BlockTarget(pos, face, false);
    }

    /**
     * Factory for a granny-arc redirected block target.
     *
     * @param pos the block position
     * @return the result
     */
    static TargetResult grannyArc(BlockPos pos) {
        return new BlockTarget(pos, Direction.UP, true);
    }

    /**
     * Factory for a chain marker target.
     *
     * @param pos the chain marker block position
     * @return the result
     */
    static TargetResult chainMarker(BlockPos pos) {
        return new ChainMarkerTarget(pos);
    }

    /**
     * Factory for a glow crystal target.
     *
     * @param pos  the crystal block position
     * @param face the hit face direction
     * @return the result
     */
    static TargetResult glowCrystal(BlockPos pos, Direction face) {
        return new GlowCrystalTarget(pos, face);
    }

    /**
     * Returns the world-space Vec3 destination for this target. Used by
     * both the arc renderer and the blob flight manager so the endpoint
     * computation is not duplicated.
     *
     * @return the endpoint position, or null for {@link None}
     */
    Vec3 resolveEndpoint();

    /**
     * The player is aiming at a living entity within throw range.
     *
     * @param entity the targeted entity
     */
    record EntityTarget(Entity entity) implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return entity.getBoundingBox().getCenter();
        }
    }

    /**
     * The player is aiming at a specific block face within throw range.
     *
     * @param pos       the targeted block position
     * @param face      the targeted block face
     * @param grannyArc whether to use the boosted arc trajectory
     */
    record BlockTarget(BlockPos pos, Direction face, boolean grannyArc) implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(FACE_CENTER_OFFSET));
        }
    }

    /**
     * The player is aiming at a placed chain marker block, which in the
     * aim-assist system behaves exactly like a living entity (cone scan,
     * sticky retention, sneak bypass). The throw payload still encodes as
     * a block target, but the render path draws an entity-style arc rather
     * than a block-face voxel overlay.
     *
     * @param pos the targeted chain marker block position
     */
    record ChainMarkerTarget(BlockPos pos) implements TargetResult {
        /**
         * Returns the orb center, accounting for the placed face so
         * the arc lands on the visible blob, not above it.
         */
        @Override
        public Vec3 resolveEndpoint() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null
                    && mc.level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
                Direction face = be.getPlacedFace();
                return new Vec3(
                        pos.getX() + FACE_CENTER_OFFSET - face.getStepX() * FACE_CENTER_OFFSET,
                        pos.getY() + FACE_CENTER_OFFSET - face.getStepY() * FACE_CENTER_OFFSET,
                        pos.getZ() + FACE_CENTER_OFFSET - face.getStepZ() * FACE_CENTER_OFFSET);
            }
            return Vec3.atCenterOf(pos);
        }
    }

    /**
     * The player is aiming at a placed glow crystal block. The arc
     * lands at the block center (not the face) so the beam converges
     * on the crystal. Throwing glow goo grows the crystal's size.
     *
     * @param pos  the crystal block position
     * @param face the hit face (for payload encoding)
     */
    record GlowCrystalTarget(BlockPos pos, Direction face) implements TargetResult {
        /**
         * Points at the crystal's visual center, hugging the attachment face.
         */
        @Override
        public Vec3 resolveEndpoint() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                BlockState state = mc.level.getBlockState(pos);
                if (state.getBlock() instanceof GlowCrystalBlock) {
                    Direction facing = state.getValue(GlowCrystalBlock.FACING);
                    return new Vec3(
                            pos.getX() + FACE_CENTER_OFFSET
                                    - facing.getStepX() * FACE_CENTER_OFFSET,
                            pos.getY() + FACE_CENTER_OFFSET
                                    - facing.getStepY() * FACE_CENTER_OFFSET,
                            pos.getZ() + FACE_CENTER_OFFSET
                                    - facing.getStepZ() * FACE_CENTER_OFFSET);
                }
            }
            return Vec3.atCenterOf(pos);
        }

        /**
         * Returns the current crystal size as a 1-based stack equivalent.
         *
         * @return 1 for TINY through 4 for LARGE, or 0 if not a crystal
         */
        public int currentStacks() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return 0;
            }
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() instanceof GlowCrystalBlock) {
                return state.getValue(GlowCrystalBlock.SIZE).ordinal() + 1;
            }
            return 0;
        }
    }

    /**
     * Nothing targetable within throw range.
     */
    record None() implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return null;
        }
    }
}
