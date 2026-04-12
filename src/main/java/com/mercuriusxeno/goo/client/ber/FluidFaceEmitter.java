package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.client.GooRenderUtil;
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
     * Emits the four side quads with UVs scaled to the cuboid's face extents.
     * The V span is derived from the cuboid's Y height rather than a
     * caller-supplied fill parameter so this stays correct even if the
     * container uses a Y epsilon or other yBot offset.
     *
     * @param ctx    the render context
     * @param b      the fluid cuboid
     * @param sprite the fluid atlas sprite
     * @param u0     sprite's left U edge
     * @param v0     sprite's top V edge
     * @param su1    scaled right U edge reused from the top-face computation
     *               ({@code u0 + spriteUWidth * cuboidXWidth}) - avoids one multiply
     */
    private static void emitFluidSides(RenderContext ctx, CuboidBounds b,
                                       TextureAtlasSprite sprite, float u0, float v0, float su1) {
        float sideVSpan = (sprite.getV1() - v0) * (b.yTop() - b.yBot());
        GooRenderUtil.UvRect xUv = new GooRenderUtil.UvRect(u0, v0,
            su1, v0 + sideVSpan);
        GooRenderUtil.UvRect zUv = new GooRenderUtil.UvRect(u0, v0,
            u0 + (sprite.getU1() - u0) * (b.z1() - b.z0()), v0 + sideVSpan);
        emitAllSideFaces(ctx, b, xUv, zUv);
    }

    /**
     * Emits all four cardinal side faces using pre-computed UV rects.
     * Split out from {@link #emitFluidSides} to keep method length and
     * cyclomatic complexity low.
     *
     * @param ctx the render context
     * @param b   the cuboid bounds
     * @param xUv UV rect for the north/south faces (U scaled to X extent)
     * @param zUv UV rect for the west/east faces (U scaled to Z extent)
     */
    private static void emitAllSideFaces(RenderContext ctx, CuboidBounds b,
                                         GooRenderUtil.UvRect xUv, GooRenderUtil.UvRect zUv) {
        ctx.emitFace(b, xUv, Direction.NORTH);
        ctx.emitFace(b, xUv, Direction.SOUTH);
        ctx.emitFace(b, zUv, Direction.WEST);
        ctx.emitFace(b, zUv, Direction.EAST);
    }
}
