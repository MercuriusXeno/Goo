package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.VatBlock;
import com.mercuriusxeno.goo.block.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the fluid fill level inside a vat. When vats are vertically
 * stacked, the renderer treats the stack as one contiguous tank: fluid
 * fills from the bottom-most interior floor upward, and each vat renders
 * only the slice of the unified fluid column that falls within its block.
 */
public class VatBlockEntityRenderer
        implements BlockEntityRenderer<VatBlockEntity, VatRenderState> {

    /** Block atlas texture path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Interior wall insets in block coords (1/16 from each side). */
    private static final float MIN_X = 1f / 16f;
    private static final float MAX_X = 15f / 16f;
    private static final float MIN_Z = 1f / 16f;
    private static final float MAX_Z = 15f / 16f;

    /** Interior floor for a bottom/solo vat (above the base cap, 2/16). */
    static final float BASE_FLOOR = 2f / 16f;

    /** Interior ceiling for a top/solo vat (below the top cap, 14/16). */
    static final float CAP_CEILING = 14f / 16f;

    /** Inset from walls to prevent z-fighting (1px past inner wall faces). */
    private static final float INSET = 1.0f / 16f;

    /** Vertical nudge above floor to prevent z-fighting at low fill. */
    private static final float Y_EPSILON = 0.001f;

    /** Vat center X/Z for stream rendering. */
    private static final float VAT_CENTER_X = 0.5f;
    private static final float VAT_CENTER_Z = 0.5f;
    /** Sentinel index meaning no self-position found in stack. */
    private static final int NO_INDEX = -1;
    /** Epsilon threshold for full-submersion check. */
    private static final float SUBMERSION_EPSILON = 0.0001f;
    /** Number of intermediate vats excluded from total interior. */
    private static final int MIDDLE_EXCLUDED = 2;

    /**
     * Creates a vat BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public VatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public VatRenderState createRenderState() {
        return new VatRenderState();
    }

    // --- Render state extraction (walks the stack on the game thread) ---

    /**
     * Snapshots dominant type, fill fraction, and stack geometry by walking
     * the connected vat column. All vats in a stack share the same dominant
     * type and fill fraction so they render one unified fluid body.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param partialTick the partial tick for interpolation
     * @param cameraPos the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(VatBlockEntity be, VatRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;

        BlockState bs = be.getBlockState();
        state.vatAbove = bs.getValue(VatBlock.VAT_ABOVE);
        state.vatBelow = bs.getValue(VatBlock.VAT_BELOW);

        if (!state.vatAbove && !state.vatBelow) {
            // Solo vat - fast path, no stack walk needed
            extractSolo(be, state, gameTick);
            return;
        }

        extractStacked(be, state, gameTick);
    }

    /**
     * Fast path for solo (unstacked) vats.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param gameTick the current game tick
     */
    private static void extractSolo(VatBlockEntity be, VatRenderState state, long gameTick) {
        state.stackSize = 1;
        state.indexFromBottom = 0;

        GooContents contents = be.getContents();
        if (contents.isEmpty()) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = contents.largestType();
            state.fillFraction = Math.min(1f, (float) contents.totalVolume() / be.getCapacity());
        }
        state.streamType = be.getVatStreamType(gameTick);
        state.streamRate = be.getVatStreamRate(gameTick);
    }

    /**
     * Walks the vertical stack to compute unified fill and per-vat position.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param gameTick the current game tick
     */
    private static void extractStacked(VatBlockEntity be, VatRenderState state, long gameTick) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();

        BlockPos bottomPos = findStackBottom(level, pos);
        StackData data = collectStackData(level, bottomPos, pos, gameTick);

        state.stackSize = data.stackSize;
        state.indexFromBottom = data.selfIndex >= 0 ? data.selfIndex : 0;

        if (data.totalVolume <= 0 || data.totalCapacity <= 0) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = data.merged.largestType();
            state.fillFraction = Math.min(1f, (float) data.totalVolume / data.totalCapacity);
        }

        state.streamType = data.topStreamType;
        state.streamRate = data.topStreamRate;
    }

    /** Walks downward from the given position to find the bottom of the vat stack.
     *
     * @param level the current level
     * @param pos   the starting position
     * @return the bottom-most vat position in the stack
     */
    private static BlockPos findStackBottom(Level level, BlockPos pos) {
        BlockPos cursor = pos;
        while (level.getBlockState(cursor.below()).getBlock() instanceof VatBlock) {
            cursor = cursor.below();
        }
        return cursor;
    }

    /** Walks upward from the stack bottom, accumulating volume, capacity, and stream state.
     *
     * @param level     the current level
     * @param bottomPos the bottom-most vat position
     * @param selfPos   the position of the vat being rendered
     * @param gameTick  the current game tick
     * @return aggregated stack data
     */
    private static StackData collectStackData(Level level, BlockPos bottomPos,
            BlockPos selfPos, long gameTick) {
        long totalVolume = 0;
        long totalCapacity = 0;
        GooContents merged = GooContents.EMPTY;
        int stackSize = 0;
        int selfIndex = NO_INDEX;
        GooType topStreamType = null;
        int topStreamRate = 0;

        BlockPos cursor = bottomPos;
        while (true) {
            BlockEntity curBe = level.getBlockEntity(cursor);
            if (curBe instanceof VatBlockEntity vat) {
                GooContents vc = vat.getContents();
                totalVolume += vc.totalVolume();
                totalCapacity += vat.getCapacity();
                merged = merged.mergeWith(vc);
                if (cursor.equals(selfPos)) {
                    selfIndex = stackSize;
                }
                GooType st = vat.getVatStreamType(gameTick);
                if (st != null) {
                    topStreamType = st;
                    topStreamRate = vat.getVatStreamRate(gameTick);
                }
                stackSize++;
            }

            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) { break; }
            cursor = cursor.above();
        }
        return new StackData(totalVolume, totalCapacity, merged, stackSize,
                selfIndex, topStreamType, topStreamRate);
    }

    /** Aggregated data from walking a vat stack. */
    private record StackData(long totalVolume, long totalCapacity, GooContents merged,
            int stackSize, int selfIndex, @Nullable GooType topStreamType, int topStreamRate) {}

    // --- Geometry submission ---

    /**
     * Submits fluid geometry and stream if the vat contains goo or is receiving.
     *
     * @param state the block state
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState the camera render state
     */
    @Override
    public void submit(VatRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.dominantType != null && state.fillFraction > 0f) {
            submitFluid(poseStack, nodeCollector, state);
        }
        if (state.streamType != null) {
            submitStream(poseStack, nodeCollector, state);
        }
    }

    /**
     * Submits the fluid quad(s) for this vat's slice of the unified column.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitFluid(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, VatRenderState state) {
        int light = state.lightCoords;
        GooType type = state.dominantType;
        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> renderFluid(pose, c, light, type, state));
    }

    /**
     * Renders this vat's portion of the unified fluid column.
     * Computes local floor/ceiling from stack position, then determines
     * how much of this vat's interior is submerged.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param type the goo type
     * @param state the block state
     */
    private static void renderFluid(PoseStack.Pose pose, VertexConsumer c,
            int light, GooType type, VatRenderState state) {
        // Per-vat interior bounds
        float localFloor = state.vatBelow ? 0f : BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : CAP_CEILING;
        float localHeight = localCeiling - localFloor;

        // Compute this vat's local fill from the stack-wide fill fraction
        float localFill = computeLocalFill(state, localFloor, localCeiling);
        if (localFill <= 0f) { return; }

        float x0 = MIN_X + INSET;
        float x1 = MAX_X - INSET;
        float z0 = MIN_Z + INSET;
        float z1 = MAX_Z - INSET;
        float yBot = localFloor + (state.vatBelow ? 0f : Y_EPSILON);
        float yTop = localFloor + localFill;

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Top surface only if this vat contains the air-liquid interface
        boolean isFullySubmerged = localFill >= localHeight - SUBMERSION_EPSILON;
        if (!isFullySubmerged) {
            GooRenderUtil.liquidSurface(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
                x0, z0, x1, z1, yTop, u0, u1, v0, v1);
            // Bottom-facing winding so the surface is visible from below
            GooRenderUtil.liquidSurfaceDown(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
                x0, z0, x1, z1, yTop, u0, u1, v0, v1);
        }

        // Side faces - UV pinned at bottom so the fluid appears to rise upward.
        float fillRatio = (yTop - yBot) / localHeight;
        float sideV0 = v1 - fillRatio * (v1 - v0);
        CanisterGeometry.faceNorth(pose, c, light, x0, yBot, z0, x1, yTop, u0, u1, sideV0, v1);
        CanisterGeometry.faceSouth(pose, c, light, x0, yBot, z1, x1, yTop, u0, u1, sideV0, v1);
        CanisterGeometry.faceWest(pose, c, light, x0, yBot, z0, yTop, z1, u0, u1, sideV0, v1);
        CanisterGeometry.faceEast(pose, c, light, x1, yBot, z0, yTop, z1, u0, u1, sideV0, v1);
    }

    /**
     * Computes how much of this vat's interior is filled, based on the
     * stack-wide fill fraction and this vat's position in the stack.
     *
     * @return fill height in block coords within [0, localHeight]
     *
     * @param state the block state
     * @param localFloor the local interior floor Y
     * @param localCeiling the local interior ceiling Y
     */
    static float computeLocalFill(VatRenderState state, float localFloor, float localCeiling) {
        float localHeight = localCeiling - localFloor;
        if (state.stackSize <= 1) {
            // Solo vat: simple proportional fill
            return state.fillFraction * localHeight;
        }

        // Compute total interior height and cumulative height below this vat
        float totalInterior = computeTotalInterior(state.stackSize);
        float cumulativeBelow = computeCumulativeBelow(state.indexFromBottom, state.stackSize);
        float globalFillHeight = state.fillFraction * totalInterior;

        return Math.max(0f, Math.min(globalFillHeight - cumulativeBelow, localHeight));
    }

    /**
     * Total interior height of a stack of N vats (in block units).
     *
     * @param stackSize the number of vats in the stack
     * @return the computed totalInterior
     */
    private static float computeTotalInterior(int stackSize) {
        if (stackSize <= 1) { return CAP_CEILING - BASE_FLOOR; }
        // Bottom (1.0-BASE_FLOOR) + middles (1.0 each) + top (CAP_CEILING)
        return 1.0f - BASE_FLOOR + (stackSize - MIDDLE_EXCLUDED) * 1.0f + CAP_CEILING;
    }

    /**
     * Cumulative interior height below the vat at indexFromBottom.
     *
     * @param index the zero-based index from the bottom
     * @param stackSize the number of vats in the stack
     * @return the computed cumulativeBelow
     */
    private static float computeCumulativeBelow(int index, int stackSize) {
        if (index == 0) { return 0f; }
        // Bottom vat contributes (1.0 - BASE_FLOOR)
        float below = 1.0f - BASE_FLOOR;
        // Each middle vat below this one contributes 1.0
        int middlesBelowCount = index - 1; // index 1 = first middle, no extra middles below
        below += middlesBelowCount * 1.0f;
        return below;
    }

    // --- Stream rendering ---

    /**
     * Submits a fluid stream segment for this vat's slice of the stack.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    private static void submitStream(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, VatRenderState state) {
        int light = state.lightCoords;
        float anim = state.animationTime;
        GooType type = state.streamType;
        float rate = state.streamRate;

        float localFloor = state.vatBelow ? 0f : BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : CAP_CEILING;
        float localHeight = localCeiling - localFloor;
        float localFill = computeLocalFill(state, localFloor, localCeiling);

        // Skip stream if this vat is fully submerged (no air gap)
        if (localFill >= localHeight - SUBMERSION_EPSILON) { return; }

        float yTop = localCeiling;
        float yBottom = localFloor + Math.max(localFill, 0f);

        // Don't render stream below fluid surface
        if (yBottom >= yTop) { return; }

        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> GooStreamRenderer.renderStream(pose, c, light,
                VAT_CENTER_X, VAT_CENTER_Z, yTop, yBottom,
                type, rate, anim));
    }
}
