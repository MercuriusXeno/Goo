package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.style.NetherHoleStyles;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.effect.ChainFootprint;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the chain marker as a slime-like glowing orb during the FUSE
 * phase. Two layers: inner core with the goo fluid texture, outer
 * translucent shell with goo-tinted color. Both are emissive (fullbright).
 * Size scales with stack count. Implodes inward in the final ticks
 * before detonation.
 *
 * <p>Type-specific post-detonation visuals (currently only the nether
 * black-hole sphere/corona/disk) live in dedicated helper classes and
 * are dispatched from {@link #submit} when the matching behavior is
 * active on the BE. The BER itself only knows about the orb and the
 * thin dispatch check.
 */
public class ChainMarkerBlockEntityRenderer
        implements BlockEntityRenderer<ChainMarkerBlockEntity, ChainMarkerRenderState> {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Base inner core half-size in block units (2 pixels) at 1 stack. */
    private static final float CORE_BASE = 2f / 16f;

    /** Shell extends 1 pixel beyond core in each direction. */
    private static final float SHELL_MARGIN = 1f / 16f;

    /** Core growth per additional stack (1/32 block = 0.5 pixel). */
    private static final float CORE_GROWTH = 1f / 32f;

    /** Splat squish factor along placed face axis (half height). */
    private static final float SPLAT_HEIGHT = 0.5f;

    /** Splat widen factor perpendicular to placed face (sqrt 2). */
    private static final float SPLAT_WIDTH = 1.414f;

    /** Pulse amplitude: 10% size increase on stack add. */
    private static final float PULSE_AMPLITUDE = 0.10f;

    /** Pulse duration in ticks. */
    private static final int PULSE_TICKS = 4;

    /** Outer shell alpha (translucent). */
    private static final int SHELL_ALPHA = 0x60;

    /** Outer shell alpha when the player is aiming at the node. */
    private static final int SHELL_ALPHA_TARGETED = 0xC0;

    /** Extra scale bump when targeted. */
    private static final float TARGET_SCALE_BOOST = 1.15f;

    /** Ticks before detonation where implosion starts. */
    private static final int IMPLOSION_TICKS = 6;

    /** Minimum scale during implosion (fraction of normal). */
    private static final float IMPLOSION_MIN = 0.3f;

    /** Bit shift for alpha channel in ARGB. */
    private static final int ALPHA_SHIFT = 24;
    /** Mask for stripping alpha from an ARGB color. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;
    /** Alpha for the ghost fill quads. */
    private static final int GHOST_FILL_ALPHA = 0x30;
    /** Alpha for the perimeter wireframe. */
    private static final int GHOST_WIRE_ALPHA = 0xC0;
    /** Line width for the perimeter wireframe. */
    private static final float GHOST_LINE_WIDTH = 2.0f;
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
    /** Half-extent of the render bounding box around a chain marker, in blocks. Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;

    public ChainMarkerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ChainMarkerRenderState createRenderState() {
        return new ChainMarkerRenderState();
    }

    /** Extends the render bounding box so the implosion sphere (up to the
     * nether max radius of 9) is not frustum-culled when the player looks
     * slightly away from the marker block.
     *
     * @param blockEntity the chain marker block entity
     * @return an AABB large enough to contain the maximum implosion sphere
     */
    @Override
    public @NonNull AABB getRenderBoundingBox(@NonNull ChainMarkerBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        double cx = pos.getX() + BLOCK_CENTER;
        double cy = pos.getY() + BLOCK_CENTER;
        double cz = pos.getZ() + BLOCK_CENTER;
        return new AABB(
            cx - RENDER_BOX_HALF_EXTENT, cy - RENDER_BOX_HALF_EXTENT, cz - RENDER_BOX_HALF_EXTENT,
            cx + RENDER_BOX_HALF_EXTENT, cy + RENDER_BOX_HALF_EXTENT, cz + RENDER_BOX_HALF_EXTENT);
    }

    @Override
    public void extractRenderState(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        extractCoreFields(be, state, partialTick);
        extractFuseAndTarget(be, state);
        NetherHoleStyles.ACTIVE.extract(be, state);
    }

    /**
     * Copies goo type, stacks, fuse, and partial tick from the block entity.
     *
     * @param be the block entity
     * @param state the render state to populate
     * @param partialTick the partial tick for interpolation
     */
    private static void extractCoreFields(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state, float partialTick) {
        state.gooType = be.getGooType();
        state.stackCount = be.getStackCount();
        state.maxStacks = be.getMaxStacks();
        state.fuseRemaining = be.getFuseRemaining();
        state.partialTick = partialTick;
        state.flatMode = be.isFlatMode();
        state.lastStackTick = be.getLastStackTick();
        state.gameTime = be.getLevel() != null
                ? be.getLevel().getGameTime() + partialTick : 0f;
    }

    /**
     * Resolves fuse duration from chain profile and checks aim targeting.
     *
     * @param be the block entity
     * @param state the render state to populate
     */
    private static void extractFuseAndTarget(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state) {
        ChainProfile profile = ChainProfile.forType(be.getGooType());
        state.fuseTicks = profile != null ? profile.fuseTicks() : 1;
        // Highlight when ANY source of aim is on this marker: vanilla
        // crosshair (no-glove case), the goo cone-based aim assist
        // (glove held), or the post-throw freeze window (aim locked from
        // the previous throw and the marker was just placed on the spot).
        BlockPos pos = be.getBlockPos();
        state.targeted = GooRenderUtil.isBlockTargeted(pos)
                || GooTargetHighlighter.isChainMarkerTargeted(pos)
                || ThrowFreezeState.isFrozenOnChainMarker(pos);
        state.placedFace = be.getPlacedFace();
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.netherActive) {
            NetherHoleStyles.ACTIVE.submit(state, poseStack, nodeCollector);
            return;
        }
        submitFuseOrb(state, poseStack, nodeCollector);
        submitGhostOutline(state, poseStack, nodeCollector);
    }

    /**
     * Draws the orb visual used during the FUSE phase (existing behavior).
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     */
    private static void submitFuseOrb(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float coreHalf = computeCoreHalf(state);
        float shellHalf = coreHalf + SHELL_MARGIN;
        float implosion = computeImplosionScale(state.fuseRemaining, state.partialTick);
        float pulse = computePulseScale(state);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        float modifier = implosion * pulse * targetBoost;
        int shellColor = computeShellColor(state);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);

        poseStack.pushPose();
        translateToFace(poseStack, state);
        if (state.flatMode) {
            applySplatScale(poseStack, state.placedFace, modifier);
        } else {
            poseStack.scale(modifier, modifier, modifier);
        }
        submitCubeLayer(poseStack, nodeCollector, GooRenderUtil.OPAQUE_WHITE, coreHalf, uv);
        submitCubeLayer(poseStack, nodeCollector, shellColor, shellHalf, uv);
        poseStack.popPose();
    }

    /**
     * Computes the core half-size based on stack count.
     * Starts at CORE_BASE (2px) and grows by CORE_GROWTH (0.5px) per stack.
     *
     * @param state the chain marker render state
     * @return the core half-size in block units
     */
    private static float computeCoreHalf(ChainMarkerRenderState state) {
        return CORE_BASE + (state.stackCount - 1) * CORE_GROWTH;
    }

    /**
     * Computes a brief pulse multiplier that spikes on stack add.
     * Compares current game time against the recorded stack tick
     * for smooth partial-tick interpolation.
     *
     * @param state the chain marker render state
     * @return pulse scale factor (1.0 normally, up to 1+PULSE_AMPLITUDE)
     */
    private static float computePulseScale(ChainMarkerRenderState state) {
        if (state.lastStackTick <= 0) { return 1f; }
        float elapsed = state.gameTime - state.lastStackTick;
        if (elapsed < 0 || elapsed >= PULSE_TICKS) { return 1f; }
        float t = elapsed / PULSE_TICKS;
        return 1f + PULSE_AMPLITUDE * (float) Math.sin(t * Math.PI);
    }

    /**
     * Packs shell alpha and goo tint into an ARGB color.
     *
     * @param state the chain marker render state
     * @return the packed ARGB shell color
     */
    private static int computeShellColor(ChainMarkerRenderState state) {
        int gooColor = state.gooType.getColor();
        int baseShellAlpha = state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        return (baseShellAlpha << ALPHA_SHIFT) | (gooColor & RGB_MASK);
    }

    /**
     * Looks up the fluid sprite and wraps its UV bounds.
     *
     * @param type the goo type to look up
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect lookupSpriteUv(GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        return new GooRenderUtil.UvRect(
                sprite.getU(0f), sprite.getV(0f),
                sprite.getU(1f), sprite.getV(1f));
    }

    /**
     * Translates to the face boundary where the blob splats into the wall.
     * All chain marker types now splat against their placed face.
     *
     * @param poseStack the pose stack for rendering
     * @param state the chain marker render state
     */
    private static void translateToFace(PoseStack poseStack, ChainMarkerRenderState state) {
        Direction face = state.placedFace;
        float ox = face.getStepX() * BLOCK_CENTER;
        float oy = face.getStepY() * BLOCK_CENTER;
        float oz = face.getStepZ() * BLOCK_CENTER;
        poseStack.translate(BLOCK_CENTER - ox, BLOCK_CENTER - oy, BLOCK_CENTER - oz);
    }

    /**
     * Applies the splat deformation: squish along the placed face axis,
     * widen perpendicular. Also applies the combined modifier (implosion,
     * pulse, target boost).
     *
     * @param poseStack the pose stack to scale
     * @param face      the placed face direction
     * @param modifier  combined implosion/pulse/target scale
     */
    private static void applySplatScale(PoseStack poseStack, Direction face, float modifier) {
        float wide = SPLAT_WIDTH * modifier;
        float thin = SPLAT_HEIGHT * modifier;
        float sx = face.getAxis() == Direction.Axis.X ? thin : wide;
        float sy = face.getAxis() == Direction.Axis.Y ? thin : wide;
        float sz = face.getAxis() == Direction.Axis.Z ? thin : wide;
        poseStack.scale(sx, sy, sz);
    }


    /**
     * Submits one fullbright translucent cube layer.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param color the ARGB tint color
     * @param half the half-size of the cube
     * @param uv the UV texture rectangle
     */
    private static void submitCubeLayer(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, int color, float half,
            GooRenderUtil.UvRect uv) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        CuboidBounds box = new CuboidBounds(-half, half, -half, half, -half, half);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> new RenderContext(pose, c, light).emitBox(color, box, uv));
    }


    /**
     * Implosion scale: 1.0 normally, shrinks to IMPLOSION_MIN in the
     * final IMPLOSION_TICKS before detonation. Smooth via partial tick.
     *
     * @param fuseRemaining the fuse ticks remaining
     * @param partialTick the partial tick for interpolation
     * @return the computed implosionScale
     */
    private static float computeImplosionScale(int fuseRemaining, float partialTick) {
        if (fuseRemaining > IMPLOSION_TICKS) { return 1f; }
        float smoothFuse = Math.max(0f, fuseRemaining - partialTick);
        float t = 1f - (smoothFuse / IMPLOSION_TICKS);
        return 1f - t * (1f - IMPLOSION_MIN);
    }

    // ── Ghost outline (connected fill + perimeter wireframe) ────────

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
    private static void submitGhostOutline(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (state.fuseRemaining <= 0) { return; }
        GooType type = state.gooType;
        if (type != GooType.ROCK && type != GooType.BLAZE) { return; }

        List<int[]> offsets = ChainFootprint.computeRegionOffsets(
                state.stackCount, state.flatMode, state.placedFace);
        Set<Long> filled = new HashSet<>(offsets.size());
        for (int[] o : offsets) {
            filled.add(packPos(o[X], o[Y], o[Z]));
        }

        int fillColor = (GHOST_FILL_ALPHA << ALPHA_SHIFT) | (type.getColor() & RGB_MASK);
        int wireColor = (GHOST_WIRE_ALPHA << ALPHA_SHIFT) | (type.getColor() & RGB_MASK);

        submitGhostFill(poseStack, nodeCollector, offsets, filled, fillColor);
        submitGhostWireframe(poseStack, nodeCollector, offsets, filled, wireColor);
    }

    /**
     * Emits translucent fill quads for exterior faces only.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB fill color
     */
    private static void submitGhostFill(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, List<int[]> offsets,
            Set<Long> filled, int color) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.QUADS_NO_DEPTH,
                (pose, c) -> {
                    FlatQuadContext ctx = new FlatQuadContext(pose, c);
                    for (int[] o : offsets) {
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (!filled.contains(packPos(nx, ny, nz))) {
                                emitFaceQuad(ctx, o, dir, color);
                            }
                        }
                    }
                });
    }

    /**
     * Emits wireframe edges only on the perimeter of the region.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param offsets       all 3D block offsets in the region
     * @param filled        packed position set for neighbor checks
     * @param color         the ARGB wire color
     */
    private static void submitGhostWireframe(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, List<int[]> offsets,
            Set<Long> filled, int color) {
        nodeCollector.submitCustomGeometry(poseStack,
                GooRenderTypes.LINES_NO_DEPTH,
                (pose, c) -> {
                    LineContext ctx = new LineContext(pose, c);
                    for (int[] o : offsets) {
                        for (Direction dir : Direction.values()) {
                            int nx = o[X] + dir.getStepX();
                            int ny = o[Y] + dir.getStepY();
                            int nz = o[Z] + dir.getStepZ();
                            if (filled.contains(packPos(nx, ny, nz))) { continue; }
                            emitPerimeterEdges(ctx, o, dir, filled, color);
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
            if (isCoplanarNeighbor(pos, faceDir, edge.neighborDir, filled)) {
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
    private static boolean isCoplanarNeighbor(int[] pos, Direction faceDir,
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
        float nx = edge.neighborDir.getStepX() * HALF;
        float ny = edge.neighborDir.getStepY() * HALF;
        float nz = edge.neighborDir.getStepZ() * HALF;
        float rx = edge.runDir.getStepX() * HALF;
        float ry = edge.runDir.getStepY() * HALF;
        float rz = edge.runDir.getStepZ() * HALF;
        float ex = cx + nx;
        float ey = cy + ny;
        float ez = cz + nz;
        ctx.emitEdge(ex - rx, ey - ry, ez - rz,
                     ex + rx, ey + ry, ez + rz,
                     color, GHOST_LINE_WIDTH);
    }

    /**
     * Describes one edge of a face quad: which neighboring block shares
     * this edge (neighborDir) and which axis the edge runs along (runDir).
     */
    private record FaceEdge(Direction neighborDir, Direction runDir) {}

    /**
     * Returns the 4 edges of a face, each identified by the adjacent
     * block direction and the axis the edge runs along.
     *
     * @param face the face direction
     * @return the four face edge descriptors
     */
    private static FaceEdge[] getFaceEdges(Direction face) {
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
    private static long packPos(int x, int y, int z) {
        long px = x & PACK_MASK;
        long py = (y & PACK_MASK) << PACK_Y_SHIFT;
        long pz = (z & PACK_MASK) << PACK_Z_SHIFT;
        return px | py | pz;
    }

}
