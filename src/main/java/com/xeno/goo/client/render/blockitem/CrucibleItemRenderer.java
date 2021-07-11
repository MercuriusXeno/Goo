package com.xeno.goo.client.render.blockitem;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.tiles.BulbTile;
import com.xeno.goo.tiles.CrucibleTile;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.Map;

public class CrucibleItemRenderer extends ItemStackTileEntityRenderer
{
    public CrucibleItemRenderer() {
        super();
    }

    @Override
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transforms, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay)
    {
        Block block = ((BlockItem)stack.getItem()).getBlock();
        //super.func_239207_a_(stack, transforms, matrixStack, buffer, combinedLight, combinedOverlay);
        IBakedModel crucibleModel = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(block.getDefaultState());

        Minecraft.getInstance().getItemRenderer().renderModel(crucibleModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getCutout()));

        renderTileSafely(stack, matrixStack, buffer, light, overlay, block);

        Minecraft.getInstance().getItemRenderer().renderModel(crucibleModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getTranslucent()));
    }

    private void renderTileSafely(ItemStack stack, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay, Block block) {

        CompoundNBT stackTag = stack.getTag();
        if (stackTag != null && stackTag.contains("BlockEntityTag")) {
            CompoundNBT tileTag = stackTag.getCompound("BlockEntityTag");

            TileEntity tileEntity = BulbTile.readTileEntity(block.getDefaultState(), tileTag);
            if (tileEntity instanceof CrucibleTile) {
                CrucibleTile crucible = (CrucibleTile)tileEntity;
                renderFluid(crucible, matrixStack, buffer, light, overlay);
            }
        }
    }

    private static final float FLUID_VERTICAL_OFFSET = 0.376f;
    private static final float FLUID_VERTICAL_MAX = (1f - 0.075f);
    private static final float FLUID_HORIZONTAL_OFFSET = 0.1876f;
    public static void renderFluid(CrucibleTile tile, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        IVertexBuilder builder = buffer.getBuffer(RenderType.getCutoutMipped());
        // IVertexBuilder builder = buffer.getBuffer(RenderType.getTranslucent());

        float yOffset = 0;

        float minY = FLUID_VERTICAL_OFFSET;
        float maxY = FLUID_VERTICAL_MAX;
        float heightScale = maxY - minY;

        Object2FloatMap<Fluid> entries = tile.calculateFluidHeights();
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
            Model3d model = getFluidModel(goo, fromY, toY);
            RenderHelper.renderObject(model, matrixStack, builder, GooFluid.FULL_OPACITY, light, overlay);
            HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), tile.getPos(), matrixStack, builder, light, overlay, model);
            yOffset += (entry * heightScale);
        }
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
            model.minY = fromY;
            model.minZ = FLUID_HORIZONTAL_OFFSET;

            model.maxX = 1f - FLUID_HORIZONTAL_OFFSET;
            model.maxY = toY;
            model.maxZ = 1f - FLUID_HORIZONTAL_OFFSET;
        }
        return model;
    }
}
