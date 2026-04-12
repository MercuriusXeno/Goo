package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Result of resolving the player's aim target for blob throwing.
 * Sealed hierarchy: entity hit, block face hit, or nothing in range.
 */
public sealed interface TargetResult {

    /** Half-block offset for face center calculations. */
    double FACE_CENTER_OFFSET = 0.5;

    /** Singleton for the empty/no-target case. */
    TargetResult NONE = new None();

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
        @Override
        public Vec3 resolveEndpoint() {
            return Vec3.atCenterOf(pos);
        }
    }

    /** Nothing targetable within throw range. */
    record None() implements TargetResult {
        @Override
        public Vec3 resolveEndpoint() {
            return null;
        }
    }

    /**
     * Factory for an entity target.
     *
     * @param e the target entity
     * @return the result
     */
    static TargetResult entity(Entity e) { return new EntityTarget(e); }

    /**
     * Factory for a block face target.
     *
     * @param pos the block position
     * @param face the block face direction
     * @return the result
     */
    static TargetResult block(BlockPos pos, Direction face) { return new BlockTarget(pos, face, false); }

    /**
     * Factory for a granny-arc redirected block target.
     *
     * @param pos the block position
     * @return the result
     */
    static TargetResult grannyArc(BlockPos pos) { return new BlockTarget(pos, Direction.UP, true); }

    /**
     * Factory for a chain marker target.
     *
     * @param pos the chain marker block position
     * @return the result
     */
    static TargetResult chainMarker(BlockPos pos) { return new ChainMarkerTarget(pos); }
}
