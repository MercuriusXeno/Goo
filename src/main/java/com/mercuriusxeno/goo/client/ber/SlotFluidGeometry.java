package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Shared fluid rendering geometry for canister-shaped slots. All three BERs
 * (canister, hub, tap) use identical math with different body/inset constants;
 * this class parameterises those constants via {@link SlotGeometry}.
 */
final class SlotFluidGeometry {

    /**
     * Per-machine constants that define the canister body region.
     *
     * @param hw         half-width of the canister in block coords
     * @param bodyBot    Y coordinate of the body bottom (top of lower gasket)
     * @param bodyTop    Y coordinate of the body top (bottom of upper gasket)
     * @param fluidInset inset from body walls to avoid z-fighting
     */
    record SlotGeometry(float hw, float bodyBot, float bodyTop, float fluidInset) {
    }

    private SlotFluidGeometry() {
    }

    /**
     * Computes XZ-inset fluid cuboid bounds for a slot at the given center.
     *
     * @param g    the slot geometry constants
     * @param cx   the slot center X in block coords
     * @param cz   the slot center Z in block coords
     * @param fill the fluid fill fraction (0.0 to 1.0)
     * @return the fluid cuboid bounds
     */
    static CuboidBounds computeBounds(SlotGeometry g, float cx, float cz, float fill) {
        float x0 = cx - g.hw() + g.fluidInset();
        float x1 = cx + g.hw() - g.fluidInset();
        float z0 = cz - g.hw() + g.fluidInset();
        float z1 = cz + g.hw() - g.fluidInset();
        float yTop = g.bodyBot() + fill * (g.bodyTop() - g.bodyBot());
        return new CuboidBounds(x0, x1, z0, z1, g.bodyBot(), yTop);
    }

    /**
     * Renders the horizontal top-face quad of a fluid surface with UVs scaled to the cuboid footprint.
     *
     * @param ctx    the render context
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     */
    static void renderFluidTop(RenderContext ctx, CuboidBounds b, TextureAtlasSprite sprite) {
        renderFluidTop(ctx, b, sprite, GooRenderUtil.OPAQUE_WHITE);
    }

    /**
     * Renders the horizontal top-face quad with an explicit tint color.
     *
     * @param ctx    the render context
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     * @param color  the ARGB tint color
     */
    static void renderFluidTop(RenderContext ctx, CuboidBounds b,
            TextureAtlasSprite sprite, int color) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sv1 = v0 + (sprite.getV1() - v0) * (b.z1() - b.z0());
        ctx.liquidSurface(color, b, new GooRenderUtil.UvRect(u0, v0, su1, sv1));
    }

    /**
     * Renders the four side faces of a fluid column from body bottom to fill height.
     *
     * @param ctx    the render context
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     * @param fill   the fluid fill fraction (0.0 to 1.0)
     * @param g      the slot geometry constants for V-span computation
     */
    static void renderFluidSides(RenderContext ctx, CuboidBounds b,
                                 TextureAtlasSprite sprite, float fill, SlotGeometry g) {
        renderFluidSides(ctx, b, sprite, fill, g, GooRenderUtil.OPAQUE_WHITE);
    }

    /**
     * Renders the four side faces with an explicit tint color.
     *
     * @param ctx    the render context
     * @param b      the precomputed fluid cuboid bounds
     * @param sprite the fluid texture atlas sprite
     * @param fill   the fluid fill fraction (0.0 to 1.0)
     * @param g      the slot geometry constants for V-span computation
     * @param color  the ARGB tint color
     */
    static void renderFluidSides(RenderContext ctx, CuboidBounds b,
            TextureAtlasSprite sprite, float fill, SlotGeometry g, int color) {
        float sideVSpan = computeSideVSpan(sprite, fill, g);
        GooRenderUtil.UvRect xUv = sideUvRect(sprite, b.x1() - b.x0(), sideVSpan);
        GooRenderUtil.UvRect zUv = sideUvRect(sprite, b.z1() - b.z0(), sideVSpan);
        ctx.emitFace(color, b, xUv, Direction.NORTH);
        ctx.emitFace(color, b, xUv, Direction.SOUTH);
        ctx.emitFace(color, b, zUv, Direction.WEST);
        ctx.emitFace(color, b, zUv, Direction.EAST);
    }

    /**
     * Computes the V-axis texture span scaled by fluid fill height.
     *
     * @param sprite the fluid texture atlas sprite
     * @param fill   the fluid fill fraction (0.0 to 1.0)
     * @param g      the slot geometry constants
     * @return the V-axis texture span proportional to fill height
     */
    static float computeSideVSpan(TextureAtlasSprite sprite, float fill, SlotGeometry g) {
        float fillHeight = fill * (g.bodyTop() - g.bodyBot());
        return (sprite.getV1() - sprite.getV0()) * fillHeight;
    }

    /**
     * Builds a UV rect for a side face scaled to the given width and V span.
     *
     * @param sprite the fluid texture atlas sprite
     * @param width  the face width in block coords for U scaling
     * @param vSpan  the pre-computed V-axis span
     * @return a UV rect scaled to the face dimensions
     */
    static GooRenderUtil.UvRect sideUvRect(TextureAtlasSprite sprite, float width, float vSpan) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        return new GooRenderUtil.UvRect(u0, v0, u0 + (sprite.getU1() - u0) * width, v0 + vSpan);
    }
}
