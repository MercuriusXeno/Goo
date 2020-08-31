package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.GooPump;
import com.xeno.goo.blocks.PumpRenderMode;
import com.xeno.goo.client.models.FluidCuboid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockModelRenderer;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.ChestTileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.minecraft.util.Direction.UP;

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
                FluidCuboidHelper.renderCuboid(matrixStack, buffer.getBuffer(RenderHelper.GOO_BLOCK), cuboid, still, flowing,
                        fillFromVector(intensity, face), fillToVector(intensity, face),
                        0xffffffff, combinedLightIn, false);
            }
        }

        renderActuator(tile, matrixStack, buffer, combinedLightIn, combinedOverlayIn);
    }

    private void renderActuator(GooPumpTile tile, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int combinedLightIn, int combinedOverlayIn) {
        final BlockState dynamicState = tile.getBlockState()
                .with(BlockStateProperties.FACING, tile.facing())
                .with(GooPump.RENDER, PumpRenderMode.DYNAMIC);
        // translate the position of the actuator sleeve as a function of animation time on a sine wave
        float heightOffset = MathHelper.sin(GooPumpTile.PROGRESS_PER_FRAME * tile.animationFrames()) * 0.25f;
        Vector3f positionOffset = tile.facing().toVector3f();
        positionOffset.mul(heightOffset);
        matrixStackIn.translate(positionOffset.getX(), positionOffset.getY(), positionOffset.getZ());
        // matrixStackIn.translate(0.5f, 0.5f, 0.5f);
        // matrixStackIn.rotate(tile.facing().getRotation());
        // matrixStackIn.translate(-0.5f, -0.5f, -0.5f);
        blockRenderer.renderBlock(dynamicState, matrixStackIn, bufferIn, combinedLightIn, combinedOverlayIn, EmptyModelData.INSTANCE);
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
                results.put(UP, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 0));
                break;
            case NORTH:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 0));
                results.put(UP, new FluidCuboid.FluidFace(true, 180));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(false, 0));
                break;
            case EAST:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 90));
                results.put(UP, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 90));
                break;
            case SOUTH:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 180));
                results.put(UP, new FluidCuboid.FluidFace(true, 0));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(false, 0));
                break;
            case WEST:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(true, 270));
                results.put(UP, new FluidCuboid.FluidFace(true, 270));
                results.put(Direction.EAST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.WEST, new FluidCuboid.FluidFace(false, 0));
                results.put(Direction.NORTH, new FluidCuboid.FluidFace(true, 90));
                results.put(Direction.SOUTH, new FluidCuboid.FluidFace(true, 270));
                break;
            case UP:
                results.put(Direction.DOWN, new FluidCuboid.FluidFace(false, 0));
                results.put(UP, new FluidCuboid.FluidFace(false, 0));
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
