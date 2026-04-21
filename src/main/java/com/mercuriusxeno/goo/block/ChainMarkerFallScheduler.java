package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ThrowArc;
import com.mercuriusxeno.goo.network.BlobFlightPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Schedules deferred chain marker re-placements after a support block
 * breaks. The marker is removed immediately; after the flight animation
 * completes, a new marker is placed at the landing position.
 */
public final class ChainMarkerFallScheduler {

    /** Block center offset for flight start/end positions. */
    private static final double BLOCK_CENTER = 0.5;
    /** Sentinel for "no target entity" in BlobFlightPayload. */
    private static final int NO_ENTITY = -1;
    /** Block update flags: notify neighbors + send to clients. */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /** Empty ability id for legacy (non-ability) flight payloads. */
    private static final String LEGACY_ABILITY = "";

    private static final List<PendingFall> PENDING_FALLS = new ArrayList<>();

    private ChainMarkerFallScheduler() {}

    /**
     * Initiates a chain marker fall: removes the marker, broadcasts a
     * flight animation, and schedules re-placement at the landing pos.
     *
     * @param level       the server level
     * @param oldPos      the position being vacated
     * @param landingPos  the position to re-place at
     * @param markerBlock the chain marker block instance
     * @param gooType     the marker's goo type
     * @param stackCount  the marker's stack count
     * @param maxStacks   the marker's max stacks
     * @param fuse        the remaining fuse ticks
     * @param face        the placed face direction
     * @param blobShape   the cosmetic blob shape
     * @param areaMode    the delivery area mode
     */
    public static void scheduleFall(ServerLevel level, BlockPos oldPos, BlockPos landingPos,
            Block markerBlock, GooType gooType, int stackCount, int maxStacks, int fuse,
            Direction face, String blobShape, String areaMode) {
        double distance = oldPos.distManhattan(landingPos);
        int travelTicks = (int) ThrowArc.travelTicks(distance);

        broadcastFlight(level, oldPos, landingPos, gooType, travelTicks);

        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        PENDING_FALLS.add(new PendingFall(
                arrivalTick, level, landingPos, markerBlock, gooType,
                stackCount, maxStacks, fuse, face, blobShape, areaMode));
    }

    /**
     * Called each server tick to place markers whose fall animation
     * has completed. Wire to ServerTickEvent.Post.
     *
     * @param currentTick the current server tick count
     */
    public static void drainArrivedFalls(int currentTick) {
        List<PendingFall> ready = new ArrayList<>();
        Iterator<PendingFall> it = PENDING_FALLS.iterator();
        while (it.hasNext()) {
            PendingFall pf = it.next();
            if (currentTick >= pf.arrivalTick) {
                ready.add(pf);
                it.remove();
            }
        }
        for (PendingFall pf : ready) {
            placeMarker(pf);
        }
    }

    /**
     * Returns true if there are pending falls to process.
     *
     * @return true if the queue is non-empty
     */
    public static boolean hasPending() {
        return !PENDING_FALLS.isEmpty();
    }

    /**
     * Broadcasts a blob flight payload for the falling animation.
     *
     * @param level       the server level
     * @param oldPos      the starting position
     * @param landingPos  the landing position
     * @param gooType     the goo type
     * @param travelTicks the flight duration in ticks
     */
    private static void broadcastFlight(ServerLevel level, BlockPos oldPos,
            BlockPos landingPos, GooType gooType, int travelTicks) {
        BlobFlightPayload flight = new BlobFlightPayload(
                oldPos.getX() + BLOCK_CENTER,
                oldPos.getY() + BLOCK_CENTER,
                oldPos.getZ() + BLOCK_CENTER,
                gooType.getId(),
                NO_ENTITY,
                landingPos,
                Direction.UP.ordinal(),
                travelTicks,
                false,
                LEGACY_ABILITY);
        PacketDistributor.sendToPlayersTrackingChunk(
                level, level.getChunkAt(oldPos).getPos(), flight);
    }

    /**
     * Places a chain marker block at the landing position and
     * initializes it with the snapshotted state.
     *
     * @param pf the pending fall data
     */
    private static void placeMarker(PendingFall pf) {
        BlockState existing = pf.level.getBlockState(pf.landingPos);
        boolean waterlogged = existing.getFluidState().is(Fluids.WATER);
        BlockState markerState = pf.markerBlock.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        pf.level.setBlock(pf.landingPos, markerState, BLOCK_UPDATE_FLAGS);
        if (pf.level.getBlockEntity(pf.landingPos) instanceof ChainMarkerBlockEntity be) {
            be.initChain(pf.gooType, pf.face);
            be.restoreFromFall(pf.stackCount, pf.maxStacks, pf.fuse, pf.blobShape, pf.areaMode);
        }
    }

    /** Snapshot of a chain marker in mid-fall. */
    private record PendingFall(int arrivalTick, ServerLevel level, BlockPos landingPos,
                               Block markerBlock, GooType gooType, int stackCount,
                               int maxStacks, int fuse, Direction face,
                               String blobShape, String areaMode) {}
}
