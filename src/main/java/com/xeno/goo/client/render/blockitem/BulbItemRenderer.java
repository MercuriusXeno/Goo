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
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
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

public class BulbItemRenderer extends ItemStackTileEntityRenderer
{
    public BulbItemRenderer() {
        super();
    }

    @Override
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transforms, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay)
    {
        Block block = ((BlockItem)stack.getItem()).getBlock();
        super.func_239207_a_(stack, transforms, matrixStack, buffer, light, overlay);
        IBakedModel bulbModel = Minecraft.getInstance().getBlockRendererDispatcher()
                .getModelForState(block.getDefaultState());


        Minecraft.getInstance().getItemRenderer().renderModel(bulbModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getCutoutMipped()));
        renderTileSafely(stack, 0f, matrixStack, buffer, light, overlay, block);
        Minecraft.getInstance().getItemRenderer().renderModel(bulbModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getTranslucent()));
    }

    private void renderTileSafely(ItemStack stack, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay, Block block) {

        CompoundNBT stackTag = stack.getTag();
        if (stackTag != null && stackTag.contains("BlockEntityTag")) {
            CompoundNBT tileTag = stackTag.getCompound("BlockEntityTag");

            TileEntity tileEntity = BulbTile.readTileEntity(block.getDefaultState(), tileTag);
            if (tileEntity instanceof BulbTile) {
                BulbTile bulb = (BulbTile)tileEntity;
                renderFluid(bulb, partialTicks, matrixStack, buffer, light, overlay);
            }
        }
    }

    private static final float FLUID_HORIZONTAL_OFFSET = 0.15f;
    private static final float FROM_SCALED_VERTICAL = BulbTile.FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - (BulbTile.FLUID_VERTICAL_MAX * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);
    public static void renderFluid(BulbTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        IVertexBuilder builder = buffer.getBuffer(RenderType.getCutoutMipped());
        // IVertexBuilder builder = buffer.getBuffer(RenderType.getTranslucent());

        float yOffset = 0;

        // determine where to draw the fluid based on the model
        Vector3f from = FROM_FALLBACK, to = TO_FALLBACK;

        float minY = from.getY();
        float maxY = to.getY();
        float heightScale = maxY - minY;

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
            model.minX = FLUID_HORIZONTAL_OFFSET / 16f;
            model.minY = fromY / 16f;
            model.minZ = FLUID_HORIZONTAL_OFFSET / 16f;

            model.maxX = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
            model.maxY = toY / 16f;
            model.maxZ = (16f - FLUID_HORIZONTAL_OFFSET) / 16f;
        }
        return model;
    }
}
