package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.client.render.block.HatchOpeningState.HatchOpeningStates;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import com.xeno.goo.setup.Resources.Hatch;
import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class SolidifierRenderer extends TileEntityRenderer<SolidifierTile>
{
    // vars holding the frame timing of hatch opening states
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

        renderHatch(tile, partialTicks, matrices, buffer, light, overlay);
    }

    private void renderHatch(SolidifierTile tile, float partialTicks, MatrixStack matrices, IRenderTypeBuffer buffer, int light, int overlay) {
        int hatchState = getHatchOpeningStateFromFrames(tile.hatchOpeningFrames());
        Model3d hatchModel = getHatchModel(hatchState);
        RenderHelper.renderObject(hatchModel, matrices, buffer.getBuffer(RenderType.getCutoutMipped()), 0xffffffff, light, overlay);
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
            }
        }

    }

    private static final Map<Direction, Vector3d> renderVec = new HashMap<>();
    static {
        renderVec.put(Direction.NORTH, new Vector3d(0D, 0D, -0.375D));
        renderVec.put(Direction.SOUTH, new Vector3d(0D, 0D, 0.375D));
        renderVec.put(Direction.EAST, new Vector3d(0.375D, 0D, 0D));
        renderVec.put(Direction.WEST, new Vector3d(-0.375D, 0D, 0D));
    }

    private static final Map<Integer, SpriteInfo[]> spriteCache = new HashMap();
    private static int getHatchOpeningStateFromFrames(int hatchOpeningFrames) {
        if (hatchOpeningFrames > SolidifierTile.HATCH_CLOSED_UPPER ||
                hatchOpeningFrames < SolidifierTile.HATCH_CLOSED_LOWER) {
            return SolidifierTile.HATCH_CLOSED_STATE;
        } else if (hatchOpeningFrames > SolidifierTile.HATCH_WAXING_UPPER ||
                hatchOpeningFrames < SolidifierTile.HATCH_WAXING_LOWER) {
            return SolidifierTile.HATCH_WAXING_STATE;
        } else if (hatchOpeningFrames > SolidifierTile.HATCH_HALF_UPPER ||
                hatchOpeningFrames < SolidifierTile.HATCH_HALF_LOWER) {
            return SolidifierTile.HATCH_HALF_STATE;
        } else if (hatchOpeningFrames > SolidifierTile.HATCH_WANING_UPPER ||
                hatchOpeningFrames < SolidifierTile.HATCH_WANING_LOWER) {
            return SolidifierTile.HATCH_WANING_STATE;
        } else {
            return SolidifierTile.HATCH_OPEN_STATE;
        }
    }
    private static Model3d getHatchModel(int hatchOpeningState) {
        Model3d model = new Model3d();
        if (spriteCache.containsKey(hatchOpeningState)) {
            SpriteInfo[] cache = spriteCache.get(hatchOpeningState);
            model.setTextures(cache[0], cache[1], cache[2], cache[2], cache[2], cache[2]);
        } else {
           ResourceLocation[] hatchStateSprites = getHatchStateSpritesFromState(hatchOpeningState);
            SpriteInfo[] sprites = new SpriteInfo[] {
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[0]), 16),
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[1]), 16),
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[2]), 16),
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[2]), 16),
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[2]), 16),
                    new SpriteInfo(RenderHelper.getSprite(hatchStateSprites[2]), 16)
            };
            spriteCache.put(hatchOpeningState, sprites);
            model.setTextures(sprites[0], sprites[1], sprites[2], sprites[3], sprites[4], sprites[5]);
        }

        model.minX = .125f;
        model.minY = .001f;
        model.minZ = .125f;

        model.maxX = .875f;
        model.maxY = .002f;
        model.maxZ = .875f;

        return model;
    }

    private static ResourceLocation[] getHatchStateSpritesFromState(int hatchOpeningState) {
        ResourceLocation[] results = new ResourceLocation[3];
        switch(hatchOpeningState) {
            case SolidifierTile.HATCH_CLOSED_STATE:
                results[0] = Hatch.OUTER_CLOSED;
                results[1] = Hatch.INNER_CLOSED;
                break;
            case SolidifierTile.HATCH_WAXING_STATE:
                results[0] = Hatch.OUTER_WAXING;
                results[1] = Hatch.INNER_WAXING;
                break;
            case SolidifierTile.HATCH_HALF_STATE:
                results[0] = Hatch.OUTER_HALF;
                results[1] = Hatch.INNER_HALF;
                break;
            case SolidifierTile.HATCH_WANING_STATE:
                results[0] = Hatch.OUTER_WANING;
                results[1] = Hatch.INNER_WANING;
                break;
            case SolidifierTile.HATCH_OPEN_STATE:
                results[0] = Hatch.OUTER_OPEN;
                results[1] = Hatch.INNER_OPEN;
                break;
        }
        results[2] = Resources.EMPTY;
        return results;
    }
}
