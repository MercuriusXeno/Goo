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

    /** Creates a vat BER. Context is unused. */
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

    /** Fast path for solo (unstacked) vats. */
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

    /** Walks the vertical stack to compute unified fill and per-vat position. */
    private static void extractStacked(VatBlockEntity be, VatRenderState state, long gameTick) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();

        // Walk down to bottom
        int belowCount = 0;
        long totalVolume = 0;
        long totalCapacity = 0;
        GooContents merged = GooContents.EMPTY;

        BlockPos cursor = pos;
        while (level.getBlockState(cursor.below()).getBlock() instanceof VatBlock) {
            cursor = cursor.below();
            belowCount++;
        }
        BlockPos bottomPos = cursor;

        // Walk up from bottom, collecting data
        int stackSize = 0;
        int selfIndex = -1;
        GooType topStreamType = null;
        int topStreamRate = 0;

        cursor = bottomPos;
        while (true) {
            BlockEntity curBe = level.getBlockEntity(cursor);
            if (curBe instanceof VatBlockEntity vat) {
                GooContents vc = vat.getContents();
                totalVolume += vc.totalVolume();
                totalCapacity += vat.getCapacity();
                merged = merged.mergeWith(vc);
                if (cursor.equals(pos)) {
                    selfIndex = stackSize;
                }
                // Track stream state from the topmost vat
                GooType st = vat.getVatStreamType(gameTick);
                if (st != null) {
                    topStreamType = st;
                    topStreamRate = vat.getVatStreamRate(gameTick);
                }
                stackSize++;
            }

            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) break;
            cursor = cursor.above();
        }

        state.stackSize = stackSize;
        state.indexFromBottom = selfIndex >= 0 ? selfIndex : 0;

        if (totalVolume <= 0 || totalCapacity <= 0) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = merged.largestType();
            state.fillFraction = Math.min(1f, (float) totalVolume / totalCapacity);
        }

        // Propagate stream from top vat to all vats in stack
        state.streamType = topStreamType;
        state.streamRate = topStreamRate;
    }

    // --- Geometry submission ---

    /** Submits fluid geometry and stream if the vat contains goo or is receiving. */
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

    /** Submits the fluid quad(s) for this vat's slice of the unified column. */
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
     */
    private static void renderFluid(PoseStack.Pose pose, VertexConsumer c,
            int light, GooType type, VatRenderState state) {
        // Per-vat interior bounds
        float localFloor = state.vatBelow ? 0f : BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : CAP_CEILING;
        float localHeight = localCeiling - localFloor;

        // Compute this vat's local fill from the stack-wide fill fraction
        float localFill = computeLocalFill(state, localFloor, localCeiling);
        if (localFill <= 0f) return;

        float x0 = MIN_X + INSET, x1 = MAX_X - INSET;
        float z0 = MIN_Z + INSET, z1 = MAX_Z - INSET;
        float yBot = localFloor + (state.vatBelow ? 0f : Y_EPSILON);
        float yTop = localFloor + localFill;

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        // Top surface only if this vat contains the air-liquid interface
        boolean isFullySubmerged = localFill >= localHeight - 0.0001f;
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

    /** Total interior height of a stack of N vats (in block units). */
    private static float computeTotalInterior(int stackSize) {
        if (stackSize <= 1) return CAP_CEILING - BASE_FLOOR;
        // Bottom (1.0-BASE_FLOOR) + middles (1.0 each) + top (CAP_CEILING)
        return (1.0f - BASE_FLOOR) + (stackSize - 2) * 1.0f + CAP_CEILING;
    }

    /** Cumulative interior height below the vat at indexFromBottom. */
    private static float computeCumulativeBelow(int index, int stackSize) {
        if (index == 0) return 0f;
        // Bottom vat contributes (1.0 - BASE_FLOOR)
        float below = 1.0f - BASE_FLOOR;
        // Each middle vat below this one contributes 1.0
        int middlesBelowCount = index - 1; // index 1 = first middle, no extra middles below
        below += middlesBelowCount * 1.0f;
        return below;
    }

    // --- Stream rendering ---

    /** Submits a fluid stream segment for this vat's slice of the stack. */
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
        if (localFill >= localHeight - 0.0001f) return;

        float yTop = localCeiling;
        float yBottom = localFloor + Math.max(localFill, 0f);

        // Don't render stream below fluid surface
        if (yBottom >= yTop) return;

        nodeCollector.submitCustomGeometry(poseStack,
            RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
            (pose, c) -> GooStreamRenderer.renderStream(pose, c, light,
                VAT_CENTER_X, VAT_CENTER_Z, yTop, yBottom,
                type, rate, anim));
    }
}
