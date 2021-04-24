package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class GooBulbRenderer extends TileEntityRenderer<GooBulbTile> {
    private static final float FLUID_HORIZONTAL_OFFSET = 0.01626f;
    private static final float FROM_SCALED_VERTICAL = GooBulbTile.FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - (GooBulbTile.FLUID_VERTICAL_MAX * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);

    public GooBulbRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(GooBulbTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfSelf(tile, null);
        cap.ifPresent((c) -> render(tile, partialTicks, tile.crystal(),
                tile.isVerticallyFilled(), tile.verticalFillFluid(),
                tile.verticalFillIntensity(),
                matrixStack, buffer, combinedLightIn));
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.

    public static void render(GooBulbTile tile, float partialTicks, ItemStack crystal,
                              boolean isVerticallyFilled, FluidStack verticalFillFluid, float verticalFillIntensity,
                              MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn) {
        IVertexBuilder normalBrightness = buffer.getBuffer(GooRenderHelper.GOO_CUBE);

        float yOffset = 0;

        // determine where to draw the fluid based on the model
        Vector3f from = FROM_FALLBACK, to = TO_FALLBACK;

        float minY = from.getY();
        float maxY = to.getY();
        float heightScale = maxY - minY;
        float highestToY = minY;

        Object2FloatMap<Fluid> entries = tile.calculateFluidHeights(partialTicks);
        //noinspection OptionalGetWithoutIsPresent
        IFluidHandler fluids = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null).resolve().get();

        // then we can render
        for(int i = 0, e = fluids.getTanks(); i < e; ++i) {
            FluidStack goo = fluids.getFluidInTank(i);
            if (goo.getAmount() <= 0) // quick hacks
                continue;
            float entry = entries.getFloat(goo.getFluid());
            float fromY, toY;
            fromY = minY + yOffset;
            toY = fromY + (entry * heightScale);
            highestToY = toY;
            HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), tile.getPos(), matrixStack, normalBrightness, combinedLightIn, from, fromY, to, toY);
            FluidCuboidHelper.renderScaledFluidCuboid(goo.getFluid(), matrixStack, normalBrightness, combinedLightIn, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ());
            yOffset += (entry * heightScale);
        }

        if (isVerticallyFilled) {
            Vector3f verticalFillFrom = verticalFillFromVector(verticalFillIntensity), verticalFillTo = verticalFillToVector(verticalFillIntensity);
            FluidCuboidHelper.renderScaledFluidCuboid(verticalFillFluid.getFluid(), matrixStack, normalBrightness, combinedLightIn, verticalFillFrom.getX(), highestToY, verticalFillFrom.getZ(), verticalFillTo.getX(), maxY, verticalFillTo.getZ());
        }

        if (crystal.isEmpty()) {
            return;
        }

        matrixStack.push();
        // translate to center

        // note the z here is off a little because the x axis rotation we do tends to push things a little z-positive
        // by about a sixth of the bulb width.
        matrixStack.translate(0.5D, Math.max(0.15625D, Math.min(highestToY / 16f, 0.84375D)), 0.34D);
        matrixStack.rotate(Vector3f.XP.rotationDegrees(90));
        Minecraft.getInstance().getItemRenderer().renderItem(crystal, ItemCameraTransforms.TransformType.GROUND, combinedLightIn, OverlayTexture.NO_OVERLAY, matrixStack, buffer);
        matrixStack.pop();
    }

    // vertical fill graphics scale width to the intensity of the fill which decays after a short time
    private static final float FROM_VERTICAL_FILL_PORT_WIDTH_BASE = 0.125f;
    private static float verticalFillHorizontalOffset(float intensity) {
        return (1f / 2f) - (FROM_VERTICAL_FILL_PORT_WIDTH_BASE * intensity / 2f);
    };
    private static float verticalFillHorizontalFrom(float intensity) {return 16 * verticalFillHorizontalOffset(intensity); }
    private static float verticalFillHorizontalTo(float intensity) { return  16 - verticalFillHorizontalFrom(intensity); }
    private static Vector3f verticalFillFromVector(float intensity) { return new Vector3f(verticalFillHorizontalFrom(intensity), FROM_SCALED_VERTICAL, verticalFillHorizontalFrom(intensity)); }
    private static Vector3f verticalFillToVector(float intensity) { return new Vector3f(verticalFillHorizontalTo(intensity), TO_SCALED_VERTICAL, verticalFillHorizontalTo(intensity)); }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE.get(), GooBulbRenderer::new);
    }
}
