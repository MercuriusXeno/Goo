package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import com.xeno.goo.tiles.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class MixerRenderer extends TileEntityRenderer<MixerTile> {
    private static final float INPUT_Y_OFFSET = 4.03f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "left" just means west if facing north.
    private static final float RIGHT_INPUT_X_START_OFFSET = 0.03f;
    private static final float RIGHT_INPUT_X_END_OFFSET = 5.97f;
    private static final float RIGHT_INPUT_Z_START_OFFSET = 2.03f;
    private static final float RIGHT_INPUT_Z_END_OFFSET = 13.97f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "right" just means east if facing north.
    private static final float LEFT_INPUT_X_START_OFFSET = 10.03f;
    private static final float LEFT_INPUT_X_END_OFFSET = 15.97f;
    private static final float LEFT_INPUT_Z_START_OFFSET = 2.03f;
    private static final float LEFT_INPUT_Z_END_OFFSET = 13.97f;
    // left channel dimension
    private static final float CHANNEL_Y_OFFSET = 0.03f;
    private static final float RIGHT_CHANNEL_X_START_OFFSET = 1.03f;
    private static final float RIGHT_CHANNEL_X_END_OFFSET = 4.97f;
    private static final float RIGHT_CHANNEL_Z_START_OFFSET = 6.03f;
    private static final float RIGHT_CHANNEL_Z_END_OFFSET = 9.97f;

    // right channel dimensions
    private static final float LEFT_CHANNEL_X_START_OFFSET = 11.03f;
    private static final float LEFT_CHANNEL_X_END_OFFSET = 14.97f;
    private static final float LEFT_CHANNEL_Z_START_OFFSET = 6.03f;
    private static final float LEFT_CHANNEL_Z_END_OFFSET = 9.97f;

    private static final float MAX_FLUID_HEIGHT_INPUT = 11.94f;
    private static final float MAX_FLUID_HEIGHT_CHANNEL = 3.94f;

    public MixerRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(MixerTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        // mixers have 2 tanks
        IFluidHandler left = FluidHandlerHelper.capability(tile, tile.orientedLeft());
        IFluidHandler right = FluidHandlerHelper.capability(tile, tile.orientedRight());
        if (left == null || right == null) {
            // this is real bad, abort
            return;
        }
        render(tile.facing(), tile, left.getTankCapacity(0), right.getTankCapacity(0),
                left.getFluidInTank(0), right.getFluidInTank(0),
                matrixStack, buffer, combinedLightIn, combinedOverlayIn);
    }

    private void render(Direction facing, MixerTile tile, int leftCap, int rightCap,
            FluidStack leftGoo, FluidStack rightGoo,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn)
    {
        if (leftCap == 0 || rightCap == 0) {
            // divide by zeros are bad :|
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(GooRenderHelper.GOO_BLOCK);
        renderTankFluid(tile.getPos(), false, facing, leftCap, leftGoo, matrixStack, builder, combinedLightIn);
        renderTankFluid(tile.getPos(), true, facing, rightCap, rightGoo, matrixStack, builder, combinedLightIn);
        renderChannelFluid(tile.getPos(), false, facing, leftGoo, matrixStack, builder, combinedLightIn);
        renderChannelFluid(tile.getPos(), true, facing, rightGoo, matrixStack, builder, combinedLightIn);
    }

    private void renderTankFluid(BlockPos pos, boolean isRight, Direction facing, int capacity, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidTankDimensionsTo(isRight, facing, goo.getAmount(), capacity);
        Vector3f from = fluidTankDimensionsFrom(isRight, facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        HighlightingHelper.renderHighlightAsNeeded(goo, pos, matrixStack, builder, combinedLightIn, from, from.getY(), to, to.getY());
    }

    private void renderChannelFluid(BlockPos pos, boolean isRight, Direction facing, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidChannelDimensionsTo(isRight, facing);
        Vector3f from = fluidChannelDimensionsFrom(isRight, facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        HighlightingHelper.renderHighlightAsNeeded(goo, pos, matrixStack, builder, combinedLightIn, from, from.getY(), to, to.getY());
    }

    private Vector3f fluidChannelDimensionsTo(boolean isRight, Direction facing)
    {
        // this is the total fill percentage of the container

        float toY = CHANNEL_Y_OFFSET + MAX_FLUID_HEIGHT_CHANNEL;
        float toX = endingChannelFromSideAndFaceX(isRight, facing);
        float toZ = endingChannelFromSideAndFaceZ(isRight, facing);
        return new Vector3f(toX, toY, toZ);
    }

    private Vector3f fluidChannelDimensionsFrom(boolean isRight, Direction facing)
    {
        float fromY = CHANNEL_Y_OFFSET;
        float fromX = startingChannelFromSideAndFaceX(isRight, facing);
        float fromZ = startingChannelFromSideAndFaceZ(isRight, facing);

        return new Vector3f(fromX, fromY, fromZ);
    }

    private float startingChannelFromSideAndFaceX(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_Z_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingChannelFromSideAndFaceX(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_Z_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private float startingChannelFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_X_START_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_X_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingChannelFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_X_END_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_X_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private Vector3f fluidTankDimensionsTo(boolean isRight, Direction facing, int amount, int capacity)
    {
        // this is the total fill percentage of the container

        float toY = endingInputFromSideAndFaceY(amount, capacity);
        float toX = endingInputFromSideAndFaceX(isRight, facing);
        float toZ = endingInputFromSideAndFaceZ(isRight, facing);
        return new Vector3f(toX, toY, toZ);
    }

    private Vector3f fluidTankDimensionsFrom(boolean isRight, Direction facing)
    {
        float fromY = INPUT_Y_OFFSET;
        float fromX = startingInputFromSideAndFaceX(isRight, facing);
        float fromZ = startingInputFromSideAndFaceZ(isRight, facing);

        return new Vector3f(fromX, fromY, fromZ);
    }

    private float endingInputFromSideAndFaceY(int amount, int capacity)
    {
        return scaledHeightTank(amount, capacity) + INPUT_Y_OFFSET;
    }

    private float scaledHeightTank(int amount, int capacity)
    {
        return Math.max(ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, amount / (float)capacity) * MAX_FLUID_HEIGHT_INPUT;
    }

    private float startingInputFromSideAndFaceX(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_X_START_OFFSET;
                case EAST:
                    return LEFT_INPUT_Z_START_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_X_START_OFFSET;
                case WEST:
                    return RIGHT_INPUT_Z_START_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_X_START_OFFSET;
                case EAST:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_X_START_OFFSET;
                case WEST:
                    return LEFT_INPUT_Z_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceX(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_X_END_OFFSET;
                case EAST:
                    return LEFT_INPUT_Z_END_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_X_END_OFFSET;
                case WEST:
                    return RIGHT_INPUT_Z_END_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_X_END_OFFSET;
                case EAST:
                    return RIGHT_INPUT_Z_END_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_X_END_OFFSET;
                case WEST:
                    return LEFT_INPUT_Z_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private float startingInputFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_Z_START_OFFSET;
                case EAST:
                    return LEFT_INPUT_X_START_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case WEST:
                    return RIGHT_INPUT_X_START_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case EAST:
                    return RIGHT_INPUT_X_START_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_Z_START_OFFSET;
                case WEST:
                    return LEFT_INPUT_X_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_Z_END_OFFSET;
                case EAST:
                    return LEFT_INPUT_X_END_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_Z_END_OFFSET;
                case WEST:
                    return RIGHT_INPUT_X_END_OFFSET;
            }
        } else {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_Z_END_OFFSET;
                case EAST:
                    return RIGHT_INPUT_X_END_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_Z_END_OFFSET;
                case WEST:
                    return LEFT_INPUT_X_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.MIXER_TILE.get(), MixerRenderer::new);
    }
}
