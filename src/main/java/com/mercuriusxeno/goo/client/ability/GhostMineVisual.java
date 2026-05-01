package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ghost outline of the destruction footprint shown beneath ROCK / BLAZE /
 * FROST chain markers during fuse and active mining. Renders translucent
 * fill on exterior block faces plus a perimeter wireframe; the GLOW path
 * then layers an aurora-style fade wall over the same outline via
 * {@link GlowFadeVisual}. Tunnel-mode outlines fade alpha along the blast
 * axis so deeper layers read as receding.
 */
public final class GhostMineVisual {

    /** Area mode constant for tunnel delivery. */
    private static final String MODE_TUNNEL = "tunnel";

    /** Alpha for the ghost fill quads. */
    private static final int GHOST_FILL_ALPHA = 0x26;
    /** Alpha for the perimeter wireframe. */
    private static final int GHOST_WIRE_ALPHA = 0xC0;
    /** Per-layer alpha decay factor for tunnel depth falloff. */
    private static final float DEPTH_ALPHA_DECAY = 0.80f;
    /** Line width for the perimeter wireframe. */
    private static final float GHOST_LINE_WIDTH = 2.0f;

    /** Bit shift for alpha channel in ARGB. */
    private static final int ALPHA_SHIFT = 24;
    /** Mask for stripping alpha from an ARGB color. */
    private static final int RGB_MASK = 0x00FFFFFF;

    /** Bit mask for 21-bit coordinate packing. */
    private static final long PACK_MASK = 0x1FFFFF;
    /** Bit shift for Y coordinate in packed position. */
    private static final int PACK_Y_SHIFT = 21;
    /** Bit shift for Z coordinate in packed position. */
    private static final int PACK_Z_SHIFT = 42;

    /** Half-block offset for face and edge positioning. */
    private static final float HALF = 0.5f;
    /** Array index for X component in offset triples. */
    private static final int X = 0;
    /** Array index for Y component in offset triples. */
    private static final int Y = 1;
    /** Array index for Z component in offset triples. */
    private static final int Z = 2;

    private GhostMineVisual() {
    }

    /**
     * Renders the effect region as connected translucent fill with
     * wireframe only on the outer perimeter. Interior faces between
     * adjacent blocks are eliminated; interior edges between coplanar
     * exterior faces are eliminated.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    public static void submit(ChainMarkerRenderState state,
                              PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (!shouldShow(state)) {
            return;
        }

        List<int[]> offsets = computeFilteredOffsets(state);
        Set<Long> filled = packOffsets(offsets);

        int edgeRgb = GooColors.edge(state.gooType) & RGB_MASK;
        int fillColor = (GHOST_FILL_ALPHA << ALPHA_SHIFT) | edgeRgb;
        int wireColor = (GHOST_WIRE_ALPHA << ALPHA_SHIFT) | edgeRgb;

        Direction blastDir = MODE_TUNNEL.equals(state.areaMode) ? state.placedFace.getOpposite() : null;
        int minedLayers = state.minedLayers;
        submitGhostFill(poseStack, nodeCollector, offsets, filled, fillColor, blastDir, minedLayers);
        submitGhostWireframe(poseStack, nodeCollector, offsets, filled, wireColor, blastDir, minedLayers);
        GlowFadeVisual.submit(poseStack, nodeCollector, offsets,
                filled, fillColor, state.placedFace, state.gameTime);
    }

    /**
     * Returns true if the ghost outline should render for this state.
     *
     * @param state the render state snapshot
     * @return true if the ghost outline should be drawn
     */
    private static boolean shouldShow(ChainMarkerRenderState state) {
        if (state.fuseRemaining <= 0 && !state.behaviorActive) {
            return false;
        }
        return hasGhostOutline(state.gooType);
    }

    /**
     * True for goo types that display a destructive-area ghost outline.
     *
     * @param type the goo type to check
     * @return true if the type has a destructive-area ghost
     */
    private static boolean hasGhostOutline(GooType type) {
        return type == GooType.ROCK || type == GooType.BLAZE || type == GooType.FROST;
    }

    /**
     * Computes ghost offsets with mined-layer and air-block filtering applied.
     *
     * @param state the render state snapshot
     * @return filtered list of block offsets
     */
    private static List<int[]> computeFilteredOffsets(ChainMarkerRenderState state) {
        List<int[]> allOffsets = ChainFootprint.computeRegionOffsets(
                state.stackCount, state.areaMode, state.placedFace);
        List<int[]> afterMined = excludeMinedLayers(allOffsets, state.placedFace, state.minedLayers);
        return excludeAirBlocks(afterMined, state.blockPos);
    }

    /**
     * Packs a list of offsets into a position set for neighbor lookups.
     *
     * @param offsets the block offsets to pack
     * @return set of packed position keys
     */
    private static Set<Long> packOffsets(List<int[]> offsets) {
        Set<Long> filled = HashSet.newHashSet(offsets.size());
        for (int[] o : offsets) {
            filled.add(packPos(o[X], o[Y], o[Z]));
        }
        return filled;
    }

    /**
     * Filters out offsets that correspond to air blocks in the world.
     * The ghost outline only highlights solid blocks that will actually
     * be affected by the chain effect.
     *
     * @param offsets   the block offsets to filter
     * @param markerPos the chain marker's world position
     * @return the filtered offset list with air blocks removed
     */
    private static List<int[]> excludeAirBlocks(List<int[]> offsets, BlockPos markerPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return offsets;
        }
        List<int[]> result = new ArrayList<>(offsets.size());
        for (int[] o : offsets) {
            BlockPos worldPos = markerPos.offset(o[X], o[Y], o[Z]);
            if (!mc.level.getBlockState(worldPos).isAir()) {
                result.add(o);
            }
        }
        return result;
    }

    /**
     * Attenuates a color's alpha by the block's effective depth along
     * the blast axis. Mined layers shift the origin forward so the
     * remaining front face is always at full alpha.
     *
     * @param color       the base ARGB color
     * @param offset      the block offset {dx, dy, dz}
     * @param blastDir    the blast direction, or null for flat mode
     * @param minedLayers layers already mined (subtracted from depth)
     * @return the depth-attenuated ARGB color
     */
    private static int attenuateByDepth(int color, int[] offset,
                                        @Nullable Direction blastDir, int minedLayers) {
        if (blastDir == null) {
            return color;
        }
        int rawDepth = offset[X] * blastDir.getStepX()
                + offset[Y] * blastDir.getStepY()
                + offset[Z] * blastDir.getStepZ();
        int effectiveDepth = rawDepth - minedLayers;
        if (effectiveDepth <= 0) {
            return color;
        }
        float factor = (float) Math.sqrt(Math.pow(DEPTH_ALPHA_DECAY, effectiveDepth));
        int alpha = (int) ((color >>> ALPHA_SHIFT) * factor);
        return (alpha << ALPHA_SHIFT) | (color & RGB_MASK);
    }

    /**
     * Filters out block offsets belonging to layers already mined.
     * Depth is measured along the blast direction (placedFace opposite).
     * Layer 0 is the block immediately behind the marker.
     *
     * @param offsets     all block offsets in the region
     * @param placedFace  the placed face direction
     * @param minedLayers the number of layers already mined
     * @return the filtered offset list
     */
    private static List<int[]> excludeMinedLayers(List<int[]> offsets,
                                                  Direction placedFace, int minedLayers) {
        if (minedLayers <= 0) {
            return offsets;
        }
        Direction blast = placedFace.getOpposite();
        int bx = blast.getStepX();
        int by = blast.getStepY();
        int bz = blast.getStepZ();
        List<int[]> result = new ArrayList<>(offsets.size());
        for (int[] o : offsets) {
            int depth = o[X] * bx + o[Y] * by + o[Z] * bz;
            if (depth >= minedLayers) {
                result.add(o);
            }
        }
        return result;
    }

    /**
     * Emits translucent fill quads for exterior faces only. In tunnel
     * mode, alpha decays with depth along the blast axis.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB fill color
     * @param blastDir      the blast direction for depth falloff, or null for flat mode
     * @param minedLayers   layers already mined (shifts depth origin forward)
     */
    private static void submitGhostFill(PoseStack poseStack,
                                        SubmitNodeCollector nodeCollector, List<int[]> offsets,
                                        Set<Long> filled, int color, @Nullable Direction blastDir,
                                        int minedLayers) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.QUADS_NO_DEPTH,
                (pose, c) -> {
                    FlatQuadContext ctx = new FlatQuadContext(pose, c);
                    for (int[] o : offsets) {
                        int depthColor = attenuateByDepth(color, o, blastDir, minedLayers);
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (!filled.contains(packPos(nx, ny, nz))) {
                                emitFaceQuad(ctx, o, dir, depthColor);
                            }
                        }
                    }
                });
    }

    /**
     * Emits wireframe edges only on the perimeter of the region. In
     * tunnel mode, alpha decays with depth along the blast axis.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB wire color
     * @param blastDir      the blast direction for depth falloff, or null for flat mode
     * @param minedLayers   layers already mined (shifts depth origin forward)
     */
    private static void submitGhostWireframe(PoseStack poseStack,
                                             SubmitNodeCollector nodeCollector, List<int[]> offsets,
                                             Set<Long> filled, int color, @Nullable Direction blastDir,
                                             int minedLayers) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.LINES_NO_DEPTH,
                (pose, c) -> {
                    LineContext ctx = new LineContext(pose, c);
                    for (int[] o : offsets) {
                        int depthColor = attenuateByDepth(color, o, blastDir, minedLayers);
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (filled.contains(packPos(nx, ny, nz))) {
                                continue;
                            }
                            emitPerimeterEdges(ctx, o, dir, filled, depthColor);
                        }
                    }
                });
    }

    /**
     * For one exterior face, emits only perimeter edges. Interior
     * edges shared with coplanar neighbors are skipped.
     *
     * @param ctx     the line render context
     * @param pos     the block offset {dx, dy, dz}
     * @param faceDir the exterior face direction
     * @param filled  packed position set for neighbor checks
     * @param color   the ARGB wire color
     */
    private static void emitPerimeterEdges(LineContext ctx, int[] pos,
                                           Direction faceDir, Set<Long> filled, int color) {
        FaceEdge[] edges = getFaceEdges(faceDir);
        float cx = pos[X] + HALF + faceDir.getStepX() * HALF;
        float cy = pos[Y] + HALF + faceDir.getStepY() * HALF;
        float cz = pos[Z] + HALF + faceDir.getStepZ() * HALF;

        for (FaceEdge edge : edges) {
            if (isCoplanarNeighbor(pos, faceDir, edge.neighborDir(), filled)) {
                continue;
            }
            emitSingleEdge(ctx, cx, cy, cz, edge, color);
        }
    }

    /**
     * Checks if the adjacent block has a coplanar exterior face.
     *
     * @param pos         the block offset
     * @param faceDir     the exterior face direction
     * @param neighborDir the direction to the adjacent block
     * @param filled      packed position set for neighbor checks
     * @return true if the neighbor has an exterior face on the same side
     */
    public static boolean isCoplanarNeighbor(int[] pos, Direction faceDir,
                                             Direction neighborDir, Set<Long> filled) {
        int adjX = pos[X] + neighborDir.getStepX();
        int adjY = pos[Y] + neighborDir.getStepY();
        int adjZ = pos[Z] + neighborDir.getStepZ();
        return filled.contains(packPos(adjX, adjY, adjZ))
                && !filled.contains(packPos(
                adjX + faceDir.getStepX(),
                adjY + faceDir.getStepY(),
                adjZ + faceDir.getStepZ()));
    }

    /**
     * Emits one wireframe edge segment for a face edge.
     *
     * @param ctx   the line render context
     * @param cx    face center X
     * @param cy    face center Y
     * @param cz    face center Z
     * @param edge  the edge descriptor
     * @param color the ARGB wire color
     */
    private static void emitSingleEdge(LineContext ctx, float cx, float cy,
                                       float cz, FaceEdge edge, int color) {
        float nx = edge.neighborDir().getStepX() * HALF;
        float ny = edge.neighborDir().getStepY() * HALF;
        float nz = edge.neighborDir().getStepZ() * HALF;
        float rx = edge.runDir().getStepX() * HALF;
        float ry = edge.runDir().getStepY() * HALF;
        float rz = edge.runDir().getStepZ() * HALF;
        float ex = cx + nx;
        float ey = cy + ny;
        float ez = cz + nz;
        ctx.emitEdge(ex - rx, ey - ry, ez - rz,
                ex + rx, ey + ry, ez + rz,
                color, GHOST_LINE_WIDTH);
    }

    /**
     * Returns the 4 edges of a face, each identified by the adjacent
     * block direction and the axis the edge runs along.
     *
     * @param face the face direction
     * @return the four face edge descriptors
     */
    public static FaceEdge[] getFaceEdges(Direction face) {
        return switch (face.getAxis()) {
            case X -> new FaceEdge[]{
                    new FaceEdge(Direction.UP, Direction.NORTH),
                    new FaceEdge(Direction.DOWN, Direction.NORTH),
                    new FaceEdge(Direction.NORTH, Direction.UP),
                    new FaceEdge(Direction.SOUTH, Direction.UP)
            };
            case Y -> new FaceEdge[]{
                    new FaceEdge(Direction.NORTH, Direction.EAST),
                    new FaceEdge(Direction.SOUTH, Direction.EAST),
                    new FaceEdge(Direction.WEST, Direction.NORTH),
                    new FaceEdge(Direction.EAST, Direction.NORTH)
            };
            case Z -> new FaceEdge[]{
                    new FaceEdge(Direction.UP, Direction.EAST),
                    new FaceEdge(Direction.DOWN, Direction.EAST),
                    new FaceEdge(Direction.WEST, Direction.UP),
                    new FaceEdge(Direction.EAST, Direction.UP)
            };
        };
    }

    /**
     * Emits a single face quad at the given position and direction.
     *
     * @param ctx   the flat quad render context
     * @param pos   the block offset {dx, dy, dz}
     * @param dir   the face direction to emit
     * @param color the ARGB fill color
     */
    private static void emitFaceQuad(FlatQuadContext ctx, int[] pos,
                                     Direction dir, int color) {
        CuboidBounds box = new CuboidBounds(
                pos[X], pos[X] + 1, pos[Z], pos[Z] + 1, pos[Y], pos[Y] + 1);
        ctx.emitFace(color, box, dir);
    }

    /**
     * Packs three 21-bit coordinates into a single long for set lookup.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param z the Z coordinate
     * @return the packed position
     */
    public static long packPos(int x, int y, int z) {
        long px = x & PACK_MASK;
        long py = (y & PACK_MASK) << PACK_Y_SHIFT;
        long pz = (z & PACK_MASK) << PACK_Z_SHIFT;
        return px | py | pz;
    }

    /**
     * Describes one edge of a face quad: which neighboring block shares
     * this edge (neighborDir) and which axis the edge runs along (runDir).
     *
     * @param neighborDir the direction to the adjacent block sharing this edge
     * @param runDir      the axis the edge runs along
     */
    public record FaceEdge(Direction neighborDir, Direction runDir) {
    }
}
