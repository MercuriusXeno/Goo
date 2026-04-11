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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

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
    /** Solid alpha (0xFF) for the blackhole sphere vertices. */
    private static final int BLACKHOLE_ALPHA = 0xFF;
    /** Maximum byte value for a progress-in-R channel mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;
    /** Bit shift for the red channel in an ARGB color. */
    private static final int RED_CHANNEL_SHIFT = 16;
    /** Bit shift for the green channel in an ARGB color. */
    private static final int GREEN_CHANNEL_SHIFT = 8;
    /** Half-extent of the render bounding box around a chain marker, in blocks. Must exceed the maximum implosion radius (nether max = 9). */
    private static final double RENDER_BOX_HALF_EXTENT = 12.0;
    /** Number of latitude bands on the sphere mesh (excluding poles). */
    private static final int SPHERE_LAT_SEGMENTS = 12;
    /** Number of longitude segments around the sphere mesh. */
    private static final int SPHERE_LON_SEGMENTS = 24;
    /** Vertices per quad in the sphere mesh (matches VertexFormat.Mode.QUADS). */
    private static final int VERTICES_PER_QUAD = 4;
    /** Cycle length in ticks for the swirl animation time. */
    private static final int ANIMATION_CYCLE_TICKS = 64;
    /** Latitude offset subtracted from {@code lat / latSegments} to center phi on zero. */
    private static final double LATITUDE_HALF_OFFSET = 0.5;
    /** Full circle in radians. */
    private static final double TWO_PI = 2.0 * Math.PI;

    /** One vertex of the pre-generated unit sphere mesh; coordinates double as the unit normal. */
    private record SphereVertex(float nx, float ny, float nz) {}

    /** Pre-generated unit sphere mesh. Every 4 consecutive elements form one quad. */
    private static final List<SphereVertex> SPHERE_MESH = buildSphereMesh();

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
        state.animationTime = computeAnimationTime(be);
    }

    /** Derives a deterministic [0, 1) animation phase from the BE's level
     * game time, cycling every {@link #ANIMATION_CYCLE_TICKS} ticks.
     *
     * @param be the chain marker block entity
     * @return the animation phase for the shader
     */
    private static float computeAnimationTime(ChainMarkerBlockEntity be) {
        var level = be.getLevel();
        if (level == null) { return 0f; }
        long tick = level.getGameTime() % ANIMATION_CYCLE_TICKS;
        return (float) tick / ANIMATION_CYCLE_TICKS;
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
        if (isBlackholePhase(state.phase)) {
            submitBlackholeSphere(state, poseStack, nodeCollector);
            return;
        }
        submitFuseOrb(state, poseStack, nodeCollector);
    }

    /** True if the given phase renders the black-hole sphere.
     *
     * @param phase the phase to check
     * @return true for EXPAND, HOLD, CONTRACT; false otherwise
     */
    private static boolean isBlackholePhase(ChainMarkerBlockEntity.Phase phase) {
        return phase == ChainMarkerBlockEntity.Phase.EXPAND
            || phase == ChainMarkerBlockEntity.Phase.HOLD
            || phase == ChainMarkerBlockEntity.Phase.CONTRACT;
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
     * Emits the goal-013 black-hole as a real 3D UV sphere mesh during
     * EXPAND / HOLD / CONTRACT. Depth writes are on, so every triangle of
     * the sphere occludes what is behind it in the depth buffer — the
     * destroyed volume is actually hidden inside the sphere.
     *
     * <p>Vertices carry {@code POSITION_COLOR_NORMAL}. Position is the
     * block-local world position of the scaled sphere surface. Normal is
     * the unit-sphere direction (equal to the local vertex offset since
     * the sphere is at the origin). Color.r carries {@code visibleScale};
     * Color.g carries a deterministic animation phase derived from the
     * level game time so the swirl rotates without depending on the
     * {@code GameTime} uniform plumbing.
     *
     * @param state         the render state snapshot
     * @param poseStack     the pose stack for rendering (already at block corner)
     * @param nodeCollector the render node collector
     */
    private static void submitBlackholeSphere(ChainMarkerRenderState state,
            PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        float fullRadius = state.implodeRadius + OCCLUSION_MARGIN;
        float visibleRadius = Math.max(BLACKHOLE_MIN_RADIUS, fullRadius * state.visibleScale);
        int color = packBlackholeColor(state.visibleScale, state.animationTime);

        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitSphereMesh(pose, c, visibleRadius, color));
    }

    /**
     * Packs {@code visibleScale} into the ARGB R channel and
     * {@code animationTime} into the G channel, so the fragment shader
     * can read both as normalized floats in the vertex Color attribute.
     *
     * @param scale         implosion visible scale in [0, 1]
     * @param animationTime swirl animation phase in [0, 1] (cycling)
     * @return the packed ARGB color
     */
    private static int packBlackholeColor(float scale, float animationTime) {
        int scaleByte = Math.round(clamp01(scale) * PROGRESS_BYTE_MAX);
        int animByte = Math.round(clamp01(animationTime) * PROGRESS_BYTE_MAX);
        return (BLACKHOLE_ALPHA << ALPHA_SHIFT)
            | (scaleByte << RED_CHANNEL_SHIFT)
            | (animByte << GREEN_CHANNEL_SHIFT);
    }

    /** Clamps {@code v} to {@code [0, 1]}.
     *
     * @param v the value to clamp
     * @return the clamped value
     */
    private static float clamp01(float v) {
        return Math.min(1f, Math.max(0f, v));
    }

    /**
     * Emits the pre-generated unit sphere mesh with each vertex scaled to
     * {@code radius} and translated to the block center. Iterates the
     * vertex record list directly; consecutive four-tuples form quads.
     *
     * @param pose   the current pose entry (block corner in camera-relative world coords)
     * @param c      the vertex consumer
     * @param radius world-space sphere radius in blocks
     * @param color  ARGB vertex color (visibleScale in R, animationTime in G)
     */
    private static void emitSphereMesh(PoseStack.Pose pose, VertexConsumer c,
            float radius, int color) {
        for (SphereVertex v : SPHERE_MESH) {
            c.addVertex(pose,
                    BLOCK_CENTER + v.nx() * radius,
                    BLOCK_CENTER + v.ny() * radius,
                    BLOCK_CENTER + v.nz() * radius)
                .setColor(color)
                .setNormal(pose, v.nx(), v.ny(), v.nz());
        }
    }

    /**
     * Builds a UV sphere mesh as a list of {@link SphereVertex}. Every
     * four consecutive entries form one quad matching
     * {@link VertexFormat.Mode#QUADS}. Called once at class init.
     *
     * @return the unit sphere vertex list
     */
    private static List<SphereVertex> buildSphereMesh() {
        int capacity = SPHERE_LAT_SEGMENTS * SPHERE_LON_SEGMENTS * VERTICES_PER_QUAD;
        List<SphereVertex> out = new ArrayList<>(capacity);
        for (int lat = 0; lat < SPHERE_LAT_SEGMENTS; lat++) {
            double phi0 = Math.PI * ((double) lat / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            double phi1 = Math.PI * ((double) (lat + 1) / SPHERE_LAT_SEGMENTS - LATITUDE_HALF_OFFSET);
            for (int lon = 0; lon < SPHERE_LON_SEGMENTS; lon++) {
                double theta0 = TWO_PI * lon / SPHERE_LON_SEGMENTS;
                double theta1 = TWO_PI * (lon + 1) / SPHERE_LON_SEGMENTS;
                out.add(sphereVertex(phi0, theta0));
                out.add(sphereVertex(phi1, theta0));
                out.add(sphereVertex(phi1, theta1));
                out.add(sphereVertex(phi0, theta1));
            }
        }
        return out;
    }

    /** Builds one unit-sphere vertex at spherical coordinates (phi, theta).
     *
     * @param phi   latitude in radians, {@code [-PI/2, PI/2]}
     * @param theta longitude in radians, {@code [0, 2*PI]}
     * @return the unit-sphere vertex
     */
    private static SphereVertex sphereVertex(double phi, double theta) {
        double cosPhi = Math.cos(phi);
        return new SphereVertex(
            (float) (cosPhi * Math.cos(theta)),
            (float) Math.sin(phi),
            (float) (cosPhi * Math.sin(theta)));
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
