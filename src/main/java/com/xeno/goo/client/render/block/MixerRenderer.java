package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.client.render.RenderHelper.FluidType;
import com.xeno.goo.client.render.block.DynamicRenderMode.DynamicRenderTypes;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooPumpTile;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Atlases;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.fluid.Fluid;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Quaternion;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class MixerRenderer extends TileEntityRenderer<MixerTile> {
    // output dimensions
    private static final float OUTPUT_Y_OFFSET = 0.11f;
    private static final float OUTPUT_X_START_OFFSET = 0.11f;
    private static final float OUTPUT_X_END_OFFSET = 15.89f;
    private static final float OUTPUT_Z_START_OFFSET = 0.11f;
    private static final float OUTPUT_Z_END_OFFSET = 15.89f;
    private static final float MAX_FLUID_HEIGHT_OUTPUT = 15.78f;

    private final BlockRendererDispatcher blockRenderer = Minecraft.getInstance().getBlockRendererDispatcher();

    public MixerRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(MixerTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay) {
        LazyOptional<IFluidHandler> mixerHandler = FluidHandlerHelper.capabilityOfSelf(tile, null);
        mixerHandler.ifPresent((b) ->
                render(tile.facing(), tile, b.getTankCapacity(0), b.getFluidInTank(0), matrixStack, buffer, light, overlay)
        );

        renderSpinner(tile, matrixStack, buffer, light, overlay, partialTicks);
    }

    private void render(Direction facing, MixerTile tile, int bottomCap, FluidStack bottomGoo,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int light, int overlay)
    {
        if (bottomCap == 0) {
            // divide by zeros are bad :|
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(Atlases.getTranslucentCullBlockType());
        IVertexBuilder highlightVertex = buffer.getBuffer(RenderHelper.GOO_CUBE);
        renderOutputFluid(tile.getPos(), facing, bottomGoo, bottomCap, matrixStack, builder, highlightVertex, light, overlay);
    }

    private void renderSpinner(MixerTile tile, MatrixStack matrices, IRenderTypeBuffer bufferIn, int light, int overlay, float partialTicks) {
        matrices.push();
        final BlockState dynamicState = tile.getBlockState()
                .with(BlockStateProperties.HORIZONTAL_FACING, tile.facing())
                .with(BlockStateProperties.POWERED, true)
                .with(DynamicRenderMode.RENDER, DynamicRenderTypes.DYNAMIC);
        // rotate the spinner based on the tile's rotation properties
        matrices.translate(0.5d, 0d, 0.5d);
        Quaternion rotationQuat = new Quaternion(0f, tile.spinnerDegrees(), 0f, true);
        matrices.rotate(rotationQuat);
        matrices.translate(-0.5d, 0d, -0.5d);
        blockRenderer.renderBlock(dynamicState, matrices, bufferIn, light, overlay, EmptyModelData.INSTANCE);
        matrices.pop();
    }

    private void renderOutputFluid(BlockPos pos, Direction facing, FluidStack goo, int cap, MatrixStack matrixStack, IVertexBuilder builder, IVertexBuilder highlightVertex, int light, int overlay)
    {
        boolean shouldHighlight = HighlightingHelper.isTargeted(goo.getFluid(), pos);
        int highlight = 0; //HighlightingHelper.getHighlight(shouldHighlight);

        Model3d model = getFluidModel(goo, goo.getAmount() / (float)cap);
        RenderHelper.renderObject(model, matrixStack, builder, 0xffffffff, light, overlay);
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, light, overlay, model);
    }

    private static final Map<Fluid, SpriteInfo[]> spriteCache = new HashMap();
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

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.MIXER_TILE.get(), MixerRenderer::new);
    }
}
