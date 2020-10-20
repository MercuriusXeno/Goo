package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.blocks.GooPump;
import com.xeno.goo.client.models.FluidCuboid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;

public class GooPumpRenderer extends TileEntityRenderer<GooPumpTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.0575f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_HORIZONTAL_OFFSET = 0.325f;
    private static final float FROM_VERTICAL = (FLUID_VERTICAL_OFFSET);
    private static final float TO_VERTICAL = (1 - FLUID_VERTICAL_OFFSET);

    public GooPumpRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    private final BlockRendererDispatcher blockRenderer = Minecraft.getInstance().getBlockRendererDispatcher();

    @Override
    public void render(GooPumpTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        if (!tile.verticalFillFluid().isEmpty()) {
            Direction face = tile.facing();
            float intensity = tile.verticalFillIntensity();
            FluidStack fluid = tile.verticalFillFluid();
            TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getStillTexture(fluid));
            TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getFlowingTexture(fluid));

            FluidCuboid cuboid = new FluidCuboid(fillFromVector(intensity, face), fillToVector(intensity, face),
                    flowingFacesByDirection(face));
            if (tile.isVerticallyFilled()) {
                FluidCuboidHelper.renderCuboid(matrixStack, buffer.getBuffer(GooRenderHelper.GOO_CUBE), cuboid, still, flowing,
                        fillFromVector(intensity, face), fillToVector(intensity, face),
                        0xffffffff, combinedLightIn, false);
            }
        }

        renderActuator(tile, matrixStack, buffer, combinedLightIn, combinedOverlayIn, partialTicks);

        renderItem(tile, matrixStack, buffer, combinedLightIn, combinedOverlayIn, partialTicks);
    }

    private void renderItem(GooPumpTile tile, MatrixStack matrices, IRenderTypeBuffer buffer, int light, int overlay, float partialTicks)
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

        // ItemFrameRenderer
        ItemStack item = tile.getDisplayedItem();
        if (!item.isEmpty()) {

            Direction flow = tile.facing();

            Map<Direction, Vector3f> offsets = offsetVectors.get(flow);
            // Map<Direction, Vector3i> rotations = rotationVectors.get(flow);
            for (Direction d : offsets.keySet()) {
                matrices.push();

                Vector3f vecOffset = offsets.get(d);
                matrices.translate(vecOffset.getX(), vecOffset.getY(), vecOffset.getZ());

                matrices.translate(0.5f, 0.5f, 0.5f);
                // Vector3i vecRotate = rotations.get(d);
//                matrices.rotate(Vector3f.YP.rotationDegrees(vecRotate.getY()));
//                matrices.rotate(Vector3f.XP.rotationDegrees(vecRotate.getX()));
//                matrices.rotate(Vector3f.ZP.rotationDegrees(vecRotate.getZ()));

                // try a different rotation strat
                // the only exception here is down flow direction is special, invert it. (Make all vertical up)
                matrices.rotate(flow.getAxis().isVertical() ? Direction.UP.getRotation() : flow.getRotation());
                matrices.rotate(Vector3f.YP.rotationDegrees(180f - (float)(d.getHorizontalIndex() * 90)));

                // scale
                Vector3f scaleVec = new Vector3f(0.375F, 0.375F, 0.001f);
                MatrixStack.Entry last = matrices.getLast();
                last.getMatrix().mul(Matrix4f.makeScale(scaleVec.getX(), scaleVec.getY(), scaleVec.getZ()));

                Minecraft.getInstance().getItemRenderer().renderItem(item, ItemCameraTransforms.TransformType.FIXED, light, overlay, matrices, buffer);
                matrices.pop();
            }
        }
    }

    // note these are "imaginary" directions; the actual faces are going to include UP/DOWN
    // depending on flow orientation, but the purpose of these rotations is to rotate "about the flow axis"
    private static final Map<Direction, Map<Direction, Vector3f>> offsetVectors = new HashMap<>();
    static {
        for(Direction flow : Direction.values()) {
            Map<Direction, Vector3f> passResult = new HashMap<>();
            switch (flow) {
                case UP:
                    passResult.put(Direction.NORTH, new Vector3f(0f, -0.25f, -0.5001f));
                    passResult.put(Direction.SOUTH, new Vector3f(0f, -0.25f, 0.5001f));
                    passResult.put(Direction.EAST, new Vector3f(0.5001f, -0.25f, 0f));
                    passResult.put(Direction.WEST, new Vector3f(-0.5001f, -0.25f, 0f));
                    break;
                case DOWN:
                    passResult.put(Direction.NORTH, new Vector3f(0f, 0.25f, -0.5001f));
                    passResult.put(Direction.SOUTH, new Vector3f(0f, 0.25f, 0.5001f));
                    passResult.put(Direction.EAST, new Vector3f(0.5001f, 0.25f, 0f));
                    passResult.put(Direction.WEST, new Vector3f(-0.5001f, 0.25f, 0f));
                    break;
                case SOUTH:
                    passResult.put(Direction.NORTH, new Vector3f(0f, 0.5001f, -0.25f));
                    passResult.put(Direction.SOUTH, new Vector3f(0f, -0.5001f, -0.25f));
                    passResult.put(Direction.EAST, new Vector3f(0.5001f, 0f, -0.25f));
                    passResult.put(Direction.WEST, new Vector3f(-0.5001f, 0f, -0.25f));
                    break;
                case NORTH:
                    passResult.put(Direction.NORTH, new Vector3f(0f, 0.5001f, 0.25f));
                    passResult.put(Direction.SOUTH, new Vector3f(0f, -0.5001f, 0.25f));
                    passResult.put(Direction.EAST, new Vector3f(-0.5001f, 0f, 0.25f));
                    passResult.put(Direction.WEST, new Vector3f(0.5001f, 0f, 0.25f));
                    break;
                case WEST:
                    passResult.put(Direction.NORTH, new Vector3f(0.25f, 0.5001f, 0f));
                    passResult.put(Direction.SOUTH, new Vector3f(0.25f, -0.5001f, 0f));
                    passResult.put(Direction.EAST, new Vector3f(0.25f, 0f, 0.5001f));
                    passResult.put(Direction.WEST, new Vector3f(0.25f, 0f, -0.5001f));
                    break;
                case EAST:
                    passResult.put(Direction.NORTH, new Vector3f(-0.25f, 0.5001f, 0f));
                    passResult.put(Direction.SOUTH, new Vector3f(-0.25f, -0.5001f, 0f));
                    passResult.put(Direction.EAST, new Vector3f(-0.25f, 0f, -0.5001f));
                    passResult.put(Direction.WEST, new Vector3f(-0.25f, 0f, 0.5001f));
                    break;
            }
            offsetVectors.put(flow, passResult);
        }
    }


    private static final Map<Direction, Map<Direction, Vector3i>> rotationVectors = new HashMap<>();
    static {
        for(Direction flow : Direction.values()) {
            Map<Direction, Vector3i> passResult = new HashMap<>();
            switch (flow) {
                case UP:
                case DOWN:
                    passResult.put(Direction.NORTH, new Vector3i(0, 90, 0));
                    passResult.put(Direction.SOUTH, new Vector3i(0, 270, 0));
                    passResult.put(Direction.EAST, new Vector3i(0, 0, 0));
                    passResult.put(Direction.WEST, new Vector3i(0, 180, 0));
                    break;
                case SOUTH:
                    passResult.put(Direction.DOWN, new Vector3i(270, 90, 90));
                    passResult.put(Direction.UP, new Vector3i(270, 90, 90));
                    passResult.put(Direction.EAST, new Vector3i(0, 90, 90));
                    passResult.put(Direction.WEST, new Vector3i(0, 90, 90));
                    break;
                case NORTH:
                    passResult.put(Direction.DOWN, new Vector3i(90, 90, 270));
                    passResult.put(Direction.UP, new Vector3i(90, 90, 270));
                    passResult.put(Direction.EAST, new Vector3i(0, 90, 270));
                    passResult.put(Direction.WEST, new Vector3i(0, 90, 270));
                    break;
                case WEST:
                    passResult.put(Direction.DOWN, new Vector3i(90, 0, 90));
                    passResult.put(Direction.UP, new Vector3i(90, 0, 90));
                    passResult.put(Direction.NORTH, new Vector3i(0, 0, 90));
                    passResult.put(Direction.SOUTH, new Vector3i(0, 0, 90));
                    break;
                case EAST:
                    passResult.put(Direction.DOWN, new Vector3i(270, 0, 270));
                    passResult.put(Direction.UP, new Vector3i(270, 0, 270));
                    passResult.put(Direction.NORTH, new Vector3i(0, 0, 270));
                    passResult.put(Direction.SOUTH, new Vector3i(0, 0, 270));
                    break;
            }
            rotationVectors.put(flow, passResult);
        }
    }

    private void renderActuator(GooPumpTile tile, MatrixStack matrices, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn, float partialTicks) {
        matrices.push();
        final BlockState dynamicState = tile.getBlockState()
                .with(BlockStateProperties.FACING, tile.facing())
                .with(GooPump.RENDER, PumpRenderMode.DYNAMIC);
        // translate the position of the actuator sleeve as a function of animation time on a sine wave
        // float heightOffset = (float)Math.min(0.95, smoothStep(0, 1, MathHelper.sin(GooPumpTile.PROGRESS_PER_FRAME * (tile.animationFrames() + partialTicks)))) * 0.25f;
        float frames = tile.animationFrames();
        if (frames > 0 && frames < 20) { frames -= partialTicks; }
        if (frames < 0) { frames = 0; }
        float heightOffset = (float)Math.min(0.95, smoothStep(0, 1, MathHelper.sin(GooPumpTile.PROGRESS_PER_FRAME * frames))) * 0.25f;
        Vector3f positionOffset = tile.facing().toVector3f();
        positionOffset.mul(heightOffset);
        matrices.translate(positionOffset.getX(), positionOffset.getY(), positionOffset.getZ());
        blockRenderer.renderBlock(dynamicState, matrices, bufferIn, combinedLightIn, combinedOverlayIn, EmptyModelData.INSTANCE);
        matrices.pop();
    }

    public static double smoothStep(double start, double end, double amount) {
        amount = MathHelper.clamp(amount, 0, 1);
        amount = MathHelper.clamp((amount - start) / (end - start), 0, 1);
        return amount * amount * (3 - 2 * amount);
    }

    public static final Map<Direction, Map<Direction, FluidCuboid.FluidFace>> cachedFaces;
    static {
        cachedFaces = flowingFacesInAllDirections();
    }

    private static Map<Direction, Map<Direction, FluidCuboid.FluidFace>> flowingFacesInAllDirections()
    {
        Map<Direction, Map<Direction, FluidCuboid.FluidFace>> results = new HashMap<>();
        for(Direction d : Direction.values()) {
            results.put(d, getFacesForFlowingDirection(d));
        }
        return results;
    }

    private static Map<Direction, FluidCuboid.FluidFace> getFacesForFlowingDirection(Direction d)
    {
        Map<Direction, FluidCuboid.FluidFace> results = new HashMap<>();

        switch (d) {
            case DOWN:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.UP, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 0));
                break;
            case NORTH:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.UP, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(false, 0));
                break;
            case EAST:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.UP, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 90));
                break;
            case SOUTH:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.UP, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(false, 0));
                break;
            case WEST:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.UP, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 270));
                break;
            case UP:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.UP, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 180));
                break;
        }
        return results;
    }

    private Map<Direction, FluidCuboid.FluidFace> flowingFacesByDirection(Direction face)
    {
        return cachedFaces.get(face);
    }

    // vertical fill graphics scale width to the intensity of the fill which decays after a short time
    private static final float lateralOffset(float intensity) {
        return (1f / 2f) - (FLUID_HORIZONTAL_OFFSET * intensity / 2f);
    };

    private static final float lateralFrom(float intensity) { return lateralOffset(intensity); }
    private static final float lateralTo(float intensity) { return  1f - lateralFrom(intensity); }
    private static final Vector3f fillFromVector(float intensity, Direction d) {
        switch (d) {
            case EAST:
            case WEST:
                return new Vector3f(FROM_VERTICAL, lateralFrom(intensity), lateralFrom(intensity));
            case NORTH:
            case SOUTH:
                return new Vector3f(lateralFrom(intensity), lateralFrom(intensity), FROM_VERTICAL);
            case DOWN:
            case UP:
                return new Vector3f(lateralFrom(intensity), FROM_VERTICAL, lateralFrom(intensity));
        }
        // default
        return new Vector3f(lateralFrom(intensity), FROM_VERTICAL, lateralFrom(intensity));
    }

    private static final Vector3f fillToVector(float intensity, Direction d) {
        switch (d) {
            case EAST:
            case WEST:
                return new Vector3f(TO_VERTICAL, lateralTo(intensity), lateralTo(intensity));
            case NORTH:
            case SOUTH:
                return new Vector3f(lateralTo(intensity), lateralTo(intensity), TO_VERTICAL);
            case DOWN:
            case UP:
                return new Vector3f(lateralTo(intensity), TO_VERTICAL, lateralTo(intensity));
        }
        // default
        return new Vector3f(lateralTo(intensity), TO_VERTICAL, lateralTo(intensity));
    }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_PUMP_TILE.get(), GooPumpRenderer::new);
    }
}
