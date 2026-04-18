package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

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

    /** Semi-transparent blue tint for water streams. */
    private static final int WATER_STREAM_COLOR = 0xB03F76E4;

    /** Vanilla water still sprite ID. */
    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");

    /** Vanilla lava still sprite ID. */
    private static final Identifier LAVA_STILL = Identifier.withDefaultNamespace("block/lava_still");

    /** Half divisor for stream width calculation. */
    private static final float WIDTH_HALF = 2f;

    private GooStreamRenderer() {}

    /**
     * Renders a pulsing fluid stream from yTop down to yBottom.
     *
     * @param ctx           the render context
     * @param cx            stream center X in block coords
     * @param cz            stream center Z in block coords
     * @param yTop          top of stream (bottom of upper gasket)
     * @param yBottom       bottom of stream (current fluid surface level)
     * @param type          goo type for sprite/color
     * @param rate          transfer rate in mB/tick (controls width)
     * @param animationTime game time + partial tick for sin wave
     */
    public static void renderStream(RenderContext ctx,
                                    float cx, float cz, float yTop, float yBottom,
                                    GooType type, float rate, float animationTime) {
        if (yTop <= yBottom) { return; }

        float hw = computeHalfWidth(rate, animationTime);
        CuboidBounds box = new CuboidBounds(cx - hw, cx + hw, cz - hw, cz + hw, yBottom, yTop);
        GooRenderUtil.UvRect uv = computeStreamUv(type, hw, yTop - yBottom);
        ctx.emitSides(STREAM_COLOR, box, uv);
    }

    /**
     * Computes the pulsing half-width from transfer rate and animation time.
     * @param rate the transfer rate in mB/tick
     * @param animationTime the game time plus partial tick for sin wave
     * @return the pulsing half-width in block coords
     */
    private static float computeHalfWidth(float rate, float animationTime) {
        float t = Mth.clamp(rate / MAX_RATE, 0f, 1f);
        float baseHW = Mth.lerp(t, MIN_HW, MAX_HW);
        float pulse = 1.0f + PULSE_AMPLITUDE * Mth.sin(animationTime * PULSE_FREQUENCY);
        return baseHW * pulse;
    }

    /**
     * Computes the UV rect for stream side faces from the fluid sprite.
     * @param type the goo type for sprite lookup
     * @param hw the stream half-width in block coords
     * @param height the stream height in block coords
     * @return a UV rect scaled to the stream dimensions
     */
    /**
     * Renders a pulsing vanilla fluid stream from yTop down to yBottom.
     *
     * @param ctx           the render context
     * @param cx            stream center X in block coords
     * @param cz            stream center Z in block coords
     * @param yTop          top of stream
     * @param yBottom       bottom of stream
     * @param fluid         the vanilla fluid
     * @param rate          transfer rate in mB/tick
     * @param animationTime game time + partial tick for sin wave
     */
    public static void renderStream(RenderContext ctx,
            float cx, float cz, float yTop, float yBottom,
            Fluid fluid, float rate, float animationTime) {
        if (yTop <= yBottom) { return; }

        float hw = computeHalfWidth(rate, animationTime);
        CuboidBounds box = new CuboidBounds(cx - hw, cx + hw, cz - hw, cz + hw, yBottom, yTop);
        TextureAtlasSprite sprite = lookupVanillaSprite(fluid);
        int color = isWater(fluid) ? WATER_STREAM_COLOR : STREAM_COLOR;
        GooRenderUtil.UvRect uv = computeSpriteUv(sprite, hw, yTop - yBottom);
        ctx.emitSides(color, box, uv);
    }

    private static boolean isWater(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private static TextureAtlasSprite lookupVanillaSprite(Fluid fluid) {
        Identifier id = isWater(fluid) ? WATER_STILL : LAVA_STILL;
        return Minecraft.getInstance().getAtlasManager()
                .getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(id);
    }

    private static GooRenderUtil.UvRect computeSpriteUv(
            TextureAtlasSprite sprite, float hw, float height) {
        float su0 = sprite.getU0();
        float sv0 = sprite.getV0();
        float sideU1 = su0 + (sprite.getU1() - su0) * (hw * WIDTH_HALF);
        float sideV1 = sv0 + (sprite.getV1() - sv0) * height;
        return new GooRenderUtil.UvRect(su0, sv0, sideU1, sideV1);
    }

    private static GooRenderUtil.UvRect computeStreamUv(GooType type, float hw, float height) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float su0 = sprite.getU0();
        float sv0 = sprite.getV0();
        float sideU1 = su0 + (sprite.getU1() - su0) * (hw * WIDTH_HALF);
        float sideV1 = sv0 + (sprite.getV1() - sv0) * height;
        return new GooRenderUtil.UvRect(su0, sv0, sideU1, sideV1);
    }
}
