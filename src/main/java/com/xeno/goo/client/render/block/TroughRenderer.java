package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.TroughTile;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class TroughRenderer extends TileEntityRenderer<TroughTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.0626f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_VERTICAL_MAX = 0.24f;
    private static final float FLUID_HORIZONTAL_OFFSET = 0.1251f;
    private static final float FROM_SCALED_VERTICAL = FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - ((1f - FLUID_VERTICAL_MAX) * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);
    private static final float TO_SCALED_VERTICAL_FIXTURE = 0.5625f * 16;

    public TroughRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(TroughTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfSelf(tile, null);
        cap.ifPresent((c) -> render(c.getTankCapacity(0), c.getFluidInTank(0), tile.getPos(),
                tile.hasWorld() ? tile.facing() :  Direction.NORTH, tile.isVerticallyFilled(), tile.verticalFillFluid(),
                tile.verticalFillIntensity(),
                matrixStack, buffer, light, overlay));
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;
    public static void render(int bulbCapacity, FluidStack goo, BlockPos pos, Direction d, boolean isVerticallyFilled,
            FluidStack verticalFillFluid, float verticalFillIntensity, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        float totalGoo = goo.getAmount();
        if (goo.isEmpty()) {
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(RenderHelper.GOO_CUBE);
        float minHeight = ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;

        // this is the total fill percentage of the container
        float scaledHeight = Math.max(minHeight, totalGoo / (float)bulbCapacity);
        float yOffset = 0;

        // determine where to draw the fluid based on the model
        Vector3f from = FROM_FALLBACK, to = TO_FALLBACK;

        float minY = from.getY();
        float maxY = to.getY();
        // this is the total fill of the goo in the tank of this particular goo, as a percentage
        float percentage = goo.getAmount() / totalGoo;
        float heightScale = percentage * scaledHeight;
        float height = (maxY - minY) * heightScale;
        float fromY, toY;
        fromY = minY + yOffset;
        toY = fromY + height;
        Model3d fluidModel = getFluidModel(goo, fromY, toY);
        RenderHelper.renderObject(fluidModel, matrixStack, builder, GooFluid.UNCOLORED_WITH_PARTIAL_TRANSPARENCY, light, overlay);
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, light, overlay, fluidModel);

        if (isVerticallyFilled) {
        }
        if (isVerticallyFilled) {
            Vector3f vertFrom = verticalFillFromVector(verticalFillIntensity, d),
                    vertTo = verticalFillToVector(verticalFillIntensity, d);
            float streamWidth = FROM_VERTICAL_FILL_PORT_WIDTH_BASE * verticalFillIntensity;
            float vertToY = TO_SCALED_VERTICAL_FIXTURE - (16f * (FROM_VERTICAL_FILL_PORT_WIDTH_BASE - streamWidth));
            Model3d fillModel = getVerticalFillModel(verticalFillFluid, vertFrom.getX(), toY, vertFrom.getZ(), vertTo.getX(), vertToY, vertTo.getZ());
            RenderHelper.renderObject(fillModel, matrixStack, builder, GooFluid.UNCOLORED_WITH_PARTIAL_TRANSPARENCY, light, overlay);
        }
    }

    private static Model3d getVerticalFillModel(FluidStack fluid, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        Model3d model = new Model3d();
        if (spriteCache.containsKey(fluid.getFluid())) {
            SpriteInfo[] cache = spriteCache.get(fluid.getFluid());
            model.setTextures(cache[0], cache[1], cache[2], cache[3], cache[4], cache[5]);
        } else {
            SpriteInfo[] sprites = new SpriteInfo[] {
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.STILL), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.STILL), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16)
            };
            spriteCache.put(fluid.getFluid(), sprites);
            model.setTextures(sprites[0], sprites[1], sprites[2], sprites[3], sprites[4], sprites[5]);
        }
        if (fluid.getFluid().getAttributes().getStillTexture(fluid) != null) {
            model.minX = minX / 16f;
            model.minY = minY / 16f;
            model.minZ = minZ / 16f;

            model.maxX = maxX / 16f;
            model.maxY = maxY / 16f;
            model.maxZ = maxZ / 16f;
        }
        return model;
    }

    private static final Map<Fluid, SpriteInfo[]> spriteCache = new HashMap();
    private static Model3d getFluidModel(FluidStack fluid, float fromY, float toY) {
        Model3d model = new Model3d();
        if (spriteCache.containsKey(fluid.getFluid())) {
            SpriteInfo[] cache = spriteCache.get(fluid.getFluid());
            model.setTextures(cache[0], cache[1], cache[2], cache[3], cache[4], cache[5]);
        } else {
            SpriteInfo[] sprites = new SpriteInfo[] {
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.STILL), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.STILL), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16),
                    new SpriteInfo(RenderHelper.getFluidTexture(fluid, FluidType.FLOWING), 16)
            };
            spriteCache.put(fluid.getFluid(), sprites);
            model.setTextures(sprites[0], sprites[1], sprites[2], sprites[3], sprites[4], sprites[5]);
        }
        if (fluid.getFluid().getAttributes().getStillTexture(fluid) != null) {
            model.minX = FLUID_HORIZONTAL_OFFSET;
            model.minY = fromY / 16f;
            model.minZ = FLUID_HORIZONTAL_OFFSET;

            model.maxX = (1f - FLUID_HORIZONTAL_OFFSET);
            model.maxY = toY / 16f;
            model.maxZ = (1f - FLUID_HORIZONTAL_OFFSET);
        }
        return model;
    }

    // vertical fill graphics scale width to the intensity of the fill which decays after a short time
    private static final float FROM_VERTICAL_FILL_PORT_WIDTH_BASE = 0.125f;
    private static float verticalFillHorizontalOffsetZ(float intensity, Direction d, boolean start) {
        float streamWidth = FROM_VERTICAL_FILL_PORT_WIDTH_BASE * intensity;
        float offset = 0.251f;
        float streamOffset = start ? -streamWidth / 2f : streamWidth / 2f;
        switch (d) {
            case SOUTH:
                return (1f - offset) + streamOffset * 2f;
            case NORTH:
                return offset + streamOffset * 2f;
        }
        return 0.5f + streamOffset;
    };
    private static float verticalFillHorizontalOffsetX(float intensity, Direction d, boolean start) {
        float streamWidth = FROM_VERTICAL_FILL_PORT_WIDTH_BASE * intensity;
        float offset = 0.251f;
        float streamOffset = start ? -streamWidth / 2f : streamWidth / 2f;
        switch (d) {
            case EAST:
                return (1f - offset) + streamOffset;
            case WEST:
                return offset + streamOffset;
        }
        return 0.5f + streamOffset;
    };

    private static float verticalFillFromZ(float intensity, Direction direction) {
        return 16 * verticalFillHorizontalOffsetZ(intensity, direction, true); }
    private static float verticalFillToZ(float intensity, Direction direction) {
        return  16 * verticalFillHorizontalOffsetZ(intensity, direction, false); }

    private static float verticalFillFromX(float intensity, Direction direction) {
        return 16 * verticalFillHorizontalOffsetX(intensity, direction, true); }
    private static float verticalFillToX(float intensity, Direction direction) {
        return  16 * verticalFillHorizontalOffsetX(intensity, direction, false); }

    private static Vector3f verticalFillFromVector(float intensity, Direction direction) {
        return new Vector3f(verticalFillFromX(intensity, direction), FROM_SCALED_VERTICAL, verticalFillFromZ(intensity, direction)); }
    private static Vector3f verticalFillToVector(float intensity, Direction direction) {
        return new Vector3f(verticalFillToX(intensity, direction), TO_SCALED_VERTICAL_FIXTURE, verticalFillToZ(intensity, direction)); }


    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.TROUGH_TILE.get(), TroughRenderer::new);
    }
}
