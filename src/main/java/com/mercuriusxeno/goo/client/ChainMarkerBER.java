package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
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
    /** Negative face direction for normal inversion. */
    private static final float NEG_FACE = -1f;

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
        state.gooType = be.getGooType();
        state.stackCount = be.getStackCount();
        state.maxStacks = be.getMaxStacks();
        state.fuseRemaining = be.getFuseRemaining();
        state.partialTick = partialTick;
        ChainProfile profile = ChainProfile.forType(be.getGooType());
        state.fuseTicks = profile != null ? profile.fuseTicks() : 1;
        state.targeted = GooRenderUtil.isBlockTargeted(be.getBlockPos());
        state.placedFace = be.getPlacedFace();
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        GooType type = state.gooType;
        float stackScale = computeStackScale(state.stackCount, state.maxStacks);
        float implosionScale = computeImplosionScale(
                state.fuseRemaining, state.partialTick);
        float targetBoost = state.targeted ? TARGET_SCALE_BOOST : 1f;
        float scale = stackScale * implosionScale * targetBoost;

        float coreHalf = CORE_BASE * scale;
        float shellHalf = SHELL_BASE * scale;

        int light = LightCoordsUtil.FULL_BRIGHT;
        int gooColor = type.getColor();
        int baseShellAlpha = state.targeted ? SHELL_ALPHA_TARGETED : SHELL_ALPHA;
        int shellColor = (baseShellAlpha << ALPHA_SHIFT) | (gooColor & RGB_MASK);

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU(0f);
        float u1 = sprite.getU(1f);
        float v0 = sprite.getV(0f);
        float v1 = sprite.getV(1f);

        poseStack.pushPose();
        float cx = BLOCK_CENTER;
        float cy = BLOCK_CENTER;
        float cz = BLOCK_CENTER;

        // Rock markers render half-embedded in the face they're stuck to
        if (type == GooType.ROCK) {
            Direction face = state.placedFace;
            cx -= face.getStepX() * BLOCK_CENTER;
            cy -= face.getStepY() * BLOCK_CENTER;
            cz -= face.getStepZ() * BLOCK_CENTER;
        }

        poseStack.translate(cx, cy, cz);

        // Inner core: opaque fluid-textured cube
        float ch = coreHalf;
        GooRenderUtil.UvRect coreUv = new GooRenderUtil.UvRect(u0, v0, u1, v1);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> renderCube(pose, c, light, GooRenderUtil.OPAQUE_WHITE,
                        -ch, ch, coreUv));

        // Outer shell: translucent goo-tinted cube
        float sh = shellHalf;
        GooRenderUtil.UvRect shellUv = new GooRenderUtil.UvRect(u0, v0, u1, v1);
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(BLOCK_ATLAS),
                (pose, c) -> renderCube(pose, c, light, shellColor,
                        -sh, sh, shellUv));

        poseStack.popPose();
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

    /**
     * Renders all 6 faces of an axis-aligned cube centered at the origin.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param min the min
     * @param max the max
     * @param uv the UV texture rectangle
     */
    private static void renderCube(PoseStack.Pose pose, VertexConsumer c,
            int light, int color, float min, float max,
            GooRenderUtil.UvRect uv) {
        coloredFaceY(pose, c, light, color, min, max, max, min, max, uv, 1f);
        coloredFaceY(pose, c, light, color, min, max, min, min, max, uv, NEG_FACE);
        coloredFaceX(pose, c, light, color, max, min, max, min, max, uv, 1f);
        coloredFaceX(pose, c, light, color, min, min, max, min, max, uv, NEG_FACE);
        coloredFaceZ(pose, c, light, color, min, max, min, max, max, uv, 1f);
        coloredFaceZ(pose, c, light, color, min, max, min, max, min, uv, NEG_FACE);
    }

    /**
     * Y-axis face with explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y the Y coordinate
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param ny the Y normal component
     */
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

    /**
     * X-axis face with explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x the X coordinate
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z0 the minimum Z bound
     * @param z1 the maximum Z bound
     * @param uv the UV texture rectangle
     * @param nx the X normal component
     */
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

    /**
     * Z-axis face with explicit ARGB color.
     *
     * @param pose the pose matrix entry
     * @param c the vertex consumer
     * @param light the packed light value
     * @param color the ARGB color value
     * @param x0 the minimum X bound
     * @param x1 the maximum X bound
     * @param y0 the minimum Y bound
     * @param y1 the maximum Y bound
     * @param z the Z coordinate
     * @param uv the UV texture rectangle
     * @param nz the Z normal component
     */
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
