package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.FluidCuboid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooPumpTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GooPumpRenderer extends TileEntityRenderer<GooPumpTile> {
    private static final float FLUID_VERTICAL_OFFSET = 0.0575f; // this offset puts it slightly below/above the 1px line to seal up an ugly seam
    private static final float FLUID_HORIZONTAL_OFFSET = 0.325f;
    private static final float FROM_VERTICAL = (FLUID_VERTICAL_OFFSET);
    private static final float TO_VERTICAL = (1 - FLUID_VERTICAL_OFFSET);

    public GooPumpRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }


    @Override
    public void render(GooPumpTile tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        if (tile.verticalFillFluid().isEmpty()) {
            return;
        }
        IVertexBuilder builder = buffer.getBuffer(RenderType.getTranslucent());
        renderActuator(tile.animationFrames(), tile.facing(), matrixStack, builder, combinedLightIn);

        Direction face = tile.facing();
        float intensity = tile.verticalFillIntensity();
        FluidStack fluid = tile.verticalFillFluid();
        TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getStillTexture(fluid));
        TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(fluid.getFluid().getAttributes().getFlowingTexture(fluid));

        FluidCuboid cuboid = new FluidCuboid(fillFromVector(intensity, face), fillToVector(intensity, face),
                flowingFacesByDirection(face));
        if (tile.isVerticallyFilled()) {
            // FluidCuboidHelper.renderDirectionalScaledFluidCuboid(tile.verticalFillFluid(), matrixStack, builder, combinedLightIn, verticalFillFrom.getX(), verticalFillFrom.getY(), verticalFillFrom.getZ(), verticalFillTo.getX(), verticalFillTo.getY(), verticalFillTo.getZ(), tile.facing());
            FluidCuboidHelper.renderCuboid(matrixStack, buffer.getBuffer(RenderHelper.GOO_BLOCK), cuboid, still, flowing,
                    // scaledFillFromVector(intensity, face), scaledFillToVector(intensity, face),
                    fillFromVector(intensity, face), fillToVector(intensity, face),
                    0xffffffff, combinedLightIn, false);
        }
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

    // render the ring around the glass stem
    private void renderActuator(int animationFrames, Direction d, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn)
    {
        ResourceLocation sideTexture = new ResourceLocation(GooMod.MOD_ID, "blocks/pump_actuator_side");
        ResourceLocation topTexture = new ResourceLocation(GooMod.MOD_ID, "blocks/pump_actuator_top");
        putTexturedQuad(builder, matrixStack.getLast().getMatrix(), sideTexture, topTexture, d, 0xff, 0xff, 0xff, 0xff, combinedLightIn, combinedLightIn);
    }

    private void putTexturedQuad(IVertexBuilder builder, Matrix4f matrix, ResourceLocation sideTexture, ResourceLocation topTexture, Direction d, int i, int i1, int i2, int i3, int combinedLightIn, int combinedLightIn1)
    {
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
