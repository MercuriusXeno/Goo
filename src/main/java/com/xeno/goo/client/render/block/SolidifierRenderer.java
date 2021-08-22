package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class SolidifierRenderer extends TileEntityRenderer<SolidifierTile>
{
    private int lastItemLight;
    public SolidifierRenderer(TileEntityRendererDispatcher rendererDispatcherIn)
    {
        super(rendererDispatcherIn);
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.SOLIDIFIER_TILE.get(), SolidifierRenderer::new);
    }

    @Override
    public void render(SolidifierTile tile, float partialTicks, MatrixStack matrices, IRenderTypeBuffer buffer, int light, int overlay)
    {
        if (tile.getWorld() == null) {
            return;
        }

        renderItem(tile, matrices, buffer);
    }

    private void renderItem(SolidifierTile tile, MatrixStack matrices, IRenderTypeBuffer buffer) {


        // one function of the solidifier is a safety mechanism to prevent accidentally changing the item.
        // when you attempt to change it, you get a flashing indication that you're trying to alter the target
        // which you must then confirm. this is achieved by checking the tile change timer is nonzero, abstracted into shouldFlash()
        // shouldFlash() is also only true when the world timer is inside an interval where flashing should occur.
        if (tile.shouldFlashTargetItem()) {
            return;
        }

        // ItemFrameRenderer
        ItemStack item = tile.getDisplayedItem();
        if (!item.isEmpty()) {
            IBakedModel model = Minecraft.getInstance().getItemRenderer().getItemModelMesher().getItemModel(item.getItem());
            if (model == null) {
                return;
            }
            for (Direction d : Direction.values()) {
                if (d == Direction.UP || d == Direction.DOWN) {
                    continue;
                }

                // the light value of the block is 0; we want the light value of the area the item is rendering in, which is just offset a bit from the block.
                int itemLight = WorldRenderer.getCombinedLight(tile.getWorld(), tile.getPos().offset(d));
                matrices.push();
                // translate to center
                matrices.translate(0.5D, 0.75D, 0.5D);
                // translate
                Vector3d vecOrient = renderVec.get(d);
                matrices.translate(vecOrient.getX(), vecOrient.getY(), vecOrient.getZ());
                matrices.rotate(Vector3f.YP.rotationDegrees(180.0F - (float)(d.getHorizontalIndex() * 90)));
                // scale
                Vector3f scaleVec = new Vector3f(0.25f, 0.25f, 0.03f);
                // special scaling that doesn't scale the normals, prevents weird lighting issues.
                MatrixStack.Entry last = matrices.getLast();
                last.getMatrix().mul(Matrix4f.makeScale(scaleVec.getX(), scaleVec.getY(), scaleVec.getZ()));

                Minecraft.getInstance().getItemRenderer().renderItem(item, ItemCameraTransforms.TransformType.FIXED, itemLight, OverlayTexture.NO_OVERLAY, matrices, buffer);
                matrices.pop();

                // draw the hatch based on the tile state
                tile.hatchOpeningFrames();
            }
        }

    }

    private static final Map<Direction, Vector3d> renderVec = new HashMap<>();
    static {
        renderVec.put(Direction.NORTH, new Vector3d(0D, 0D, -0.3125D));
        renderVec.put(Direction.SOUTH, new Vector3d(0D, 0D, 0.3125D));
        renderVec.put(Direction.EAST, new Vector3d(0.3125D, 0D, 0D));
        renderVec.put(Direction.WEST, new Vector3d(-0.3125D, 0D, 0D));
    }
}
