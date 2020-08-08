package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class GooBulbRenderer extends TileEntityRenderer<GooBulbTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.0575f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_VERTICAL_MAX = 0.0005f;
    private static final float FLUID_HORIZONTAL_OFFSET = 0.0005f;
    private static final float FROM_SCALED_VERTICAL = FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - (FLUID_VERTICAL_MAX * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);

    public GooBulbRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    /**
     * Renders a fluid block with offset from the matrices and from x1/y1/z1 to x2/y2/z2 using block model coordinates, so from 0-16
     */
    public static void renderScaledFluidCuboid(FluidStack fluid, MatrixStack matrices, IVertexBuilder renderer, int combinedLight, float x1, float y1, float z1, float x2, float y2, float z2) {
        renderFluidCuboid(fluid, matrices, renderer, combinedLight, x1 / 16, y1 / 16, z1 / 16, x2 / 16, y2 / 16, x2 / 16);
    }

    /**
     * Renders a fluid block with offset from the matrices and from x1/y1/z1 to x2/y2/z2 inside the block local coordinates, so from 0-1
     */
    public static void renderFluidCuboid(FluidStack fluid, MatrixStack matrices, IVertexBuilder renderer, int combinedLight, float x1, float y1, float z1, float x2, float y2, float z2) {
        int color = fluid.getFluid().getAttributes().getColor(fluid);
        renderFluidCuboid(fluid, matrices, renderer, combinedLight, x1, y1, z1, x2, y2, z2, color);
    }

    /**
     * Renders a fluid block with offset from the matrices and from x1/y1/z1 to x2/y2/z2 inside the block local coordinates, so from 0-1
     */
    public static void renderFluidCuboid(FluidStack fluid, MatrixStack matrices, IVertexBuilder renderer, int combinedLight, float x1, float y1, float z1, float x2, float y2, float z2, int color)
    {
        TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getStillTexture(fluid));
        TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getFlowingTexture(fluid));

        matrices.push();
        matrices.translate(x1, y1, z1);
        Matrix4f matrix = matrices.getLast().getMatrix();

        // x/y/z2 - x/y/z1 is because we need the width/height/depth
        putTexturedQuad(renderer, matrix, still, x2 - x1, y2 - y1, z2 - z1, Direction.DOWN, color, combinedLight, false);
        putTexturedQuad(renderer, matrix, flowing, x2 - x1, y2 - y1, z2 - z1, Direction.NORTH, color, combinedLight, true);
        putTexturedQuad(renderer, matrix, flowing, x2 - x1, y2 - y1, z2 - z1, Direction.EAST, color, combinedLight, true);
        putTexturedQuad(renderer, matrix, flowing, x2 - x1, y2 - y1, z2 - z1, Direction.SOUTH, color, combinedLight, true);
        putTexturedQuad(renderer, matrix, flowing, x2 - x1, y2 - y1, z2 - z1, Direction.WEST, color, combinedLight, true);
        putTexturedQuad(renderer, matrix, still, x2 - x1, y2 - y1, z2 - z1, Direction.UP, color, combinedLight, false);

        matrices.pop();
    }

    public static void putTexturedQuad(IVertexBuilder renderer, Matrix4f matrix, TextureAtlasSprite sprite, float w, float h, float d, Direction face,
                                       int color, int brightness, boolean flowing) {
        putTexturedQuad(renderer, matrix, sprite, w, h, d, face, color, brightness, flowing, false);
    }

    public static void putTexturedQuad(IVertexBuilder renderer, Matrix4f matrix, TextureAtlasSprite sprite, float w, float h, float d, Direction face,
                                       int color, int brightness, boolean flowing, boolean flipHorizontally) {
        int l1 = brightness >> 0x10 & 0xFFFF;
        int l2 = brightness & 0xFFFF;

        int a = color >> 24 & 0xFF;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;

        putTexturedQuad(renderer, matrix, sprite, w, h, d, face, r, g, b, a, l1, l2, flowing, flipHorizontally);
    }

    /* Fluid cuboids */
// x and x+w has to be within [0,1], same for y/h and z/d
    public static void putTexturedQuad(IVertexBuilder renderer, Matrix4f matrix, TextureAtlasSprite sprite, float w, float h, float d, Direction face,
                                       int r, int g, int b, int a, int light1, int light2, boolean flowing, boolean flipHorizontally) {
        // safety
        if (sprite == null) {
            return;
        }
        float minU;
        float maxU;
        float minV;
        float maxV;

        double size = 16f;
        if (flowing) {
            size = 8f;
        }

        double xt1 = 0;
        double xt2 = w;
        while (xt2 > 1f) xt2 -= 1f;
        double yt1 = 0;
        double yt2 = h;
        while (yt2 > 1f) yt2 -= 1f;
        double zt1 = 0;
        double zt2 = d;
        while (zt2 > 1f) zt2 -= 1f;

        // flowing stuff should start from the bottom, not from the start
        if (flowing) {
            double tmp = 1d - yt1;
            yt1 = 1d - yt2;
            yt2 = tmp;
        }

        switch (face) {
            case DOWN:
            case UP:
                minU = sprite.getInterpolatedU(xt1 * size);
                maxU = sprite.getInterpolatedU(xt2 * size);
                minV = sprite.getInterpolatedV(zt1 * size);
                maxV = sprite.getInterpolatedV(zt2 * size);
                break;
            case NORTH:
            case SOUTH:
                minU = sprite.getInterpolatedU(xt2 * size);
                maxU = sprite.getInterpolatedU(xt1 * size);
                minV = sprite.getInterpolatedV(yt1 * size);
                maxV = sprite.getInterpolatedV(yt2 * size);
                break;
            case WEST:
            case EAST:
                minU = sprite.getInterpolatedU(zt2 * size);
                maxU = sprite.getInterpolatedU(zt1 * size);
                minV = sprite.getInterpolatedV(yt1 * size);
                maxV = sprite.getInterpolatedV(yt2 * size);
                break;
            default:
                minU = sprite.getMinU();
                maxU = sprite.getMaxU();
                minV = sprite.getMinV();
                maxV = sprite.getMaxV();
        }

        if (flipHorizontally) {
            float tmp = minV;
            minV = maxV;
            maxV = tmp;
        }

        switch (face) {
            case DOWN:
                renderer.pos(matrix, 0, 0, 0).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, 0, 0).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, 0, d).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, 0, d).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
            case UP:
                renderer.pos(matrix, 0, h, 0).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, h, d).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, d).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, 0).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
            case NORTH:
                renderer.pos(matrix, 0, 0, 0).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, h, 0).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, 0).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, 0, 0).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
            case SOUTH:
                renderer.pos(matrix, 0, 0, d).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, 0, d).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, d).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, h, d).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
            case WEST:
                renderer.pos(matrix, 0, 0, 0).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, 0, d).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, h, d).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, 0, h, 0).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
            case EAST:
                renderer.pos(matrix, w, 0, 0).color(r, g, b, a).tex(minU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, 0).color(r, g, b, a).tex(minU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, h, d).color(r, g, b, a).tex(maxU, minV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                renderer.pos(matrix, w, 0, d).color(r, g, b, a).tex(maxU, maxV).lightmap(light1, light2).normal(1, 0, 0).endVertex();
                break;
        }
    }

    @Override
    public void render(GooBulbTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        IVertexBuilder builder = buffer.getBuffer(RenderType.getTranslucent());
        float totalGoo = tile.getTotalGoo();

        // this is the total fill percentage of the container
        float scaledHeight = totalGoo / (float) GooMod.mainConfig.bulbCapacity();
        float  yOffset = 0;

        // determine where to draw the fluid based on the model
        Vector3f from = FROM_FALLBACK, to = TO_FALLBACK;

        float minY = from.getY();
        float maxY = to.getY();
        float highestToY = minY;
        for(FluidStack goo : tile.goop()) {
            // this is the total fill of the goop in the tank of this particular goop, as a percentage
            float percentage = goo.getAmount() / totalGoo;
            float heightScale = percentage * scaledHeight;
            float height = (maxY - minY) * heightScale;
            float fromY, toY;
            fromY = minY + yOffset;
            toY = fromY + height;
            highestToY = toY;
            renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ());
            yOffset += height;
        }

        Vector3f verticalFillFrom = verticalFillFromVector(tile.verticalFillIntensity()), verticalFillTo = verticalFillToVector(tile.verticalFillIntensity());
        if (tile.isVerticallyFilled()) {
            renderScaledFluidCuboid(tile.verticalFillFluid(), matrixStack, builder, combinedLightIn, verticalFillFrom.getX(), highestToY, verticalFillFrom.getZ(), verticalFillTo.getX(), maxY, verticalFillTo.getZ());
        }
    }

    // vertical fill graphics scale width to the intensity of the fill which decays after a short time
    private static final float FROM_VERTICAL_FILL_PORT_WIDTH_BASE = 0.125f;
    private static final float verticalFillHorizontalOffset(float intensity) {
        return (1f / 2f) - (FROM_VERTICAL_FILL_PORT_WIDTH_BASE * intensity / 2f);
    };
    private static final float verticalFillHorizontalFrom(float intensity) {return 16 * verticalFillHorizontalOffset(intensity); }
    private static final float verticalFillHorizontalTo(float intensity) { return  16 - verticalFillHorizontalFrom(intensity); }
    private static final Vector3f verticalFillFromVector(float intensity) { return new Vector3f(verticalFillHorizontalFrom(intensity), FROM_SCALED_VERTICAL, verticalFillHorizontalFrom(intensity)); }
    private static final Vector3f verticalFillToVector(float intensity) { return new Vector3f(verticalFillHorizontalTo(intensity), TO_SCALED_VERTICAL, verticalFillHorizontalTo(intensity)); }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.GOOP_BULB_TILE.get(), GooBulbRenderer::new);
    }
}
