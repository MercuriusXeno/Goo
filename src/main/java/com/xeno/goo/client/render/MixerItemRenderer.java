package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;

public class MixerItemRenderer extends ItemStackTileEntityRenderer
{
    public static final MixerItemRenderer instance = new MixerItemRenderer();
    public MixerItemRenderer() {
        super();
    }

    @Override
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transforms, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay)
    {
        Block block = ((BlockItem)stack.getItem()).getBlock();
        super.func_239207_a_(stack, transforms, matrixStack, buffer, combinedLight, combinedOverlay);
        IBakedModel mixerModel = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(block.getDefaultState());

        Minecraft.getInstance().getItemRenderer().renderModel(mixerModel, stack, combinedLight, combinedOverlay, matrixStack, buffer.getBuffer(RenderType.getCutout()));

        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT mixerTag = stackTag.getCompound("BlockEntityTag");

        TileEntity tileEntity = MixerTile.readTileEntity(block.getDefaultState(), mixerTag);
        if (tileEntity == null) {
            return;
        }

        TileEntityRendererDispatcher.instance.renderItem(tileEntity, matrixStack, buffer, combinedLight, combinedOverlay);
    }
}
