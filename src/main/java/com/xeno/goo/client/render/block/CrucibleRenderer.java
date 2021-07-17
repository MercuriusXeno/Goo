package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.CrucibleTile;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.TroughTile;
import com.xeno.goo.util.IGooTank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrucibleRenderer extends TileEntityRenderer<CrucibleTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.3126f;
    private static final float FLUID_VERTICAL_MAX = (1f - 0.075f);
    private static final float FLUID_HORIZONTAL_OFFSET = 0.126f;

    public CrucibleRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(CrucibleTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        LazyOptional<IFluidHandler> cap = FluidHandlerHelper.capabilityOfSelf(tile, null);
        cap.ifPresent((c) -> render(tile, matrixStack, buffer, light, overlay));
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;
    public static void render(CrucibleTile tile, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {

        List<FluidStack> allGoo = tile.getAllGooContentsFromTile();
        float lastMaxY = FLUID_VERTICAL_OFFSET;
        BlockPos pos = tile.getPos();
        int crucibleCapacity = tile.getStorageCapacity();
        float yOffset = 0;
        float minY = FLUID_VERTICAL_OFFSET;
        float maxY = FLUID_VERTICAL_MAX;
        float gap = maxY - minY;
        for(FluidStack goo : allGoo) {
            float totalGoo = goo.getAmount();
            if (goo.isEmpty()) {
                continue;
            }
            IVertexBuilder builder = buffer.getBuffer(Atlases.getTranslucentCullBlockType());
            float minHeight = ARBITRARY_GOO_STACK_HEIGHT_MINIMUM;

            // this is the total fill percentage of the container
            float scaledHeight = Math.max(minHeight, totalGoo / (float) crucibleCapacity);

            // this is the total fill of the goo in the tank of this particular goo, as a percentage
            float percentage = goo.getAmount() / totalGoo;
            float heightScale = percentage * scaledHeight;
            float height = gap * heightScale;
            float fromY, toY;
            fromY = minY + yOffset;
            toY = fromY + height;
            lastMaxY = toY;
            Model3d fluidModel = getFluidModel(goo, fromY, toY);
            RenderHelper.renderObject(fluidModel, matrixStack, builder, GooFluid.UNCOLORED_WITH_PARTIAL_TRANSPARENCY, light, overlay);
            HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, light, overlay, fluidModel);
            yOffset += height;
        }
        float progressPercent = tile.simplifiedProgress();
        ItemStack currentItem = tile.currentItem();
        if (currentItem.isEmpty()) {
            return;
        }
        matrixStack.push();
        matrixStack.translate(0.5d, lastMaxY, 0.5d);
        matrixStack.scale(1f, (1f - progressPercent), 1f);
        matrixStack.rotate(new Quaternion(0f, 45f, 0f, true));
        GooMod.debug("Item offset " + (lastMaxY + FLUID_VERTICAL_OFFSET));
        Minecraft.getInstance().getItemRenderer().renderItem(currentItem, TransformType.GROUND, light, overlay, matrixStack, buffer);
        matrixStack.pop();
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

            model.maxX = (1f - FLUID_HORIZONTAL_OFFSET);
            model.maxY = toY;
            model.maxZ = (1f - FLUID_HORIZONTAL_OFFSET);
        }
        return model;
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.CRUCIBLE_TILE.get(), CrucibleRenderer::new);
    }
}
