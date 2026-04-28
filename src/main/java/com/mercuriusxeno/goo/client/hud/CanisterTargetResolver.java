package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import static com.mercuriusxeno.goo.GooConstants.NO_SLOT;

/**
 * Resolves crosshair aim into a canister/hub/tap target with spatial
 * anchor offsets for HUD panel positioning.
 */
final class CanisterTargetResolver {
    /**
     * Sentinel slot value indicating this target is a tap canister (not a grid slot).
     */
    static final int TAP_SLOT = -2;
    /**
     * Sentinel slot value for reactor output canister.
     */
    static final int REACTOR_SLOT = -3;
    /**
     * Canister body height in blocks (12/16).
     */
    private static final double BODY_HEIGHT = 12.0 / 16.0;
    /**
     * Mid-body Y for side-face anchoring (6/16).
     */
    private static final double MID_BODY = 6.0 / 16.0;
    /**
     * Y position for the HUD below the canister block bottom.
     */
    private static final double BLOCK_BOTTOM = 0.0;
    /**
     * Hub canister top in blocks (14/16 + 1/16 gap = 15/16).
     */
    private static final double HUB_CANISTER_TOP = 15.0 / 16.0;
    /**
     * Mid-height for hub canister side faces (8/16).
     */
    private static final double HUB_MID = 8.0 / 16.0;
    /**
     * Offset to push the panel to the face surface (half a block).
     */
    private static final double FACE_OFFSET = 0.5;
    /**
     * Canister top Y on the tap (16/16 = full block height).
     */
    private static final double TAP_CANISTER_TOP = 1.0;

    /** Sentinel value for no tracked slot. */
    /**
     * Tap canister slot center offsets by facing (cx, cz in block coords).
     */
    private static final double[][] TAP_SLOT_CENTERS = {
            /* SOUTH */ {8.0 / 16.0, 3.0 / 16.0},
            /* NORTH */ {8.0 / 16.0, 13.0 / 16.0},
            /* EAST  */ {13.0 / 16.0, 8.0 / 16.0},
            /* WEST  */ {3.0 / 16.0, 8.0 / 16.0},
    };
    /**
     * Block-local coordinate to pixel conversion factor.
     */
    private static final double BLOCK_PIXELS = 16.0;
    /**
     * Index for SOUTH facing in TAP_SLOT_CENTERS.
     */
    private static final int TAP_SOUTH = 0;
    /**
     * Index for NORTH facing in TAP_SLOT_CENTERS.
     */
    private static final int TAP_NORTH = 1;
    /**
     * Index for EAST facing in TAP_SLOT_CENTERS.
     */
    private static final int TAP_EAST = 2;
    /**
     * Index for WEST facing in TAP_SLOT_CENTERS.
     */
    private static final int TAP_WEST = 3;
    /**
     * Block center offset for hub frame targets.
     */
    private static final double BLOCK_CENTER = 0.5;
    /**
     * Reactor hollow center Y (midpoint of 1-15 pixel range).
     */
    private static final double REACTOR_MID_Y = 8.0 / 16.0;

    private CanisterTargetResolver() {
    }

    /**
     * Returns the targeted canister slot, or null if not looking at a canister.
     *
     * @return the target
     */
    static CanisterHudRenderer.@Nullable Target getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null) {
            return null;
        }
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        return resolveBlockTarget(mc, hit, pos, be);
    }

    /**
     * Dispatches to the per-block-type target builder.
     *
     * @param mc  the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @param be  the block entity at the hit position
     * @return the target, or null if not a supported block
     */
    private static CanisterHudRenderer.@Nullable Target resolveBlockTarget(
            Minecraft mc, BlockHitResult hit, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof CanisterBlockEntity) {
            return getCanisterTarget(mc, hit, pos);
        }
        if (be instanceof HubBlockEntity) {
            return getHubTarget(mc, hit, pos);
        }
        if (be instanceof TapBlockEntity tap) {
            return getTapTarget(mc, pos, tap);
        }
        if (be instanceof ReactorBlockEntity reactor) {
            return getReactorTarget(mc, hit, pos, reactor);
        }
        return null;
    }

    /**
     * Resolves a canister block hit into a slot target with face and block-above awareness.
     *
     * @param mc  the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @return the target, or null if the hit missed all slots
     */
    private static CanisterHudRenderer.@Nullable Target getCanisterTarget(Minecraft mc,
                                                                          BlockHitResult hit, BlockPos pos) {
        int slot = CanisterBlock.hitSlot(hit, pos);
        if (slot < 0) {
            return null;
        }
        boolean blockAbove = hasFullBlockAbove(mc.level, pos);
        Direction playerFacing = mc.player != null
                ? mc.player.getDirection() : Direction.NORTH;
        return canisterTarget(pos, slot, hit.getDirection(), blockAbove, playerFacing);
    }

    /**
     * Resolves a hub block hit into either a slot target or a frame target.
     *
     * @param mc  the Minecraft instance
     * @param hit the block hit result
     * @param pos the block position
     * @return the target (frame fallback if no slot was hit)
     */
    private static CanisterHudRenderer.@Nullable Target getHubTarget(Minecraft mc,
                                                                     BlockHitResult hit, BlockPos pos) {
        int slot = HubBlock.hitSlot(hit, pos);
        if (slot >= 0) {
            Vec3 look = mc.player != null ? mc.player.getLookAngle() : Vec3.ZERO;
            return hubTarget(pos, slot, hit.getDirection(), look);
        }
        return hubFrameTarget(pos);
    }

    /**
     * Resolves a tap block hit into a canister target if the tap holds a canister.
     *
     * @param mc  the Minecraft instance
     * @param pos the block position
     * @param tap the tap block entity
     * @return the target, or null if the tap has no canister
     */
    private static CanisterHudRenderer.@Nullable Target getTapTarget(Minecraft mc,
                                                                     BlockPos pos, TapBlockEntity tap) {
        if (tap.getCanister().isEmpty()) {
            return null;
        }
        Direction facing = mc.level.getBlockState(pos).getValue(TapBlock.FACING);
        return tapTarget(pos, facing);
    }

    /**
     * Returns true if the block above has a full collision shape.
     *
     * @param level the current level
     * @param pos   the block position
     * @return true if fullBlockAbove is present
     */
    private static boolean hasFullBlockAbove(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        return level.getBlockState(above)
                .isCollisionShapeFullBlock(level, above);
    }

    /**
     * Builds a target for a canister block slot, using the 3x3 grid centers.
     * Positioning depends on whether a full block is above and which face was hit.
     *
     * @param pos          the block position
     * @param slot         the slot index
     * @param face         the block face direction
     * @param blockAbove   the blockAbove
     * @param playerFacing the playerFacing
     * @return the result
     */
    private static CanisterHudRenderer.Target canisterTarget(BlockPos pos, int slot,
                                                             Direction face, boolean blockAbove, Direction playerFacing) {
        double cx = slotCenterX(slot);
        double cz = slotCenterZ(slot);
        if (face == Direction.DOWN) {
            return canisterDownTarget(pos, slot, cx, cz);
        }
        if (!blockAbove) {
            return canisterTopTarget(pos, slot, cx, cz);
        }
        Direction side = (face == Direction.UP) ? playerFacing : face;
        return canisterSideTarget(pos, slot, side, cx, cz);
    }

    /**
     * Builds a target anchored below a canister (viewed from underneath).
     *
     * @param pos  the block position
     * @param slot the slot index
     * @param cx   the center X in block coords
     * @param cz   the center Z in block coords
     * @return the downward-facing target
     */
    private static CanisterHudRenderer.Target canisterDownTarget(BlockPos pos, int slot,
                                                                 double cx, double cz) {
        return new CanisterHudRenderer.Target(pos, slot, cx, cz, BLOCK_BOTTOM, Direction.DOWN, false);
    }

    /**
     * Builds a target anchored above the canister body top.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @param cx   the center X in block coords
     * @param cz   the center Z in block coords
     * @return the upward-facing target
     */
    private static CanisterHudRenderer.Target canisterTopTarget(BlockPos pos, int slot,
                                                                double cx, double cz) {
        return new CanisterHudRenderer.Target(pos, slot, cx, cz, BODY_HEIGHT, Direction.UP, false);
    }

    /**
     * Returns the block-local X center for a canister slot (0..1 range).
     *
     * @param slot the slot index
     * @return the X coordinate in block units
     */
    private static double slotCenterX(int slot) {
        return CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
    }

    /**
     * Returns the block-local Z center for a canister slot (0..1 range).
     *
     * @param slot the slot index
     * @return the Z coordinate in block units
     */
    private static double slotCenterZ(int slot) {
        return CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
    }

    /**
     * Builds a side-face target for a canister slot: offset to the hit face, mid-body height.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @param face the block face direction
     * @param cx   the center X in block coords
     * @param cz   the center Z in block coords
     * @return the result
     */
    private static CanisterHudRenderer.Target canisterSideTarget(BlockPos pos, int slot,
                                                                 Direction face, double cx, double cz) {
        double offCx = cx + face.getStepX() * FACE_OFFSET;
        double offCz = cz + face.getStepZ() * FACE_OFFSET;
        return new CanisterHudRenderer.Target(pos, slot, offCx, offCz, MID_BODY, face, false);
    }

    /**
     * Builds a target for a hub canister slot.
     * Side face: anchored on the face most perpendicular to the player's look vector,
     * so the HUD always appears on the side most visible to the player.
     * UP/DOWN hit: billboard above canister top.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @param face the block face direction
     * @param look the player look direction vector
     * @return the result
     */
    private static CanisterHudRenderer.Target hubTarget(BlockPos pos, int slot,
                                                        Direction face, Vec3 look) {
        double[] center = HubBlock.SLOT_CENTERS[slot];
        double cx = center[0] / BLOCK_PIXELS;
        double cz = center[1] / BLOCK_PIXELS;
        if (face != Direction.UP && face != Direction.DOWN) {
            return hubSideTarget(pos, slot, cx, cz, look);
        }
        return new CanisterHudRenderer.Target(pos, slot, cx, cz, HUB_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Builds a side-face target for a hub slot, anchored on the face most
     * perpendicular to the player's look vector.
     *
     * @param pos  the block position
     * @param slot the slot index
     * @param cx   the center X in block coords
     * @param cz   the center Z in block coords
     * @param look the player look direction vector
     * @return the side-anchored target
     */
    private static CanisterHudRenderer.Target hubSideTarget(BlockPos pos, int slot,
                                                            double cx, double cz, Vec3 look) {
        Direction bestFace = InWorldHud.bestPerpendicularFace(look);
        double offCx = cx + bestFace.getStepX() * FACE_OFFSET;
        double offCz = cz + bestFace.getStepZ() * FACE_OFFSET;
        return new CanisterHudRenderer.Target(pos, slot, offCx, offCz, HUB_MID, bestFace, false);
    }

    /**
     * Builds a target for the hub frame (intake/spindle). Centered, HUD above intake.
     *
     * @param pos the block position
     * @return the result
     */
    private static CanisterHudRenderer.Target hubFrameTarget(BlockPos pos) {
        return new CanisterHudRenderer.Target(pos, NO_SLOT, BLOCK_CENTER, BLOCK_CENTER,
                HUB_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Builds a target for the tap's canister slot, anchored above the canister.
     *
     * @param pos    the block position
     * @param facing the block facing direction
     * @return the result
     */
    private static CanisterHudRenderer.Target tapTarget(BlockPos pos, Direction facing) {
        int idx = tapSlotIndex(facing);
        double cx = TAP_SLOT_CENTERS[idx][0];
        double cz = TAP_SLOT_CENTERS[idx][1];
        return new CanisterHudRenderer.Target(pos, TAP_SLOT, cx, cz, TAP_CANISTER_TOP, Direction.UP, false);
    }

    /**
     * Resolves a reactor hit into an output canister target.
     *
     * @param mc      the Minecraft instance
     * @param hit     the block hit result
     * @param pos     the block position
     * @param reactor the reactor block entity
     * @return the target, or null if no output canister
     */
    private static CanisterHudRenderer.@Nullable Target getReactorTarget(Minecraft mc,
                                                                         BlockHitResult hit, BlockPos pos, ReactorBlockEntity reactor) {
        if (reactor.getOutputCanister().isEmpty()) {
            return null;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!ReactorBlock.isHollowClick(state, pos, hit)) {
            return null;
        }
        Direction facing = state.getValue(ReactorBlock.FACING);
        double cx = BLOCK_CENTER + facing.getStepX() * FACE_OFFSET;
        double cz = BLOCK_CENTER + facing.getStepZ() * FACE_OFFSET;
        return new CanisterHudRenderer.Target(pos, REACTOR_SLOT, cx, cz,
                REACTOR_MID_Y, facing, false);
    }

    /**
     * Maps a horizontal facing direction to its TAP_SLOT_CENTERS array index.
     *
     * @param facing the horizontal direction
     * @return the array index
     */
    private static int tapSlotIndex(Direction facing) {
        return switch (facing) {
            case SOUTH -> TAP_SOUTH;
            case NORTH -> TAP_NORTH;
            case EAST -> TAP_EAST;
            case WEST -> TAP_WEST;
            default -> TAP_SOUTH;
        };
    }
}
