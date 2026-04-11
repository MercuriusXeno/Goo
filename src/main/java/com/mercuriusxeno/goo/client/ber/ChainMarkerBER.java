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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
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
    /** Extra world-space margin added to the effective implosion radius so the sphere fully occludes the blast zone. */
    private static final float OCCLUSION_MARGIN = 2f;
    /** Solid alpha (0xFF) for the blackhole quad vertices; progress lives in the R channel. */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /** Maximum byte value for a progress-in-R channel mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    /** Bit shift for the red channel in an ARGB color. */
    private static final int RED_CHANNEL_SHIFT = 16;
    /** Squared floor: if the camera is closer than this to the block center, skip billboarding (no valid direction). */
    private static final double MIN_CAM_DIST_SQ = 1e-6;
    /** Minimum basis-vector length before we fall back to a default "right" axis (camera directly above/below). */
    private static final double MIN_BASIS_LEN = 1e-6;
    /** Half-extent of the render bounding box around a chain marker, in blocks. Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;

    public ChainMarkerBER(BlockEntityRendererProvider.Context context) {
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
        state.visibleScale = be.getVisibleScale();
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
        if (state.phase == ChainMarkerBlockEntity.Phase.EXPAND
                || state.phase == ChainMarkerBlockEntity.Phase.CONTRACT) {
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
     * Draws the goal-013 black-hole billboard quad during IMPLODING. Builds
     * the billboard basis (right, up) manually in world space from the
     * camera-to-block direction so the quad truly faces the camera without
     * relying on {@code mulPose(cameraState.orientation)} — which, in the
     * 26.1 BER path, was leaving the quad effectively locked to screen
     * space rather than rotating it into world space.
     *
     * <p>The four corners are emitted as world-space positions (relative to
     * the BER pose's current frame, which is the block corner in camera-
     * relative coords). UVs are supplied explicitly via the
     * {@code POSITION_TEX_COLOR} vertex format, so the fragment shader can
     * read a clean {@code [0, 1]} UV and the sphere is symmetric.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering (already at block corner)
     * @param nodeCollector the render node collector
     * @param cameraState   camera state; we read {@code pos} only
     */
    private static void submitBlackholeSphere(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector,
            CameraRenderState cameraState) {
        float fullRadius = state.implodeRadius + OCCLUSION_MARGIN;
        float visibleRadius = Math.max(BLACKHOLE_MIN_RADIUS, fullRadius * state.visibleScale);
        int color = packBlackholeColor(state.visibleScale);
        BillboardBasis basis = computeBillboardBasis(state.blockPos, cameraState.pos, visibleRadius);
        if (basis == null) { return; }

        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitWorldSpaceQuad(pose, c, basis, color));
    }

    /**
     * Four corners of a camera-facing quad expressed in block-local
     * coordinates (relative to the block's lower corner, so block center is
     * {@code (0.5, 0.5, 0.5)}). Filled in by
     * {@link #computeBillboardBasis}.
     *
     * @param c00 bottom-left corner (UV 0,0)
     * @param c10 bottom-right corner (UV 1,0)
     * @param c11 top-right corner (UV 1,1)
     * @param c01 top-left corner (UV 0,1)
     */
    private record BillboardBasis(Vector3f c00, Vector3f c10, Vector3f c11, Vector3f c01) {}

    /**
     * Computes the four corners of a camera-facing quad at the block's
     * center, using the world-space camera position to derive a right/up
     * basis via cross products. Returns null if the camera is exactly at
     * the block center (no valid direction).
     *
     * @param blockPos block position in world coordinates
     * @param cameraPos camera position in world coordinates
     * @param radius world-space half-extent of the quad in blocks
     * @return the basis, or null if the camera is degenerate
     */
    private static @Nullable BillboardBasis computeBillboardBasis(BlockPos blockPos,
            Vec3 cameraPos, float radius) {
        double dx = cameraPos.x() - (blockPos.getX() + BLOCK_CENTER);
        double dy = cameraPos.y() - (blockPos.getY() + BLOCK_CENTER);
        double dz = cameraPos.z() - (blockPos.getZ() + BLOCK_CENTER);
        double dSq = dx * dx + dy * dy + dz * dz;
        if (dSq < MIN_CAM_DIST_SQ) { return null; }
        double invD = 1.0 / Math.sqrt(dSq);
        Vector3f right = computeRight(dx * invD, dz * invD);
        Vector3f up = computeUp(dx * invD, dy * invD, dz * invD, right);
        return buildCornersFromBasis(right, up, radius);
    }

    /**
     * {@code right = normalize(worldUp × toCamera)} where worldUp is
     * {@code (0, 1, 0)}. If toCamera is near-vertical (camera directly above
     * or below the block), the cross product degenerates; falls back to
     * world {@code +X} as "right".
     *
     * @param tcx toCamera x component (already normalized)
     * @param tcz toCamera z component (already normalized)
     * @return the right basis vector (unit length, y = 0)
     */
    private static Vector3f computeRight(double tcx, double tcz) {
        double rx = tcz;
        double rz = -tcx;
        double rLen = Math.sqrt(rx * rx + rz * rz);
        if (rLen < MIN_BASIS_LEN) { return new Vector3f(1f, 0f, 0f); }
        return new Vector3f((float) (rx / rLen), 0f, (float) (rz / rLen));
    }

    /**
     * {@code up = toCamera × right}. Unit length because toCamera and right
     * are unit and perpendicular.
     *
     * @param tcx   toCamera x
     * @param tcy   toCamera y
     * @param tcz   toCamera z
     * @param right right basis vector
     * @return the up basis vector
     */
    private static Vector3f computeUp(double tcx, double tcy, double tcz, Vector3f right) {
        double ux = tcy * right.z - tcz * right.y;
        double uy = tcz * right.x - tcx * right.z;
        double uz = tcx * right.y - tcy * right.x;
        return new Vector3f((float) ux, (float) uy, (float) uz);
    }

    /**
     * Builds the four quad corners from a right/up basis, scaled and
     * centered at the block's local center {@code (0.5, 0.5, 0.5)}.
     *
     * @param right  right basis vector
     * @param up     up basis vector
     * @param radius half-extent in blocks
     * @return the four corners: bottom-left, bottom-right, top-right, top-left
     */
    private static BillboardBasis buildCornersFromBasis(Vector3f right, Vector3f up, float radius) {
        float rx = right.x * radius, ry = right.y * radius, rz = right.z * radius;
        float ux = up.x * radius, uy = up.y * radius, uz = up.z * radius;
        return new BillboardBasis(
            new Vector3f(BLOCK_CENTER - rx - ux, BLOCK_CENTER - ry - uy, BLOCK_CENTER - rz - uz),
            new Vector3f(BLOCK_CENTER + rx - ux, BLOCK_CENTER + ry - uy, BLOCK_CENTER + rz - uz),
            new Vector3f(BLOCK_CENTER + rx + ux, BLOCK_CENTER + ry + uy, BLOCK_CENTER + rz + uz),
            new Vector3f(BLOCK_CENTER - rx + ux, BLOCK_CENTER - ry + uy, BLOCK_CENTER - rz + uz));
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
     * Emits a quad whose corners are pre-computed in block-local space.
     * Uses {@code POSITION_TEX_COLOR} so each vertex carries explicit UV
     * alongside position and color.
     *
     * @param pose  the current pose entry (block corner in camera-relative world coords)
     * @param c     the vertex consumer
     * @param basis the four pre-computed world-space corners
     * @param color ARGB vertex color (progress in R, solid alpha)
     */
    private static void emitWorldSpaceQuad(PoseStack.Pose pose, VertexConsumer c,
            BillboardBasis basis, int color) {
        emitQuadVertex(pose, c, basis.c00(), 0f, 0f, color);
        emitQuadVertex(pose, c, basis.c10(), 1f, 0f, color);
        emitQuadVertex(pose, c, basis.c11(), 1f, 1f, color);
        emitQuadVertex(pose, c, basis.c01(), 0f, 1f, color);
    }

    /**
     * Emits one POSITION_TEX_COLOR vertex.
     *
     * @param pose  the pose entry
     * @param c     the vertex consumer
     * @param pos   block-local position
     * @param u     UV x
     * @param v     UV y
     * @param color ARGB color
     */
    private static void emitQuadVertex(PoseStack.Pose pose, VertexConsumer c,
            Vector3f pos, float u, float v, int color) {
        c.addVertex(pose, pos.x, pos.y, pos.z).setUv(u, v).setColor(color);
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
