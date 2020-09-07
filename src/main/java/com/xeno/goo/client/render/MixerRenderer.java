package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.*;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class MixerRenderer extends TileEntityRenderer<MixerTile> {
    private static final float INPUT_Y_OFFSET = 4.03f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "left" just means west if facing north.
    private static final float LEFT_INPUT_X_START_OFFSET = 0.03f;
    private static final float LEFT_INPUT_X_END_OFFSET = 5.97f;
    private static final float LEFT_INPUT_Z_START_OFFSET = 2.03f;
    private static final float LEFT_INPUT_Z_END_OFFSET = 13.97f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "right" just means east if facing north.
    private static final float RIGHT_INPUT_X_START_OFFSET = 10.03f;
    private static final float RIGHT_INPUT_X_END_OFFSET = 15.97f;
    private static final float RIGHT_INPUT_Z_START_OFFSET = 2.03f;
    private static final float RIGHT_INPUT_Z_END_OFFSET = 13.97f;
    // left channel dimension
    private static final float CHANNEL_Y_OFFSET = 0.03f;
    private static final float LEFT_CHANNEL_X_START_OFFSET = 1.03f;
    private static final float LEFT_CHANNEL_X_END_OFFSET = 4.97f;
    private static final float LEFT_CHANNEL_Z_START_OFFSET = 6.03f;
    private static final float LEFT_CHANNEL_Z_END_OFFSET = 9.97f;

    // right channel dimensions
    private static final float RIGHT_CHANNEL_X_START_OFFSET = 11.03f;
    private static final float RIGHT_CHANNEL_X_END_OFFSET = 14.97f;
    private static final float RIGHT_CHANNEL_Z_START_OFFSET = 6.03f;
    private static final float RIGHT_CHANNEL_Z_END_OFFSET = 9.97f;

    private static final float MAX_FLUID_HEIGHT_INPUT = 11.94f;
    private static final float MAX_FLUID_HEIGHT_CHANNEL = 3.94f;

    public MixerRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(MixerTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        // mixers have 2 tanks
        IFluidHandler west = FluidHandlerHelper.capability(tile, tile.orientedLeft());
        IFluidHandler east = FluidHandlerHelper.capability(tile, tile.orientedRight());
        if (west == null || east == null) {
            // this is real bad, abort
            return;
        }
        render(tile.facing(), tile, west.getTankCapacity(0), east.getTankCapacity(0),
                west.getFluidInTank(0), east.getFluidInTank(0),
                matrixStack, buffer, combinedLightIn, combinedOverlayIn);
    }

    private void render(Direction facing, MixerTile tile, int westCapacity, int eastCapacity,
            FluidStack westFluid, FluidStack eastFluid,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn)
    {
        if (westCapacity == 0 || eastCapacity == 0) {
            // divide by zeros are bad :|
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(GooRenderHelper.GOO_BLOCK);
        renderTankFluid(Direction.WEST, facing, westCapacity, westFluid, matrixStack, builder, combinedLightIn);
        renderTankFluid(Direction.EAST, facing, eastCapacity, eastFluid, matrixStack, builder, combinedLightIn);
        renderChannelFluid(Direction.WEST, facing, westFluid, matrixStack, builder, combinedLightIn);
        renderChannelFluid(Direction.EAST, facing, eastFluid, matrixStack, builder, combinedLightIn);
    }

    private void renderTankFluid(Direction tankSide, Direction facing, int capacity, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidTankDimensionsTo(tankSide, facing, goo.getAmount(), capacity);
        Vector3f from = fluidTankDimensionsFrom(tankSide, facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private void renderChannelFluid(Direction tankSide, Direction facing, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidChannelDimensionsTo(tankSide, facing);
        Vector3f from = fluidChannelDimensionsFrom(tankSide, facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private Vector3f fluidChannelDimensionsTo(Direction tankSide, Direction facing)
    {
        // this is the total fill percentage of the container

        float toY = CHANNEL_Y_OFFSET + MAX_FLUID_HEIGHT_CHANNEL;
        float toX = endingChannelFromSideAndFaceX(tankSide, facing);
        float toZ = endingChannelFromSideAndFaceZ(tankSide, facing);
        return new Vector3f(toX, toY, toZ);
    }

    private Vector3f fluidChannelDimensionsFrom(Direction tankSide, Direction facing)
    {
        float fromY = CHANNEL_Y_OFFSET;
        float fromX = startingChannelFromSideAndFaceX(tankSide, facing);
        float fromZ = startingChannelFromSideAndFaceZ(tankSide, facing);

        return new Vector3f(fromX, fromY, fromZ);
    }

    private float startingChannelFromSideAndFaceX(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_Z_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingChannelFromSideAndFaceX(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_Z_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private float startingChannelFromSideAndFaceZ(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_X_START_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_X_START_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_Z_START_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_X_START_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_Z_START_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_X_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingChannelFromSideAndFaceZ(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case EAST:
                    return LEFT_CHANNEL_X_END_OFFSET;
                case SOUTH:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case WEST:
                    return RIGHT_CHANNEL_X_END_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_CHANNEL_Z_END_OFFSET;
                case EAST:
                    return RIGHT_CHANNEL_X_END_OFFSET;
                case SOUTH:
                    return RIGHT_CHANNEL_Z_END_OFFSET;
                case WEST:
                    return LEFT_CHANNEL_X_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private Vector3f fluidTankDimensionsTo(Direction tankSide, Direction facing, int amount, int capacity)
    {
        // this is the total fill percentage of the container

        float toY = endingInputFromSideAndFaceY(amount, capacity);
        float toX = endingInputFromSideAndFaceX(tankSide, facing);
        float toZ = endingInputFromSideAndFaceZ(tankSide, facing);
        return new Vector3f(toX, toY, toZ);
    }

    private Vector3f fluidTankDimensionsFrom(Direction tankSide, Direction facing)
    {
        float fromY = INPUT_Y_OFFSET;
        float fromX = startingInputFromSideAndFaceX(tankSide, facing);
        float fromZ = startingInputFromSideAndFaceZ(tankSide, facing);

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

    private float startingInputFromSideAndFaceX(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_X_START_OFFSET;
                case EAST:
                    return LEFT_INPUT_Z_START_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_X_START_OFFSET;
                case WEST:
                    return RIGHT_INPUT_Z_START_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_X_START_OFFSET;
                case EAST:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_X_START_OFFSET;
                case WEST:
                    return LEFT_INPUT_Z_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceX(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_X_END_OFFSET;
                case EAST:
                    return LEFT_INPUT_Z_END_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_X_END_OFFSET;
                case WEST:
                    return RIGHT_INPUT_Z_END_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_X_END_OFFSET;
                case EAST:
                    return RIGHT_INPUT_Z_END_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_X_END_OFFSET;
                case WEST:
                    return LEFT_INPUT_Z_END_OFFSET;
            }
        }
        return 16f; // this is bad
    }

    private float startingInputFromSideAndFaceZ(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case EAST:
                    return LEFT_INPUT_X_START_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_Z_START_OFFSET;
                case WEST:
                    return RIGHT_INPUT_X_START_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_Z_START_OFFSET;
                case EAST:
                    return RIGHT_INPUT_X_START_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case WEST:
                    return LEFT_INPUT_X_START_OFFSET;
            }
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceZ(Direction tankSide, Direction facing)
    {
        if (tankSide == Direction.EAST) {
            switch(facing) {
                case NORTH:
                    return RIGHT_INPUT_Z_END_OFFSET;
                case EAST:
                    return LEFT_INPUT_X_END_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_Z_END_OFFSET;
                case WEST:
                    return RIGHT_INPUT_X_END_OFFSET;
            }
        } else if (tankSide == Direction.WEST) {
            switch(facing) {
                case NORTH:
                    return LEFT_INPUT_Z_END_OFFSET;
                case EAST:
                    return RIGHT_INPUT_X_END_OFFSET;
                case SOUTH:
                    return RIGHT_INPUT_Z_END_OFFSET;
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
