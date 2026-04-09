package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * Renders a pulsing fluid stream cuboid from a gasket entry point
 * down to the current fluid surface. Shared by canister, hub, and vat BERs.
 */
public final class GooStreamRenderer {
    /** Minimum stream half-width at 1 mB/tick (tiny trickle). */
    private static final float MIN_HW = 0.125f / 16f;

    /** Maximum stream half-width at 3700+ mB/tick (half canister width). */
    private static final float MAX_HW = 1.0f / 16f;

    /** Rate at which max width is reached, in mB/tick. */
    private static final float MAX_RATE = 3700f;

    /** Sin wave frequency for width pulsing (~2-second period). */
    private static final float PULSE_FREQUENCY = 0.3f;

    /** Sin wave amplitude (±10% width variation). */
    private static final float PULSE_AMPLITUDE = 0.1f;

    /** Semi-transparent ARGB for the stream. */
    private static final int STREAM_COLOR = 0xB0FFFFFF;

    /** Half divisor for stream width calculation. */
    private static final float WIDTH_HALF = 2f;
    /** Negative normal direction for face rendering. */
    private static final int NEG_NORMAL = -1;

    private GooStreamRenderer() {}

    /**
     * Renders a pulsing fluid stream from yTop down to yBottom.
     *
     * @param pose          current pose matrix
     * @param c             vertex consumer (translucent render type)
     * @param light         packed light coords
     * @param cx            stream center X in block coords
     * @param cz            stream center Z in block coords
     * @param yTop          top of stream (bottom of upper gasket)
     * @param yBottom       bottom of stream (current fluid surface level)
     * @param type          goo type for sprite/color
     * @param rate          transfer rate in mB/tick (controls width)
     * @param animationTime game time + partial tick for sin wave
     */
    public static void renderStream(PoseStack.Pose pose, VertexConsumer c, int light,
            float cx, float cz, float yTop, float yBottom,
            GooType type, float rate, float animationTime) {
        if (yTop <= yBottom) { return; }

        float t = Mth.clamp(rate / MAX_RATE, 0f, 1f);
        float baseHW = Mth.lerp(t, MIN_HW, MAX_HW);
        float pulse = 1.0f + PULSE_AMPLITUDE * Mth.sin(animationTime * PULSE_FREQUENCY);
        float hw = baseHW * pulse;

        float x0 = cx - hw;
        float x1 = cx + hw;
        float z0 = cz - hw;
        float z1 = cz + hw;

        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float su0 = sprite.getU0();
        float su1 = sprite.getU1();
        float sv0 = sprite.getV0();
        float sv1 = sprite.getV1();

        // Keep 1:1 texel ratio: U spans the stream width, V spans the
        // same proportion of the sprite per block-unit of height so the
        // texture looks the same density as the normal fluid surface.
        float widthBlocks = hw * WIDTH_HALF;
        float heightBlocks = yTop - yBottom;
        float fullURange = su1 - su0;
        float fullVRange = sv1 - sv0;
        float sideU1 = su0 + fullURange * widthBlocks;
        float sideV1 = sv0 + fullVRange * heightBlocks;

        // 4 side faces with translucent tint
        renderColoredFaceNorth(pose, c, light, x0, yBottom, z0, x1, yTop, su0, sideU1, sv0, sideV1);
        renderColoredFaceSouth(pose, c, light, x0, yBottom, z1, x1, yTop, su0, sideU1, sv0, sideV1);
        renderColoredFaceWest(pose, c, light, x0, yBottom, z0, yTop, z1, su0, sideU1, sv0, sideV1);
        renderColoredFaceEast(pose, c, light, x1, yBottom, z0, yTop, z1, su0, sideU1, sv0, sideV1);
    }

    // --- Colored face helpers (same winding as CanisterGeometry but with STREAM_COLOR) ---

    private static void renderColoredFaceNorth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x1, y1, z, u0, v0, 0, 0, NEG_NORMAL);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x1, y0, z, u0, v1, 0, 0, NEG_NORMAL);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x0, y0, z, u1, v1, 0, 0, NEG_NORMAL);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x0, y1, z, u1, v0, 0, 0, NEG_NORMAL);
    }

    private static void renderColoredFaceSouth(PoseStack.Pose pose, VertexConsumer c, int light,
            float x0, float y0, float z, float x1, float y1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x0, y1, z, u0, v0, 0, 0, 1);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x0, y0, z, u0, v1, 0, 0, 1);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x1, y0, z, u1, v1, 0, 0, 1);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x1, y1, z, u1, v0, 0, 0, 1);
    }

    private static void renderColoredFaceWest(PoseStack.Pose pose, VertexConsumer c, int light,
            float x, float y0, float z0, float y1, float z1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y1, z0, u1, v0, NEG_NORMAL, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y0, z0, u1, v1, NEG_NORMAL, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y0, z1, u0, v1, NEG_NORMAL, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y1, z1, u0, v0, NEG_NORMAL, 0, 0);
    }

    private static void renderColoredFaceEast(PoseStack.Pose pose, VertexConsumer c, int light,
            float x, float y0, float z0, float y1, float z1,
            float u0, float u1, float v0, float v1) {
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y1, z1, u1, v0, 1, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y0, z1, u1, v1, 1, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y0, z0, u0, v1, 1, 0, 0);
        GooRenderUtil.vertexColored(pose, c, light, STREAM_COLOR, x, y1, z0, u0, v0, 1, 0, 0);
    }
}
