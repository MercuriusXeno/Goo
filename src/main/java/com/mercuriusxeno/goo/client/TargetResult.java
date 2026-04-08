package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/**
 * Result of resolving the player's aim target for blob throwing.
 * Sealed hierarchy: entity hit, block face hit, or nothing in range.
 */
public sealed interface TargetResult {

    /** Singleton for the empty/no-target case. */
    TargetResult NONE = new None();

    /**
     * The player is aiming at a living entity within throw range.
     *
     * @param entity the targeted entity
     */
    record EntityTarget(Entity entity) implements TargetResult {}

    /**
     * The player is aiming at a specific block face within throw range.
     *
     * @param pos       the targeted block position
     * @param face      the targeted block face
     * @param grannyArc whether to use the boosted arc trajectory
     */
    record BlockTarget(BlockPos pos, Direction face, boolean grannyArc) implements TargetResult {}

    /** Nothing targetable within throw range. */
    record None() implements TargetResult {}

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
}
