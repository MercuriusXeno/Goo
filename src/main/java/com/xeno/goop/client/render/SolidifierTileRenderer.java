package com.xeno.goop.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goop.setup.Registry;
import com.xeno.goop.tiles.SolidifierTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import java.util.HashMap;
import java.util.Map;

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

        // one function of the solidifier is a safety mechanism to prevent accidentally changing the item.
        // when you attempt to change it, you get a flashing indication that you're trying to alter the target
        // which you must then confirm. this is achieved by checking the tile change timer is nonzero, abstracted into shouldFlash()
        // shouldFlash() is also only true when the world timer is inside an interval where flashing should occur.
        if (tile.shouldFlashTargetItem()) {
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
            boolean isBlock = item.getItem() instanceof BlockItem ;
            matrices.scale((isBlock ? 0.4F : 0.2F), (isBlock ? 0.4F : 0.2F), (isBlock ? .02F : 0.2F));
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
