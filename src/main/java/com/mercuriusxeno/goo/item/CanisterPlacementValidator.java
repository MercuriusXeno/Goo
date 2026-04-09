package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.block.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.ICanisterAttachable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Set;

/**
 * Validates canister placement positions and slot constraints.
 * Checks support blocks below and queries ICanisterAttachable for allowed slots.
 */
final class CanisterPlacementValidator {

    private CanisterPlacementValidator() {}

    /**
     * Returns true if the block below can support a canister. A canister needs
     * either a solid rendering surface or an ICanisterAttachable with capacity.
     *
     * @param level    the current level
     * @param belowPos the block position below the canister
     * @return true if the position can support a canister
     */
    static boolean isSupportedBelow(Level level, BlockPos belowPos) {
        BlockState belowState = level.getBlockState(belowPos);
        if (belowState.isSolidRender()) { return true; }
        BlockEntity be = level.getBlockEntity(belowPos);
        return be instanceof ICanisterAttachable att && att.canAttachOnTop();
    }

    /**
     * Returns the set of allowed slot indices for a canister block at the given
     * position, or null if no constraint applies. Queries the ICanisterAttachable
     * block below (if any) for its allowed slot set.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     * @return the allowed slots, or null if unconstrained
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull") // null = unconstrained (all slots); empty = none
    static @Nullable Set<Integer> getAllowedSlots(Level level, BlockPos canisterPos) {
        BlockPos belowPos = canisterPos.below();
        BlockEntity be = level.getBlockEntity(belowPos);
        if (be instanceof ICanisterAttachable att) {
            return att.allowedSlots();
        }
        return null;
    }

    /**
     * Returns true if the given slot is allowed for a canister block at the given
     * position. If there is no ICanisterAttachable below, all slots are allowed.
     *
     * @param level        the current level
     * @param canisterPos  the canister block position
     * @param slot         the slot index to check
     * @return true if the slot is allowed
     */
    static boolean isSlotAllowed(Level level, BlockPos canisterPos, int slot) {
        Set<Integer> allowed = getAllowedSlots(level, canisterPos);
        return allowed == null || allowed.contains(slot);
    }

    /**
     * Falls back to the first allowed slot if the target slot is disallowed.
     *
     * @param slot  the preferred slot index
     * @param level the current level
     * @param pos   the canister block position
     * @return the constrained slot index
     */
    static int constrainSlot(int slot, Level level, BlockPos pos) {
        if (isSlotAllowed(level, pos, slot)) { return slot; }
        Set<Integer> allowed = getAllowedSlots(level, pos);
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.iterator().next();
        }
        return slot;
    }

    /**
     * Computes the target slot for new block placement using the click location
     * on the adjacent solid block's face.
     *
     * @param clickLoc the click location in world coordinates
     * @param placePos the block position being placed
     * @param entryFace the face the placement enters from
     * @return the target slot index (0-8)
     */
    static int computePlacementSlot(Vec3 clickLoc, BlockPos placePos, Direction entryFace) {
        float px = (float) ((clickLoc.x - placePos.getX()) * CanisterSlotResolver.PIXELS_PER_BLOCK);
        float pz = (float) ((clickLoc.z - placePos.getZ()) * CanisterSlotResolver.PIXELS_PER_BLOCK);
        return CanisterSlotLayout.placementSlot(entryFace, px, pz);
    }

    /**
     * Stamps owner UUID on the canister block entity.
     *
     * @param be     the canister block entity
     * @param player the player who placed the canister
     */
    static void stampOwner(CanisterBlockEntity be, Player player) {
        if (player != null) {
            be.setOwner(player.getUUID());
        }
    }
}
