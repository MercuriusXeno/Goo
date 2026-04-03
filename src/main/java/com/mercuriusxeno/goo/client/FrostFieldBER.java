package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.FrostFieldBlockEntity;
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
        float stackScale = computeStackScale(state.stacks);
        float breathe = computeBreathe(state.gameTime, state.partialTick);
        float fade = computeFade(state.durationRemaining, state.partialTick);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        float scale = stackScale * (1f + breathe) * targetBoost;

        float coreHalf = CORE_BASE * scale;
        float shellHalf = SHELL_BASE * scale;

        int light = LightCoordsUtil.FULL_BRIGHT;
        int gooColor = GooType.FROST.getColor();
        int baseShellAlpha = state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        int coreAlpha = (int) (0xFF * fade);
        int shellAlpha = (int) (baseShellAlpha * fade);
        int coreColor = (coreAlpha << 24) | 0x00FFFFFF;
        int shellColor = (shellAlpha << 24) | (gooColor & 0x00FFFFFF);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(GooType.FROST);
        float u0 = sprite.getU(0f);
        float u1 = sprite.getU(1f);
        float v0 = sprite.getV(0f);
        float v1 = sprite.getV(1f);

        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);

        // Inner core: frost fluid texture
        float ch = coreHalf;
        GooRenderUtil.UvRect coreUv = new GooRenderUtil.UvRect(u0, v0, u1, v1);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> renderCube(pose, c, light, coreColor,
                        -ch, ch, coreUv));

        // Outer shell: translucent frost-tinted cube
        float sh = shellHalf;
        GooRenderUtil.UvRect shellUv = new GooRenderUtil.UvRect(u0, v0, u1, v1);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> renderCube(pose, c, light, shellColor,
                        -sh, sh, shellUv));

        poseStack.popPose();
    }

    /** Linear scale from 1.0 at stack 1 to MAX_SCALE at MAX_STACKS. */
    private static float computeStackScale(int stacks) {
        if (MAX_STACKS <= 1) return 1f;
        float t = (float) (stacks - 1) / (MAX_STACKS - 1);
        return 1f + t * (MAX_SCALE - 1f);
    }

    /** Gentle sine-wave breathing pulse driven by level game time. */
    private static float computeBreathe(long gameTime, float partialTick) {
        float t = gameTime + partialTick;
        return (float) Math.sin(t * PULSE_SPEED) * PULSE_AMP;
    }

    /** Fades alpha from 1.0 to 0.0 over the final FADE_TICKS. */
    private static float computeFade(int remaining, float partialTick) {
        if (remaining > FADE_TICKS) return 1f;
        float smooth = Math.max(0f, remaining - partialTick);
        return smooth / FADE_TICKS;
    }

    /** Renders all 6 faces of an axis-aligned cube centered at the origin. */
    private static void renderCube(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max,
            GooRenderUtil.UvRect uv) {
        coloredFaceY(pose, c, light, color, min, max, max, min, max, uv, 1f);
        coloredFaceY(pose, c, light, color, min, max, min, min, max, uv, -1f);
        coloredFaceX(pose, c, light, color, max, min, max, min, max, uv, 1f);
        coloredFaceX(pose, c, light, color, min, min, max, min, max, uv, -1f);
        coloredFaceZ(pose, c, light, color, min, max, min, max, max, uv, 1f);
        coloredFaceZ(pose, c, light, color, min, max, min, max, min, uv, -1f);
    }

    private static void coloredFaceY(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x0, float x1, float y,
            float z0, float z1, GooRenderUtil.UvRect uv, float ny) {
        if (ny > 0) {
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z0, uv.u1(), uv.v0(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y, z1, uv.u1(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z1, uv.u0(), uv.v1(), 0f, ny, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y, z0, uv.u0(), uv.v0(), 0f, ny, 0f);
        }
    }

    private static void coloredFaceX(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x, float y0, float y1,
            float z0, float z1, GooRenderUtil.UvRect uv, float nx) {
        if (nx > 0) {
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u1(), uv.v0(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u1(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u0(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u0(), uv.v0(), nx, 0f, 0f);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z0, uv.u1(), uv.v0(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z0, uv.u1(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y0, z1, uv.u0(), uv.v1(), nx, 0f, 0f);
            GooRenderUtil.vertexColored(pose, c, light, color, x, y1, z1, uv.u0(), uv.v0(), nx, 0f, 0f);
        }
    }

    private static void coloredFaceZ(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float x0, float x1, float y0,
            float y1, float z, GooRenderUtil.UvRect uv, float nz) {
        if (nz > 0) {
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
        } else {
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y1, z, uv.u0(), uv.v0(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x1, y0, z, uv.u0(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y0, z, uv.u1(), uv.v1(), 0f, 0f, nz);
            GooRenderUtil.vertexColored(pose, c, light, color, x0, y1, z, uv.u1(), uv.v0(), 0f, 0f, nz);
        }
    }
}
