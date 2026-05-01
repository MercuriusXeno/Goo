package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.ber.CuboidBounds;
import com.mercuriusxeno.goo.client.ber.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Shared fluid-face emission for any container whose fluid volume is
 * represented as a {@link CuboidBounds}. Emits the translucent liquid top
 * quad plus the four vertical side quads, with UVs scaled proportionally
 * to the cuboid's XYZ extent so the sprite is not squished.
 *
 * <p>The fluid cuboid's Y range is expected to already encode the fill
 * level - callers construct it as {@code (yFloor, yFloor + fillHeight)}.
 * This helper reads the Y span from the cuboid directly, so no separate
 * {@code fill} parameter is needed.
 */
public final class FluidFaceEmitter {

    private FluidFaceEmitter() {}

    /**
     * Emits the top fluid surface quad and the four side quads for the
     * given cuboid. The sprite is selected from the goo type via
     * {@link GooRenderUtil#lookupFluidSprite(GooType)}.
     *
     * @param ctx  the render context
     * @param b    the fluid cuboid - its Y range must already encode the fill height
     * @param type the goo type, used for sprite lookup
     */
    public static void emitFluidFaces(RenderContext ctx, CuboidBounds b, GooType type) {
        TextureAtlasSprite sprite = GooRenderUtil.lookupFluidSprite(type);
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sv1 = v0 + (sprite.getV1() - v0) * (b.z1() - b.z0());
        ctx.liquidSurface(GooRenderUtil.OPAQUE_WHITE, b,
            new GooRenderUtil.UvRect(u0, v0, su1, sv1));
        emitFluidSides(ctx, b, sprite, u0, v0, su1);
    }

    /**
     * Emits fluid faces for a vanilla fluid with an explicit sprite and tint color.
     *
     * @param ctx    the render context
     * @param b      the fluid cuboid
     * @param sprite the fluid texture sprite
     * @param tint   the ARGB tint color
     */
    public static void emitFluidFaces(RenderContext ctx, CuboidBounds b,
            TextureAtlasSprite sprite, int tint) {
        float u0 = sprite.getU0();
        float v0 = sprite.getV0();
        float su1 = u0 + (sprite.getU1() - u0) * (b.x1() - b.x0());
        float sv1 = v0 + (sprite.getV1() - v0) * (b.z1() - b.z0());
        ctx.liquidSurface(tint, b,
            new GooRenderUtil.UvRect(u0, v0, su1, sv1));
        emitFluidSides(ctx, b, sprite, u0, v0, su1, tint);
    }

    /**
     * Emits the four side quads with UVs scaled to the cuboid's face extents.
     * Uses opaque white (no tint) for goo fluids.
     *
     * @param ctx    the render context
     * @param b      the fluid cuboid
     * @param sprite the fluid atlas sprite
     * @param u0     sprite's left U edge
     * @param v0     sprite's top V edge
     * @param su1    scaled right U edge
     */
    private static void emitFluidSides(RenderContext ctx, CuboidBounds b,
                                       TextureAtlasSprite sprite, float u0, float v0, float su1) {
        emitFluidSides(ctx, b, sprite, u0, v0, su1, GooRenderUtil.OPAQUE_WHITE);
    }

    /**
     * Emits the four side quads with UVs scaled to the cuboid's face extents
     * and an explicit tint color.
     *
     * @param ctx    the render context
     * @param b      the fluid cuboid
     * @param sprite the fluid atlas sprite
     * @param u0     sprite's left U edge
     * @param v0     sprite's top V edge
     * @param su1    scaled right U edge
     * @param tint   the ARGB tint color
     */
    private static void emitFluidSides(RenderContext ctx, CuboidBounds b,
                                       TextureAtlasSprite sprite, float u0, float v0,
                                       float su1, int tint) {
        float sideVSpan = (sprite.getV1() - v0) * (b.yTop() - b.yBot());
        GooRenderUtil.UvRect xUv = new GooRenderUtil.UvRect(u0, v0,
            su1, v0 + sideVSpan);
        GooRenderUtil.UvRect zUv = new GooRenderUtil.UvRect(u0, v0,
            u0 + (sprite.getU1() - u0) * (b.z1() - b.z0()), v0 + sideVSpan);
        emitAllSideFaces(ctx, b, xUv, zUv, tint);
    }

    /**
     * Emits all four cardinal side faces with the given tint color.
     *
     * @param ctx  the render context
     * @param b    the cuboid bounds
     * @param xUv  UV rect for the north/south faces
     * @param zUv  UV rect for the west/east faces
     * @param tint the ARGB tint color
     */
    private static void emitAllSideFaces(RenderContext ctx, CuboidBounds b,
                                         GooRenderUtil.UvRect xUv, GooRenderUtil.UvRect zUv,
                                         int tint) {
        ctx.emitFace(tint, b, xUv, Direction.NORTH);
        ctx.emitFace(tint, b, xUv, Direction.SOUTH);
        ctx.emitFace(tint, b, zUv, Direction.WEST);
        ctx.emitFace(tint, b, zUv, Direction.EAST);
    }
}
