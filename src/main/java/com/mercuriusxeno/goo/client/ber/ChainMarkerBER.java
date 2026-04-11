package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import com.mercuriusxeno.goo.client.throwing.ThrowFreezeState;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the chain marker as a slime-like glowing orb. Two layers:
 * inner core with the goo fluid texture, outer translucent shell with
 * goo-tinted color. Both are emissive (fullbright). Size scales with
 * stack count. Implodes inward in the final ticks before detonation.
 */
public class ChainMarkerBER
        implements BlockEntityRenderer<ChainMarkerBlockEntity, ChainMarkerRenderState> {

    /** Block atlas path for fluid sprite lookups. */
    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Base inner core half-size in block units (3 pixels). */
    private static final float CORE_BASE = 3f / 16f;

    /** Base outer shell half-size in block units (5 pixels). */
    private static final float SHELL_BASE = 5f / 16f;

    /** Maximum scale multiplier at max stacks. */
    private static final float MAX_SCALE = 1.8f;

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
    /** Minimum visible radius for the black-hole sphere so it never collapses to a single pixel. */
    private static final float BLACKHOLE_MIN_RADIUS = 0.25f;
    /** Solid alpha (0xFF) for the blackhole quad vertices; progress lives in the R channel. */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /** Maximum byte value for a progress-in-R channel mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    /** Bit shift for the red channel in an ARGB color. */
    private static final int RED_CHANNEL_SHIFT = 16;
    /** Half-extent of the blackhole billboard quad in local units; runs from -HALF to +HALF. */
    private static final float QUAD_HALF_EXTENT = 1f;

    public ChainMarkerBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public ChainMarkerRenderState createRenderState() {
        return new ChainMarkerRenderState();
    }

    @Override
    public void extractRenderState(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        extractCoreFields(be, state, partialTick);
        extractFuseAndTarget(be, state);
        extractImplosionFields(be, state);
    }

    /**
     * Copies the phase machine state so the submit pass can branch between
     * the orb and the black-hole shader sphere.
     *
     * @param be    the block entity
     * @param state the render state to populate
     */
    private static void extractImplosionFields(ChainMarkerBlockEntity be,
            ChainMarkerRenderState state) {
        state.phase = be.getPhase();
        state.implodeProgress = be.getProgress();
        state.implodeRadius = be.getCurrentRadius();
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
        if (state.phase == ChainMarkerBlockEntity.Phase.IMPLODING) {
            submitBlackholeSphere(state, poseStack, nodeCollector, cameraState);
            return;
        }
        submitFuseOrb(state, poseStack, nodeCollector);
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
        float scale = computeCombinedScale(state);
        int shellColor = computeShellColor(state);
        GooRenderUtil.UvRect uv = lookupSpriteUv(state.gooType);

        poseStack.pushPose();
        translateToCenter(poseStack, state);
        submitCoreLayers(poseStack, nodeCollector, scale, shellColor, uv);
        poseStack.popPose();
    }

    /**
     * Draws the goal-013 black-hole billboard quad during IMPLODING. The
     * quad is centered on the block, scaled to the shrinking implosion
     * radius, rotated to face the camera, and its vertex color carries the
     * implosion progress in the R channel (read by the fragment shader).
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState   the camera state used to billboard the quad
     */
    private static void submitBlackholeSphere(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            CameraRenderState cameraState) {
        // Hold at full effect radius for the entire implosion so the sphere
        // fully encompasses the destruction zone. BE removal at POPPING
        // takes the visual with it, no shrink-ramp needed in the BER.
        float visibleRadius = Math.max(BLACKHOLE_MIN_RADIUS, state.implodeRadius);
        int color = packBlackholeColor(state.implodeProgress);

        poseStack.pushPose();
        poseStack.translate(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        poseStack.mulPose(cameraState.orientation);
        poseStack.scale(visibleRadius, visibleRadius, visibleRadius);
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitBillboardQuad(pose, c, color));
        poseStack.popPose();
    }

    /**
     * Packs the implosion progress into an ARGB color's R channel so the
     * vertex shader can forward it to the fragment shader via
     * {@code Color.r}. Alpha is solid; G and B are unused.
     *
     * @param progress implosion progress in [0, 1]
     * @return the packed ARGB color
     */
    private static int packBlackholeColor(float progress) {
        float clamped = Math.min(1f, Math.max(0f, progress));
        int progressByte = Math.round(clamped * PROGRESS_BYTE_MAX);
        return (BLACKHOLE_ALPHA << ALPHA_SHIFT) | (progressByte << RED_CHANNEL_SHIFT);
    }

    /**
     * Emits a unit quad at local {@code (±1, ±1, 0)}. The caller is
     * responsible for translating, scaling, and billboarding the outer
     * pose before submission.
     *
     * @param pose  the current pose entry
     * @param c     the vertex consumer
     * @param color ARGB vertex color (progress in R, solid alpha)
     */
    private static void emitBillboardQuad(PoseStack.Pose pose, VertexConsumer c, int color) {
        float lo = -QUAD_HALF_EXTENT;
        float hi = QUAD_HALF_EXTENT;
        FlatQuadCtx ctx = new FlatQuadCtx(pose, c);
        ctx.vertex(lo, lo, 0f, color);
        ctx.vertex(lo, hi, 0f, color);
        ctx.vertex(hi, hi, 0f, color);
        ctx.vertex(hi, lo, 0f, color);
    }

    /**
     * Multiplies stack, implosion, and target scales into a single factor.
     *
     * @param state the chain marker render state
     * @return the combined scale factor
     */
    private static float computeCombinedScale(ChainMarkerRenderState state) {
        float stackScale = computeStackScale(state.stackCount, state.maxStacks);
        float implosionScale = computeImplosionScale(
                state.fuseRemaining, state.partialTick);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        return stackScale * implosionScale * targetBoost;
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
     * Translates to block center, offsetting rock markers into the placed face.
     *
     * @param poseStack the pose stack for rendering
     * @param state the chain marker render state
     */
    private static void translateToCenter(PoseStack poseStack, ChainMarkerRenderState state) {
        Direction face = state.gooType == GooType.ROCK ? state.placedFace : null;
        float ox = face != null ? face.getStepX() * BLOCK_CENTER : 0f;
        float oy = face != null ? face.getStepY() * BLOCK_CENTER : 0f;
        float oz = face != null ? face.getStepZ() * BLOCK_CENTER : 0f;
        poseStack.translate(BLOCK_CENTER - ox, BLOCK_CENTER - oy, BLOCK_CENTER - oz);
    }

    /**
     * Submits inner core and outer shell cube geometry.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param scale the combined scale factor
     * @param shellColor the ARGB shell tint
     * @param uv the UV texture rectangle
     */
    private static void submitCoreLayers(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, float scale, int shellColor,
            GooRenderUtil.UvRect uv) {
        float ch = CORE_BASE * scale;
        float sh = SHELL_BASE * scale;
        submitCubeLayer(poseStack, nodeCollector, GooRenderUtil.OPAQUE_WHITE, ch, uv);
        submitCubeLayer(poseStack, nodeCollector, shellColor, sh, uv);
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
                (pose, c) -> new RenderCtx(pose, c, light).emitBox(color, box, uv));
    }

    /**
     * Scale multiplier from stack count. Linear 1.0 to MAX_SCALE.
     *
     * @param stacks the current stack count
     * @param maxStacks the maximum stack count
     * @return the computed stackScale
     */
    private static float computeStackScale(int stacks, int maxStacks) {
        if (maxStacks <= 1) { return 1f; }
        float t = (float) (stacks - 1) / (maxStacks - 1);
        return 1f + t * (MAX_SCALE - 1f);
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

}
