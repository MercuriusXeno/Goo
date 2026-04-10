package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.FrostFieldBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the frost field as a cold glowing orb similar to the chain
 * marker. Two-layer cube: opaque frost fluid core + translucent icy
 * shell. Gentle breathing pulse driven by game time. Scales with
 * stack count. Vanishes instantly on field expiry.
 */
public class FrostFieldBER
        implements BlockEntityRenderer<FrostFieldBlockEntity, FrostFieldRenderState> {

    private static final Identifier BLOCK_ATLAS =
            Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Base inner core half-size (3 pixels). */
    private static final float CORE_BASE = 3f / 16f;

    /** Base outer shell half-size (5 pixels). */
    private static final float SHELL_BASE = 5f / 16f;

    /** Maximum scale at 4 stacks. */
    private static final float MAX_SCALE = 1.8f;

    /** Max stacks for scale interpolation. */
    private static final int MAX_STACKS = 4;

    /** Outer shell alpha at full strength. */
    private static final int SHELL_ALPHA = 0x60;

    /** Outer shell alpha when the player is aiming at the node. */
    private static final int SHELL_ALPHA_TARGETED = 0xC0;

    /** Extra scale bump when targeted. */
    private static final float TARGET_SCALE_BOOST = 1.15f;

    /** Breathing pulse amplitude (fraction of size). */
    private static final float PULSE_AMP = 0.06f;

    /** Breathing pulse speed in radians per tick. */
    private static final float PULSE_SPEED = 0.15f;

    /** Ticks over which the orb fades out before field expiry. */
    private static final int FADE_TICKS = 40;

    /** Full alpha channel value. */
    private static final int FULL_ALPHA = 0xFF;
    /** Bit shift for alpha channel in ARGB. */
    private static final int ALPHA_SHIFT = 24;
    /** Mask for stripping alpha from an ARGB color. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Center offset in block units. */
    private static final float BLOCK_CENTER = 0.5f;

    public FrostFieldBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public FrostFieldRenderState createRenderState() {
        return new FrostFieldRenderState();
    }

    @Override
    public void extractRenderState(FrostFieldBlockEntity be,
            FrostFieldRenderState state, float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.stacks = be.getStacks();
        state.radius = be.getRadius();
        state.gameTime = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
        state.durationRemaining = be.getDurationRemaining();
        state.partialTick = partialTick;
        state.targeted = GooRenderUtil.isBlockTargeted(be.getBlockPos());
    }

    @Override
    public void submit(FrostFieldRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        float scale = computeCompositeScale(state);
        float fade = computeFade(state.durationRemaining, state.partialTick);
        int coreColor = computeCoreColor(fade);
        int shellColor = computeShellColor(state.targeted, fade);
        GooRenderUtil.UvRect uv = buildFrostUv();
        submitBothLayers(nodeCollector, poseStack, scale, coreColor, shellColor, uv);
    }

    /**
     * Combines stack scale, breathing pulse, and target boost into one factor.
     * @param state the frost field render state
     * @return the composite scale factor for the orb
     */
    private static float computeCompositeScale(FrostFieldRenderState state) {
        float stackScale = computeStackScale(state.stacks);
        float breathe = computeBreathe(state.gameTime, state.partialTick);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        return stackScale * (1f + breathe) * targetBoost;
    }

    /**
     * Computes the fully opaque white core color modulated by fade.
     * @param fade the fade-out fraction (1.0 = fully visible, 0.0 = invisible)
     * @return packed ARGB color with white RGB and fade-modulated alpha
     */
    private static int computeCoreColor(float fade) {
        int coreAlpha = (int) (FULL_ALPHA * fade);
        return (coreAlpha << ALPHA_SHIFT) | RGB_MASK;
    }

    /**
     * Computes the translucent frost-tinted shell color modulated by fade.
     * @param targeted whether the player is aiming at this block
     * @param fade the fade-out fraction (1.0 = fully visible, 0.0 = invisible)
     * @return packed ARGB color with frost RGB and fade-modulated alpha
     */
    private static int computeShellColor(boolean targeted, float fade) {
        int baseShellAlpha = targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        int shellAlpha = (int) (baseShellAlpha * fade);
        int gooColor = GooType.FROST.getColor();
        return (shellAlpha << ALPHA_SHIFT) | (gooColor & RGB_MASK);
    }

    /**
     * Builds the UV rectangle from the frost fluid sprite.
     * @return UV rect spanning the full frost fluid sprite
     */
    private static GooRenderUtil.UvRect buildFrostUv() {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(GooType.FROST);
        return new GooRenderUtil.UvRect(
                sprite.getU(0f), sprite.getV(0f), sprite.getU(1f), sprite.getV(1f));
    }

    /**
     * Submits core and shell layers centered at block center.
     * @param nodeCollector the render node collector
     * @param poseStack the pose stack for rendering
     * @param scale the composite size scale factor
     * @param coreColor the packed ARGB color for the inner core
     * @param shellColor the packed ARGB color for the outer shell
     * @param uv the UV texture rectangle for the frost sprite
     */
    private static void submitBothLayers(SubmitNodeCollector nodeCollector,
            PoseStack poseStack, float scale, int coreColor, int shellColor, GooRenderUtil.UvRect uv) {
        poseStack.pushPose();
        poseStack.translate(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER);
        submitLayer(nodeCollector, poseStack, CORE_BASE * scale, coreColor, uv);
        submitLayer(nodeCollector, poseStack, SHELL_BASE * scale, shellColor, uv);
        poseStack.popPose();
    }

    /**
     * Submits a single translucent cuboid layer centered at the origin.
     * @param nodeCollector the render node collector
     * @param poseStack the pose stack for rendering
     * @param half the half-size of the cuboid in block units
     * @param color the packed ARGB color for this layer
     * @param uv the UV texture rectangle for the frost sprite
     */
    private static void submitLayer(SubmitNodeCollector nodeCollector,
            PoseStack poseStack, float half, int color, GooRenderUtil.UvRect uv) {
        int light = LightCoordsUtil.FULL_BRIGHT;
        CuboidBounds box = new CuboidBounds(-half, half, -half, half, -half, half);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> new RenderCtx(pose, c, light).emitBox(color, box, uv));
    }

    /**
     * Linear scale from 1.0 at stack 1 to MAX_SCALE at MAX_STACKS.
     *
     * @param stacks the current stack count
     * @return the computed stackScale
     */
    private static float computeStackScale(int stacks) {
        if (MAX_STACKS <= 1) { return 1f; }
        float t = (float) (stacks - 1) / (MAX_STACKS - 1);
        return 1f + t * (MAX_SCALE - 1f);
    }

    /**
     * Gentle sine-wave breathing pulse driven by level game time.
     *
     * @param gameTime the level game time in ticks
     * @param partialTick the partial tick for interpolation
     * @return the computed breathe
     */
    private static float computeBreathe(long gameTime, float partialTick) {
        float t = gameTime + partialTick;
        return (float) Math.sin(t * PULSE_SPEED) * PULSE_AMP;
    }

    /**
     * Fades alpha from 1.0 to 0.0 over the final FADE_TICKS.
     *
     * @param remaining the ticks remaining
     * @param partialTick the partial tick for interpolation
     * @return the computed fade
     */
    private static float computeFade(int remaining, float partialTick) {
        if (remaining > FADE_TICKS) { return 1f; }
        float smooth = Math.max(0f, remaining - partialTick);
        return smooth / FADE_TICKS;
    }

}
