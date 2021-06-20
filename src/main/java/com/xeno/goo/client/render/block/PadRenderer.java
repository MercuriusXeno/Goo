package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.PadTile;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class PadRenderer extends TileEntityRenderer<PadTile> {
    // not triggered
    private static final float FLUID_VERTICAL_OFFSET = 0.015626f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_VERTICAL_MAX = 0.0775f;
    private static final float FLUID_HORIZONTAL_OFFSET = 0.1876f;
    private static final float FROM_SCALED_VERTICAL = FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - ((1f - FLUID_VERTICAL_MAX) * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);

    // triggered
    private static final float FLUID_VERTICAL_MAX_TRIGGERED = 0.0467f;
    private static final float TO_SCALED_VERTICAL_TRIGGERED = 16 - ((1f - FLUID_VERTICAL_MAX_TRIGGERED) * 16);
    private static final Vector3f FROM_FALLBACK_TRIGGERED = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK_TRIGGERED = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL_TRIGGERED, TO_SCALED_HORIZONTAL);

    public PadRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(PadTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfSelf(tile, null);
        cap.ifPresent((c) -> {
            boolean isTriggered = false;
            if (tile.hasWorld()) {
                if (tile.getWorld().getBlockState(tile.getPos()).get(BlockStateProperties.TRIGGERED)) {
                    isTriggered = true;
                }
            }
            render(c.getTankCapacity(0), c.getFluidInTank(0), tile.getPos(), isTriggered, matrixStack, buffer, combinedLightIn);
        });
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;
    public static void render(int bulbCapacity, FluidStack goo, BlockPos pos, boolean isTriggered, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn) {
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
        Vector3f from = isTriggered ? FROM_FALLBACK_TRIGGERED : FROM_FALLBACK;
        Vector3f to = isTriggered ? TO_FALLBACK_TRIGGERED : TO_FALLBACK;

        float minY = from.getY();
        float maxY = to.getY();
        // this is the total fill of the goo in the tank of this particular goo, as a percentage
        float percentage = goo.getAmount() / totalGoo;
        float heightScale = percentage * scaledHeight;
        float height = (maxY - minY) * heightScale;
        float fromY, toY;
        fromY = minY + yOffset;
        toY = fromY + height;
        FluidCuboidHelper.renderScaledFluidCuboid(goo.getFluid(), matrixStack, builder, combinedLightIn, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ());
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, combinedLightIn, from, fromY, to, toY);
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.PAD_TILE.get(), PadRenderer::new);
    }
}
