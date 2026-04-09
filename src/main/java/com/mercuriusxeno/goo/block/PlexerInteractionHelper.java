package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.item.CanisterItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Cutaway hit-testing, target-item management, and item ejection for the plexer.
 * All methods are stateless helpers called from PlexerBlock.
 */
final class PlexerInteractionHelper {

    /** Overlay prefix for target-set feedback. */
    private static final String TARGET_PREFIX = "Target: ";
    /** Overlay message when target is cleared. */
    private static final String TARGET_CLEARED = "Target cleared";

    // -- Eject point (model space, south-facing) --
    /** Cutaway center X in block-relative coords. */
    private static final double EJECT_CENTER_X = 8.0 / 16.0;
    /** Cutaway center Y in block-relative coords. */
    private static final double EJECT_CENTER_Y = 10.5 / 16.0;
    /** Cutaway opening Z in block-relative coords. */
    private static final double EJECT_CENTER_Z = 15.0 / 16.0;
    /** Ejected item outward speed. */
    private static final double EJECT_SPEED = 0.15;
    /** Ejected item upward velocity. */
    private static final double EJECT_LIFT = 0.05;

    /** Cutaway volume in model space (south-facing): x in [5,11], y in [8,13], z in [12,16]. */
    private static final double CUTAWAY_MIN_X = 5.0 / 16.0;
    private static final double CUTAWAY_MAX_X = 11.0 / 16.0;
    private static final double CUTAWAY_MIN_Y = 8.0 / 16.0;
    private static final double CUTAWAY_MAX_Y = 13.0 / 16.0;
    private static final double CUTAWAY_MIN_Z = 12.0 / 16.0;

    private PlexerInteractionHelper() { }

    /** Returns true if the item interaction should pass through (canister or non-cutaway click).
     *
     * @param stack     the held item
     * @param state     the block state
     * @param pos       the block position
     * @param hitResult the ray trace hit result
     * @return true if the interaction should pass
     */
    static boolean shouldPassItemInteraction(ItemStack stack, BlockState state,
            BlockPos pos, BlockHitResult hitResult) {
        return stack.getItem() instanceof CanisterItem || !isCutawayClick(state, pos, hitResult);
    }

    /** Sets the plexer's target item and sends an overlay message to the player.
     *
     * @param plexer the plexer block entity
     * @param player the interacting player
     * @param stack  the item to set as target
     * @return SUCCESS interaction result
     */
    static InteractionResult applyTargetItem(PlexerBlockEntity plexer, Player player, ItemStack stack) {
        plexer.setTargetItem(stack.copy());
        player.sendOverlayMessage(
            Component.literal(TARGET_PREFIX + stack.getHoverName().getString()));
        return InteractionResult.SUCCESS;
    }

    /** Clears the plexer's target item and sends an overlay message to the player.
     *
     * @param plexer the plexer block entity
     * @param player the interacting player
     * @return SUCCESS interaction result
     */
    static InteractionResult clearTargetItem(PlexerBlockEntity plexer, Player player) {
        player.sendOverlayMessage(Component.literal(TARGET_CLEARED));
        plexer.setTargetItem(ItemStack.EMPTY);
        return InteractionResult.SUCCESS;
    }

    /**
     * Returns true if the hit lands on any of the 5 cutaway interior faces:
     * back wall, left/right cheek inners, top slab underside, base slab top.
     * Transforms hit coords to model space and checks against cutaway volume.
     *
     * @param state the block state
     * @param pos   the block position
     * @param hit   the ray trace hit result
     * @return true if cutaway click
     */
    static boolean isCutawayClick(BlockState state, BlockPos pos, BlockHitResult hit) {
        Direction facing = state.getValue(PlexerBlock.FACING);
        double hitX = hit.getLocation().x - pos.getX();
        double hitY = hit.getLocation().y - pos.getY();
        double hitZ = hit.getLocation().z - pos.getZ();
        double modelX = toModelX(facing, hitX, hitZ);
        double modelZ = toModelZ(facing, hitX, hitZ);
        return isInCutaway(modelX, hitY, modelZ);
    }

    /** Converts world-local XZ to south-facing model X by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    static double toModelX(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitX;
            case NORTH -> 1.0 - hitX;
            case EAST  -> hitZ;
            case WEST  -> 1.0 - hitZ;
            default    -> hitX;
        };
    }

    /** Converts world-local XZ to south-facing model Z by reversing facing rotation.
     *
     * @param facing the facing direction
     * @param hitX   block-local X hit coordinate
     * @param hitZ   block-local Z hit coordinate
     * @return the double value
     */
    static double toModelZ(Direction facing, double hitX, double hitZ) {
        return switch (facing) {
            case SOUTH -> hitZ;
            case NORTH -> 1.0 - hitZ;
            case EAST  -> 1.0 - hitX;
            case WEST  -> hitX;
            default    -> hitZ;
        };
    }

    /** Returns true if model-space coords fall within the cutaway volume.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @param modelZ model-space Z coordinate
     * @return true if in cutaway
     */
    static boolean isInCutaway(double modelX, double modelY, double modelZ) {
        return isInCutawayXY(modelX, modelY) && modelZ >= CUTAWAY_MIN_Z;
    }

    /**
     * Returns true if model-space X and Y fall within the cutaway horizontal and vertical range.
     *
     * @param modelX model-space X coordinate
     * @param modelY model-space Y coordinate
     * @return true if within cutaway X/Y bounds
     */
    static boolean isInCutawayXY(double modelX, double modelY) {
        return modelX >= CUTAWAY_MIN_X && modelX <= CUTAWAY_MAX_X
            && modelY >= CUTAWAY_MIN_Y && modelY <= CUTAWAY_MAX_Y;
    }

    /** Spawns an ItemEntity at the cutaway opening with velocity in the facing direction.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param stack the item stack
     */
    static void ejectFromCutaway(ServerLevel level, BlockPos pos, BlockState state, ItemStack stack) {
        Direction facing = state.getValue(PlexerBlock.FACING);
        ItemEntity entity = new ItemEntity(level,
            pos.getX() + ejectWorldX(facing), pos.getY() + EJECT_CENTER_Y,
            pos.getZ() + ejectWorldZ(facing), stack);
        entity.setDeltaMovement(
            facing.getStepX() * EJECT_SPEED, EJECT_LIFT, facing.getStepZ() * EJECT_SPEED);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }

    /** Converts model-space eject X to world-space X for the given facing.
     *
     * @param facing the horizontal facing direction
     * @return the world-space X coordinate
     */
    static double ejectWorldX(Direction facing) {
        return switch (facing) {
            case SOUTH -> EJECT_CENTER_X;
            case NORTH -> 1.0 - EJECT_CENTER_X;
            case EAST  -> 1.0 - EJECT_CENTER_Z;
            default    -> EJECT_CENTER_Z;
        };
    }

    /** Converts model-space eject Z to world-space Z for the given facing.
     *
     * @param facing the horizontal facing direction
     * @return the world-space Z coordinate
     */
    static double ejectWorldZ(Direction facing) {
        return switch (facing) {
            case SOUTH -> EJECT_CENTER_Z;
            case NORTH -> 1.0 - EJECT_CENTER_Z;
            case EAST  -> EJECT_CENTER_X;
            default    -> 1.0 - EJECT_CENTER_X;
        };
    }
}
