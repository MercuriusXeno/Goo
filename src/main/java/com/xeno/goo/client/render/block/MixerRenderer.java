package com.xeno.goo.client.render.block;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.HighlightingHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import com.xeno.goo.setup.Resources.Glass;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.MixerTile;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class MixerRenderer extends TileEntityRenderer<MixerTile> {
    private static final float INPUT_Y_OFFSET = 6.03f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "left" just means west if facing north.
    private static final float LEFT_INPUT_X_START_OFFSET = 0.03f;
    private static final float LEFT_INPUT_X_END_OFFSET = 5.97f;
    private static final float LEFT_INPUT_Z_START_OFFSET = 0.03f;
    private static final float LEFT_INPUT_Z_END_OFFSET = 15.97f;
    // names a bit misleading;  if the block is rotated, the X is the Z and vice versa.
    // "right" just means east if facing north.
    private static final float RIGHT_INPUT_X_START_OFFSET = 10.03f;
    private static final float RIGHT_INPUT_X_END_OFFSET = 15.97f;
    private static final float RIGHT_INPUT_Z_START_OFFSET = 0.03f;
    private static final float RIGHT_INPUT_Z_END_OFFSET = 15.97f;

    // output dimensions
    private static final float OUTPUT_Y_OFFSET = 0.03f;
    private static final float OUTPUT_X_START_OFFSET = 0.03f;
    private static final float OUTPUT_X_END_OFFSET = 15.97f;
    private static final float OUTPUT_Z_START_OFFSET = 6.03f;
    private static final float OUTPUT_Z_END_OFFSET = 15.97f;

    private static final float MAX_FLUID_HEIGHT_INPUT = 9.94f;
    private static final float MAX_FLUID_HEIGHT_OUTPUT = 5.94f;

    private static final float TOP_GLASS_Y_START = 15.98f;
    private static final float TOP_GLASS_Y_END = 15.981f;
    private static final float TOP_GLASS_X_START = 0.02f;
    private static final float TOP_GLASS_X_END = 15.98f;
    private static final float TOP_GLASS_Z_START = 0.02f;
    private static final float TOP_GLASS_Z_END = 15.98f;

    private static final Vector3f topGlassStartVec = new Vector3f(TOP_GLASS_X_START, TOP_GLASS_Y_START, TOP_GLASS_Z_START);
    private static final Vector3f topGlassEndVec = new Vector3f(TOP_GLASS_X_END, TOP_GLASS_Y_END, TOP_GLASS_Z_END);

    public MixerRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(MixerTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        // mixers have 2 tanks
        LazyOptional<IFluidHandler> left = FluidHandlerHelper.capabilityOfSelf(tile, tile.orientedLeft());
        LazyOptional<IFluidHandler> right = FluidHandlerHelper.capabilityOfSelf(tile, tile.orientedRight());
        LazyOptional<IFluidHandler> bottom = FluidHandlerHelper.capabilityOfSelf(tile, Direction.DOWN);
        // weird nesting but okay
        left.ifPresent((l) ->
                right.ifPresent((r) ->
                        bottom.ifPresent((b) ->
                render(tile.facing(), tile, l.getTankCapacity(0), r.getTankCapacity(0), b.getTankCapacity(0),
                        l.getFluidInTank(0), r.getFluidInTank(0), b.getFluidInTank(0),
                        matrixStack, buffer, combinedLightIn, combinedOverlayIn)
        )));
    }

    private void render(Direction facing, MixerTile tile, int leftCap, int rightCap, int bottomCap,
            FluidStack leftGoo, FluidStack rightGoo, FluidStack bottomGoo,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn)
    {
        if (leftCap == 0 || rightCap == 0 || bottomCap == 0) {
            // divide by zeros are bad :|
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(RenderHelper.GOO_CUBE);
        renderTankFluid(tile.getPos(), false, facing, leftCap, leftGoo, matrixStack, builder, combinedLightIn);
        renderTankFluid(tile.getPos(), true, facing, rightCap, rightGoo, matrixStack, builder, combinedLightIn);
        renderOutputFluid(tile.getPos(), facing, bottomGoo, matrixStack, builder, combinedLightIn);
        renderGlass(tile.getPos(), facing, matrixStack, builder, combinedLightIn);
    }

    private ResourceLocation glass_front = Resources.Glass.MIXER_FRONT;
    private void renderGlass(BlockPos pos, Direction facing, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn) {
        FluidCuboidHelper.renderGlass(Glass.MIXER_TOP, topGlassStartVec, topGlassEndVec, Direction.UP, facing, matrixStack, builder, combinedLightIn);
    }

    private void renderTankFluid(BlockPos pos, boolean isRight, Direction facing, int capacity, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidTankDimensionsTo(isRight, facing, goo.getAmount(), capacity);
        Vector3f from = fluidTankDimensionsFrom(isRight, facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo.getFluid(), matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, combinedLightIn, from, from.getY(), to, to.getY());
    }

    private void renderOutputFluid(BlockPos pos, Direction facing, FluidStack goo, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        Vector3f to = fluidOutputDimensionsTo(facing);
        Vector3f from = fluidOutputDimensionsFrom(facing);
        FluidCuboidHelper.renderScaledFluidCuboid(goo.getFluid(), matrixStack, builder, combinedLightIn, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
        HighlightingHelper.renderHighlightAsNeeded(goo.getFluid(), pos, matrixStack, builder, combinedLightIn, from, from.getY(), to, to.getY());
    }

    private Vector3f fluidOutputDimensionsTo(Direction facing)
    {
        // this is the total fill percentage of the container

        float toY = OUTPUT_Y_OFFSET + MAX_FLUID_HEIGHT_OUTPUT;
        float toX = endingOutputFromFaceX(facing);
        float toZ = endingOutputFromFaceZ(facing);
        return new Vector3f(toX, toY, toZ);
    }

    private Vector3f fluidOutputDimensionsFrom(Direction facing)
    {
        float fromY = OUTPUT_Y_OFFSET;
        float fromX = startingOutputFromFaceX(facing);
        float fromZ = startingOutputFromFaceZ(facing);

        return new Vector3f(fromX, fromY, fromZ);
    }

    private float startingOutputFromFaceX(Direction facing)
    {
        switch(facing) {
            case NORTH:
            case SOUTH:
            case EAST:
                return OUTPUT_X_START_OFFSET;
            case WEST:
                return OUTPUT_Z_START_OFFSET;
        }
        return 0f; // this is bad
    }

    private float endingOutputFromFaceX(Direction facing)
    {
        switch(facing) {
            case NORTH:
            case SOUTH:
                return OUTPUT_X_END_OFFSET;
            case EAST:
                return 16f - OUTPUT_Z_START_OFFSET;
            case WEST:
                return OUTPUT_Z_END_OFFSET;
        }
        return 16f; // this is bad
    }

    private float startingOutputFromFaceZ(Direction facing)
    {
        switch(facing) {
            case NORTH:
                return OUTPUT_Z_START_OFFSET;
            case EAST:
            case WEST:
            case SOUTH:
                return OUTPUT_X_START_OFFSET;
        }
        return 0f; // this is bad
    }

    private float endingOutputFromFaceZ(Direction facing)
    {
        switch(facing) {
            case NORTH:
            case EAST:
            case WEST:
                return OUTPUT_Z_END_OFFSET;
            case SOUTH:
                return 16f - OUTPUT_Z_START_OFFSET;
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
                    return RIGHT_INPUT_X_START_OFFSET;
                case EAST:
                    return RIGHT_INPUT_Z_START_OFFSET;
                case SOUTH:
                    return LEFT_INPUT_X_START_OFFSET;
                case WEST:
                    return LEFT_INPUT_Z_START_OFFSET;
            }
        } else {
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
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceX(boolean isRight, Direction facing)
    {
        if (isRight) {
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
        } else {
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
        }
        return 16f; // this is bad
    }

    private float startingInputFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
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
        } else {
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
        }
        return 0f; // this is bad
    }

    private float endingInputFromSideAndFaceZ(boolean isRight, Direction facing)
    {
        if (isRight) {
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
        } else {
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
        }
        return 16f; // this is bad
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.
    private static final float ARBITRARY_GOO_STACK_HEIGHT_MINIMUM = 0.01f;

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.MIXER_TILE.get(), MixerRenderer::new);
    }
}
