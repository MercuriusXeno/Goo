package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooBulb;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.OverlayRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.BlockModel;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.ChestTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.ItemStackTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.List;

public class GooBulbItemRenderer extends ItemStackTileEntityRenderer
{
    public static final GooBulbItemRenderer instance = new GooBulbItemRenderer();
    public GooBulbItemRenderer() {
        super();

    }

    @Override
    public void func_239207_a_(ItemStack stack, ItemCameraTransforms.TransformType transforms, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLight, int combinedOverlay)
    {
        super.func_239207_a_(stack, transforms, matrixStack, buffer, combinedLight, combinedOverlay);
        IBakedModel bulbModel = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(Registry.GOO_BULB.get().getDefaultState());//.handlePerspective(transforms, matrixStack);

        Minecraft.getInstance().getItemRenderer().renderModel(bulbModel, stack, combinedLight, combinedOverlay, matrixStack, buffer.getBuffer(RenderType.getCutout()));

        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");

        TileEntity tileEntity = GooBulbTile.readTileEntity(Registry.GOO_BULB.get().getDefaultState(), bulbTag);
        if (tileEntity == null) {
            return;
        }

        TileEntityRendererDispatcher.instance.renderItem(tileEntity, matrixStack, buffer, combinedLight, combinedOverlay);
    }
}
