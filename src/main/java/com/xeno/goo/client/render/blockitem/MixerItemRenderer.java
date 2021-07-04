package com.xeno.goo.client.render.blockitem;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mojang.datafixers.util.Pair;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.client.render.block.DynamicRenderMode;
import com.xeno.goo.client.render.block.DynamicRenderMode.DynamicRenderTypes;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.tiles.BulbTile;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.model.MultipartBakedModel;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.ForgeRenderTypes;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.extensions.IForgeBakedModel;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MixerItemRenderer extends ItemStackTileEntityRenderer
{
    private static Random r = new Random();
    public MixerItemRenderer() {
        super();
    }
    private static ItemRenderer itemRenderer() {
        return Minecraft.getInstance().getItemRenderer();
    }
    @Override
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transforms, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay)
    {
        RenderType previousLayer = MinecraftForgeClient.getRenderLayer();
        Block block = ((BlockItem)stack.getItem()).getBlock();
        BlockState state = block.getDefaultState().with(DynamicRenderMode.RENDER, DynamicRenderTypes.ITEM);
        IBakedModel itemModel = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(state);

        // frame and spinner
        Minecraft.getInstance().getItemRenderer().renderModel(itemModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getCutoutMipped()));
        // fluid, but cutout mipped and not translucent
        renderTileSafely(stack, 0f, matrixStack, buffer, light, overlay, block);
        // glass, translucent, last
        Minecraft.getInstance().getItemRenderer().renderModel(itemModel, stack, light, overlay, matrixStack, buffer.getBuffer(RenderType.getTranslucent()));
    }

    private void renderTileSafely(ItemStack stack, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay, Block block) {
        CompoundNBT stackTag = stack.getTag();
        if (stackTag != null && stackTag.contains("BlockEntityTag")) {
            CompoundNBT tileTag = stackTag.getCompound("BlockEntityTag");

            TileEntity tileEntity = MixerTile.readTileEntity(block.getDefaultState(), tileTag);
            if (tileEntity instanceof MixerTile) {
                MixerTile mixer = (MixerTile)tileEntity;
                renderFluid(mixer, partialTicks, matrixStack, buffer, light, overlay);
            }
        }
    }

    private void renderFluid(MixerTile mixer, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        LazyOptional<IFluidHandler> lazy = mixer.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        if (!lazy.isPresent() || !lazy.resolve().isPresent()) {
            return;
        }

        int cap = lazy.resolve().get().getTankCapacity(0);
        FluidStack goo = lazy.resolve().get().getFluidInTank(0);

        IVertexBuilder builder = buffer.getBuffer(RenderType.getCutoutMipped());

        Model3d model = getFluidModel(goo, goo.getAmount() / (float)cap);
        RenderHelper.renderObject(model, matrixStack, builder, GooFluid.UNCOLORED_WITH_PARTIAL_TRANSPARENCY, light, overlay);

    }

    private static final Map<Fluid, SpriteInfo[]> spriteCache = new HashMap();
    private static final float OUTPUT_Y_OFFSET = 0.11f;
    private static final float OUTPUT_X_START_OFFSET = 0.11f;
    private static final float OUTPUT_X_END_OFFSET = 15.89f;
    private static final float OUTPUT_Z_START_OFFSET = 0.11f;
    private static final float OUTPUT_Z_END_OFFSET = 15.89f;
    private static final float MAX_FLUID_HEIGHT_OUTPUT = 15.78f;
    private Model3d getFluidModel(FluidStack fluid, float scale) {
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
            model.minX = OUTPUT_X_START_OFFSET / 16f;
            model.minY = OUTPUT_Y_OFFSET / 16f;
            model.minZ = OUTPUT_Z_START_OFFSET / 16f;

            model.maxX = OUTPUT_X_END_OFFSET / 16f;
            model.maxY = (OUTPUT_Y_OFFSET + (MAX_FLUID_HEIGHT_OUTPUT * scale)) / 16f;
            model.maxZ = OUTPUT_Z_END_OFFSET / 16f;
        }
        return model;
    }
}
