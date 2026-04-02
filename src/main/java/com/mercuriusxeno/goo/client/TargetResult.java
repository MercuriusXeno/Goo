package com.mercuriusxeno.goo.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;

/**
 * Result of resolving the player's aim target for blob throwing.
 * Sealed hierarchy: entity hit, block face hit, or nothing in range.
 */
public sealed interface TargetResult {

    /** The player is aiming at a living entity within throw range. */
    record EntityTarget(Entity entity) implements TargetResult {}

    /** The player is aiming at a specific block face within throw range. */
    record BlockTarget(BlockPos pos, Direction face, boolean grannyArc) implements TargetResult {}

    /** Nothing targetable within throw range. */
    record None() implements TargetResult {}

    /** Singleton for the empty/no-target case. */
    TargetResult NONE = new None();

    /** Factory for an entity target. */
    static TargetResult entity(Entity e) { return new EntityTarget(e); }

    /** Factory for a block face target. */
    static TargetResult block(BlockPos pos, Direction face) { return new BlockTarget(pos, face, false); }

    /** Factory for a granny-arc redirected block target. */
    static TargetResult grannyArc(BlockPos pos) { return new BlockTarget(pos, Direction.UP, true); }
}
