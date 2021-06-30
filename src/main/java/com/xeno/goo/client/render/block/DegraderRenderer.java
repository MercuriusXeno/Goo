package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.DegraderTile;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class DegraderRenderer extends TileEntityRenderer<DegraderTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.0625f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_VERTICAL_MAX = 0.0625f;
    private static final float FLUID_HORIZONTAL_OFFSET = 0.0625f;
    private static final float FROM_SCALED_VERTICAL = FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - (FLUID_VERTICAL_MAX * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);

    public DegraderRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(DegraderTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfSelf(tile, null);
        cap.ifPresent((c) -> render(c.getTankCapacity(0), c.getFluidInTank(0), tile.getPos(), matrixStack, buffer, light, overlay));
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;
    public static void render(int bulbCapacity, FluidStack goo, BlockPos pos, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
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
        Model3d fluidModel = getFluidModel(goo, scaledHeight);
        RenderHelper.renderObject(fluidModel, matrixStack, builder, 0xffffffff, light, overlay);
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, light, overlay, fluidModel);
    }

    private static final Map<Fluid, SpriteInfo[]> spriteCache = new HashMap();
    private static Model3d getFluidModel(FluidStack fluid, float scale) {
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
            model.minX = FLUID_HORIZONTAL_OFFSET / 16f;
            model.minY = FLUID_VERTICAL_OFFSET / 16f;
            model.minZ = FLUID_HORIZONTAL_OFFSET / 16f;

            model.maxX = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
            model.maxY = (FLUID_VERTICAL_OFFSET + (FLUID_VERTICAL_MAX * scale)) / 16f;
            model.maxZ = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
        }
        return model;
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.DEGRADER_TILE.get(), DegraderRenderer::new);
    }
}
