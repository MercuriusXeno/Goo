package com.xeno.goop.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goop.setup.Registry;
import com.xeno.goop.tiles.SolidifierTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.model.ModelManager;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SolidifierTileRenderer extends TileEntityRenderer<SolidifierTile>
{
    public SolidifierTileRenderer(TileEntityRendererDispatcher rendererDispatcherIn)
    {
        super(rendererDispatcherIn);
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.SOLIDIFIER_TILE.get(), SolidifierTileRenderer::new);
    }

    @Override
    public void render(SolidifierTile tile, float partialTicks, MatrixStack matrices, IRenderTypeBuffer buffer, int light, int overlay)
    {
        if (tile.getWorld() == null) {
            return;
        }
        // the light value of the block is 0; we want the light value of the area the item is rendering in, which is just offset a bit from the block.
        int itemLight = WorldRenderer.getCombinedLight(tile.getWorld(), tile.getPos().offset(tile.getHorizontalFacing()));

        matrices.push();
        // ItemFrameRenderer
        ItemStack item = tile.getDisplayedItem();
        if (!item.isEmpty()) {
            // translate to center
            matrices.translate(0.5D, 0.15D, 0.5D);
            // translate
            Vec3d vecOrient = this.getRenderOffset(tile, partialTicks);
            matrices.translate(vecOrient.getX(), vecOrient.getY(), vecOrient.getZ());
            // rotate
            Direction direction = tile.getHorizontalFacing();
            matrices.rotate(Vector3f.YP.rotationDegrees(180.0F - (float)(direction.getHorizontalIndex() * 90)));
            // scale
            boolean isBlock = item.getItem() instanceof BlockItem;
            boolean isX = tile.getHorizontalFacing().getAxis() == Direction.Axis.X;
            boolean isZ = tile.getHorizontalFacing().getAxis() == Direction.Axis.Z;
            float xScale = 0.2F * (isBlock ? 2F : 1F) * (isBlock && isX ? .05F : 1F);
            float yScale = 0.2F * (isBlock ? 2F : 1F);
            float zScale = 0.2F * (isBlock ? 2F : 1F) * (isBlock && isZ ? .05F : 1F);
            matrices.scale(xScale, yScale, zScale);
            RenderHelper.disableStandardItemLighting();

            Minecraft.getInstance().getItemRenderer().renderItem(item, ItemCameraTransforms.TransformType.FIXED, itemLight, overlay, matrices, buffer);
        }
        matrices.pop();
    }

    private static final Map<Direction, Vec3d> renderVec = new HashMap<>();
    static {
        renderVec.put(Direction.NORTH, new Vec3d(0D, 0D, -0.5D));
        renderVec.put(Direction.SOUTH, new Vec3d(0D, 0D, 0.5D));
        renderVec.put(Direction.EAST, new Vec3d(0.5D, 0D, 0D));
        renderVec.put(Direction.WEST, new Vec3d(-0.5D, 0D, 0D));
    }
    private Vec3d getRenderOffset(SolidifierTile entityIn, float partialTicks)
    {
        if (!renderVec.containsKey(entityIn.getHorizontalFacing())) {
            return Vec3d.ZERO;
        }
        return renderVec.get(entityIn.getHorizontalFacing());
    }
}
