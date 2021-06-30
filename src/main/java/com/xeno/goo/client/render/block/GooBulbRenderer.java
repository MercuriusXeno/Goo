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
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
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

import java.util.HashMap;
import java.util.Map;

public class GooBulbRenderer extends TileEntityRenderer<GooBulbTile> {
    private static final float FLUID_HORIZONTAL_OFFSET = 0.11f;
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
                matrixStack, buffer, combinedLightIn, combinedOverlayIn));
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.

    public static void render(GooBulbTile tile, float partialTicks, ItemStack crystal,
                              boolean isVerticallyFilled, FluidStack verticalFillFluid, float verticalFillIntensity,
                              MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        IVertexBuilder builder = buffer.getBuffer(Atlases.getTranslucentCullBlockType());

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
            Model3d model = getFluidModel(goo, fromY, toY);
            RenderHelper.renderObject(model, matrixStack, builder, 0xffffffff, light, overlay);
            HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), tile.getPos(), matrixStack, builder, light, overlay, model);
            yOffset += (entry * heightScale);
        }

        if (isVerticallyFilled) {
            Model3d model = getVerticalFillModel(verticalFillFluid, highestToY, verticalFillIntensity, maxY);
            RenderHelper.renderObject(model, matrixStack, builder, 0xffffffff, light, overlay);
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
        Minecraft.getInstance().getItemRenderer().renderItem(crystal, ItemCameraTransforms.TransformType.GROUND, light, OverlayTexture.NO_OVERLAY, matrixStack, buffer);
        matrixStack.pop();
    }

    private static final Map<Fluid, SpriteInfo[]> spriteCache = new HashMap();
    private static Model3d getVerticalFillModel(FluidStack fluid, float highestToY, float verticalFillIntensity, float maxY) {

        Vector3f verticalFillFrom = verticalFillFromVector(verticalFillIntensity), verticalFillTo = verticalFillToVector(verticalFillIntensity);
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
            model.minX = verticalFillFrom.getX() / 16f;
            model.minY = highestToY / 16f;
            model.minZ = verticalFillFrom.getZ() / 16f;

            model.maxX = verticalFillTo.getX() / 16f;
            model.maxY = maxY / 16f;
            model.maxZ = verticalFillTo.getZ() / 16f;
        }
        return model;
    }

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
            model.minX = FLUID_HORIZONTAL_OFFSET / 16f;
            model.minY = fromY / 16f;
            model.minZ = FLUID_HORIZONTAL_OFFSET / 16f;

            model.maxX = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
            model.maxY = toY / 16f;
            model.maxZ = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
        }
        return model;
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
