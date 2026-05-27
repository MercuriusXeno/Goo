package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
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

    /**
     * Interior floor for a bottom/solo vat (above the base cap, 2/16).
     */
    static final float BASE_FLOOR = 2f / 16f;
    /**
     * Interior ceiling for a top/solo vat (below the top cap, 14/16).
     */
    static final float CAP_CEILING = 14f / 16f;
    /**
     * Block atlas texture path for fluid sprite lookups.
     */
    private static final Identifier BLOCK_ATLAS_TEXTURE =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");
    /**
     * Vat center X/Z for stream rendering.
     */
    private static final float VAT_CENTER_X = 0.5f;
    private static final float VAT_CENTER_Z = 0.5f;
    /**
     * Sentinel index meaning no self-position found in stack.
     */
    private static final int NO_INDEX = -1;
    /**
     * Epsilon threshold for full-submersion check.
     */
    private static final float SUBMERSION_EPSILON = 0.0001f;

    /**
     * Creates a vat BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public VatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Reads vatAbove/vatBelow from the block state into the render snapshot.
     *
     * @param be    the vat block entity
     * @param state the render state snapshot to populate
     */
    private static void extractVatProperties(VatBlockEntity be, VatRenderState state) {
        BlockState bs = be.getBlockState();
        state.vatAbove = bs.getValue(VatBlock.VAT_ABOVE);
        state.vatBelow = bs.getValue(VatBlock.VAT_BELOW);
    }

    // --- Render state extraction (walks the stack on the game thread) ---

    /**
     * Fast path for solo (unstacked) vats.
     *
     * @param be       the block entity instance
     * @param state    the block state
     * @param gameTick the current game tick
     */
    private static void extractSolo(VatBlockEntity be, VatRenderState state, long gameTick) {
        state.stackSize = 1;
        state.indexFromBottom = 0;
        applySoloFill(be, state);
        state.streamType = be.getVatStreamType(gameTick);
        state.streamRate = be.getVatStreamRate(gameTick);
    }

    /**
     * Sets dominant type and fill fraction from a solo vat's contents.
     *
     * @param be    the vat block entity
     * @param state the render state snapshot to populate
     */
    private static void applySoloFill(VatBlockEntity be, VatRenderState state) {
        GooContents contents = be.getContents();
        if (contents.isEmpty()) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = contents.largestType();
            state.fillFraction = Math.min(1f, (float) contents.totalVolume() / be.getCapacity());
        }
    }

    /**
     * Walks the vertical stack to compute unified fill and per-vat position.
     *
     * @param be       the block entity instance
     * @param state    the block state
     * @param gameTick the current game tick
     */
    private static void extractStacked(VatBlockEntity be, VatRenderState state, long gameTick) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        BlockPos bottomPos = findStackBottom(level, pos);
        StackData data = collectStackData(level, bottomPos, pos, gameTick);

        state.stackSize = data.stackSize;
        state.indexFromBottom = data.selfIndex >= 0 ? data.selfIndex : 0;
        applyStackFill(state, data);
        state.streamType = data.topStreamType;
        state.streamRate = data.topStreamRate;
    }

    /**
     * Applies dominant type and fill fraction from aggregated stack data.
     *
     * @param state the render state snapshot to populate
     * @param data  the aggregated stack data
     */
    private static void applyStackFill(VatRenderState state, StackData data) {
        if (data.totalVolume <= 0 || data.totalCapacity <= 0) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = data.merged.largestType();
            state.fillFraction = Math.min(1f, (float) data.totalVolume / data.totalCapacity);
        }
    }

    /**
     * Walks downward from the given position to find the bottom of the vat stack.
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

    /**
     * Walks upward from the stack bottom, accumulating volume, capacity, and stream state.
     *
     * @param level     the current level
     * @param bottomPos the bottom-most vat position
     * @param selfPos   the position of the vat being rendered
     * @param gameTick  the current game tick
     * @return aggregated stack data
     */
    private static StackData collectStackData(Level level, BlockPos bottomPos,
                                              BlockPos selfPos, long gameTick) {
        StackAccumulator acc = new StackAccumulator();
        walkStackUpward(level, bottomPos, selfPos, gameTick, acc);
        return acc.toResult();
    }

    /**
     * Walks the vat stack upward from the bottom, accumulating each vat's contribution.
     *
     * @param level    the current level
     * @param start    the bottom-most vat position in the stack
     * @param selfPos  the position of the vat being rendered
     * @param gameTick the current game tick
     * @param acc      the mutable accumulator collecting stack data
     */
    private static void walkStackUpward(Level level, BlockPos start,
                                        BlockPos selfPos, long gameTick, StackAccumulator acc) {
        BlockPos cursor = start;
        while (true) {
            BlockEntity curBe = level.getBlockEntity(cursor);
            if (curBe instanceof VatBlockEntity vat) {
                accumulateVat(acc, vat, cursor, selfPos, gameTick);
            }
            if (!(level.getBlockState(cursor.above()).getBlock() instanceof VatBlock)) {
                break;
            }
            cursor = cursor.above();
        }
    }

    /**
     * Accumulates volume, capacity, and stream data from a single vat into the accumulator.
     *
     * @param acc      the stack accumulator being built
     * @param vat      the vat block entity contributing data
     * @param cursor   the block position of the vat being accumulated
     * @param selfPos  the position of the rendering vat (base of stack walk)
     * @param gameTick the current game tick for animation timing
     */
    private static void accumulateVat(StackAccumulator acc, VatBlockEntity vat,
                                      BlockPos cursor, BlockPos selfPos, long gameTick) {
        GooContents vc = vat.getContents();
        acc.totalVolume += vc.totalVolume();
        acc.totalCapacity += vat.getCapacity();
        acc.merged = acc.merged.mergeWith(vc);
        if (cursor.equals(selfPos)) {
            acc.selfIndex = acc.stackSize;
        }
        accumulateStream(acc, vat, gameTick);
        acc.stackSize++;
    }

    /**
     * Updates the accumulator's stream state if this vat has an active stream.
     *
     * @param acc      the stack accumulator being built
     * @param vat      the vat block entity
     * @param gameTick the current game tick for animation timing
     */
    private static void accumulateStream(StackAccumulator acc,
                                         VatBlockEntity vat, long gameTick) {
        GooType st = vat.getVatStreamType(gameTick);
        if (st != null) {
            acc.topStreamType = st;
            acc.topStreamRate = vat.getVatStreamRate(gameTick);
        }
    }

    /**
     * Submits the fluid quad(s) for this vat's slice of the unified column.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state         the block state
     */
    private static void submitFluid(PoseStack poseStack,
                                    SubmitNodeCollector nodeCollector, VatRenderState state) {
        // FULL_BRIGHT lightmap UV per fluid vertex makes the lightmap
        // multiplication a no-op (samples white), so the fluid is bright
        // regardless of world light. The vat block itself is a block model
        // (not BER body geometry), so there's no buffer-share concern --
        // fluid lives alone on entityTranslucent(BLOCK_ATLAS).
        GooType type = state.dominantType;
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> VatFluidRenderer.renderFluid(new RenderContext(pose, c, LightCoordsUtil.FULL_BRIGHT), type, state));
    }

    /**
     * Submits a fluid stream segment for this vat's slice of the stack.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state         the block state
     */
    private static void submitStream(PoseStack poseStack,
                                     SubmitNodeCollector nodeCollector, VatRenderState state) {
        float[] sb = computeStreamBounds(state);
        if (sb.length == 0) {
            return;
        }
        int light = state.lightCoords;
        float anim = state.animationTime;
        GooType type = state.streamType;
        float rate = state.streamRate;
        emitStreamGeometry(poseStack, nodeCollector, light, anim, type, rate, sb[0], sb[1]);
    }

    /**
     * Submits the stream geometry draw call for the given Y range.
     *
     * @param poseStack     the current pose transformation stack
     * @param nodeCollector the render node collector for geometry submission
     * @param light         the packed light level for shading
     * @param anim          the animation tick fraction
     * @param type          the goo type for color lookup
     * @param rate          the stream flow rate for animation speed
     * @param yTop          the stream top Y coordinate
     * @param yBottom       the stream bottom Y coordinate
     */
    private static void emitStreamGeometry(PoseStack poseStack,
                                           SubmitNodeCollector nodeCollector, int light, float anim,
                                           GooType type, float rate, float yTop, float yBottom) {
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS_TEXTURE),
                (pose, c) -> GooStreamRenderer.renderStream(new RenderContext(pose, c, light),
                        VAT_CENTER_X, VAT_CENTER_Z, yTop, yBottom,
                        type, rate, anim));
    }

    /**
     * Computes stream Y bounds, or null if the vat is fully submerged or inverted.
     *
     * @param state the render state snapshot to populate
     * @return the stream Y bounds {yBot, yTop}, or empty if not visible
     */
    private static float[] computeStreamBounds(VatRenderState state) {
        float localFloor = state.vatBelow ? 0f : BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : CAP_CEILING;
        float localHeight = localCeiling - localFloor;
        float localFill = VatFluidRenderer.computeLocalFill(state, localFloor, localCeiling);

        if (localFill >= localHeight - SUBMERSION_EPSILON) {
            return new float[0];
        }
        float yTop = localCeiling;
        float yBottom = localFloor + Math.max(localFill, 0f);
        if (yBottom >= yTop) {
            return new float[0];
        }
        return new float[]{yTop, yBottom};
    }

    // --- Geometry submission ---

    @Override
    public VatRenderState createRenderState() {
        return new VatRenderState();
    }

    /**
     * Snapshots dominant type, fill fraction, and stack geometry by walking
     * the connected vat column. All vats in a stack share the same dominant
     * type and fill fraction so they render one unified fluid body.
     *
     * @param be            the block entity instance
     * @param state         the block state
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(VatBlockEntity be, VatRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;
        extractVatProperties(be, state);

        if (!state.vatAbove && !state.vatBelow) {
            extractSolo(be, state, gameTick);
            return;
        }
        extractStacked(be, state, gameTick);
    }

    // --- Stream rendering ---

    /**
     * Submits fluid geometry and stream if the vat contains goo or is receiving.
     *
     * @param state         the block state
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
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
     * Mutable accumulator for stack walk data, converted to StackData when complete.
     */
    private static final class StackAccumulator {
        int totalVolume;
        int totalCapacity;
        GooContents merged = GooContents.EMPTY;
        int stackSize;
        int selfIndex = NO_INDEX;
        @Nullable GooType topStreamType;
        int topStreamRate;

        StackData toResult() {
            return new StackData(totalVolume, totalCapacity, merged,
                    stackSize, selfIndex, topStreamType, topStreamRate);
        }
    }

    /**
     * Aggregated data from walking a vat stack.
     */
    private record StackData(int totalVolume, int totalCapacity, GooContents merged,
                             int stackSize, int selfIndex, @Nullable GooType topStreamType, int topStreamRate) {
    }
}
