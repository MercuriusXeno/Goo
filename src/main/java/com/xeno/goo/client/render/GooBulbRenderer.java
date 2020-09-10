package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.blocks.GooBulbAbstraction;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.setup.Resources;
import com.xeno.goo.tiles.*;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import java.util.List;

public class GooBulbRenderer extends TileEntityRenderer<GooBulbTileAbstraction> {
    private static final float FLUID_HORIZONTAL_OFFSET = 0.0005f;
    private static final float FROM_SCALED_VERTICAL = GooBulbTile.FLUID_VERTICAL_OFFSET * 16;
    private static final float TO_SCALED_VERTICAL = 16 - (GooBulbTile.FLUID_VERTICAL_MAX * 16);
    private static final float FROM_SCALED_HORIZONTAL = FLUID_HORIZONTAL_OFFSET * 16;
    private static final float TO_SCALED_HORIZONTAL = 16 - FROM_SCALED_HORIZONTAL;
    private static final Vector3f FROM_FALLBACK = new Vector3f(FROM_SCALED_HORIZONTAL, FROM_SCALED_VERTICAL, FROM_SCALED_HORIZONTAL);
    private static final Vector3f TO_FALLBACK = new Vector3f(TO_SCALED_HORIZONTAL, TO_SCALED_VERTICAL, TO_SCALED_HORIZONTAL);

    public GooBulbRenderer(TileEntityRendererDispatcher rendererDispatcherIn) {
        super(rendererDispatcherIn);
    }

    @Override
    public void render(GooBulbTileAbstraction tile, float partialTicks, MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn, int combinedOverlayIn) {
        IFluidHandler cap = FluidHandlerHelper.capability(tile, Direction.UP);
        render(tile.getPos(), cap.getTankCapacity(0), tile.getTotalGoo(), tile.goo(), tile.isVerticallyFilled(), tile.verticalFillFluid(), tile.verticalFillIntensity(),
                matrixStack, buffer, combinedLightIn);
    }

    // makes it so that a really small amount of goo still has a substantial enough bulb presence that you can see it.

    public static void render(BlockPos pos, int bulbCapacity, float totalGoo, List<FluidStack> gooList, boolean isVerticallyFilled, FluidStack verticalFillFluid, float verticalFillIntensity,
            MatrixStack matrixStack, IRenderTypeBuffer buffer, int combinedLightIn) {
        gooList.removeIf(FluidStack::isEmpty);
        IVertexBuilder builder = buffer.getBuffer(RenderType.getTranslucent());

        float yOffset = 0;

        // determine where to draw the fluid based on the model
        Vector3f from = FROM_FALLBACK, to = TO_FALLBACK;


        float minY = from.getY();
        float maxY = to.getY();
        float heightScale = maxY - minY;
        float highestToY = minY;
        for(FluidStack goo : gooList) {
            // this is the total fill of the goo in the tank of this particular goo, as a percentage
            float gooHeight = Math.max(GooBulbTile.ARBITRARY_GOO_STACK_HEIGHT_MINIMUM, goo.getAmount() / (float)bulbCapacity);
            float fromY, toY;
            fromY = minY + yOffset;
            toY = fromY + (gooHeight * heightScale);
            highestToY = toY;
            if (isTargeted(goo, pos)) {
                int transparency = getTransparencyFromWorldTime();
                int overlayColorizer = 0x00ffffff | transparency << 24;
                ResourceLocation hoverFlowing = Resources.Flowing.OVERLAY;
                ResourceLocation hoverStill = Resources.Still.OVERLAY;
                TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverStill);
                TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverFlowing);
                FluidCuboidHelper.renderFluidCuboid(still, flowing, overlayColorizer, matrixStack, builder, combinedLightIn, (from.getX() / 16f) - 0.0001f, (fromY / 16f) - 0.0001f, (from.getZ() / 16f) - 0.0001f, (to.getX() / 16f) + 0.0001f, (toY / 16f) + 0.0001f, (to.getZ() / 16f) + 0.0001f);
            }
            FluidCuboidHelper.renderScaledFluidCuboid(goo, matrixStack, builder, combinedLightIn, from.getX(), fromY, from.getZ(), to.getX(), toY, to.getZ());
            yOffset += (gooHeight * heightScale);
        }

        if (isVerticallyFilled) {
            Vector3f verticalFillFrom = verticalFillFromVector(verticalFillIntensity), verticalFillTo = verticalFillToVector(verticalFillIntensity);
            FluidCuboidHelper.renderScaledFluidCuboid(verticalFillFluid, matrixStack, builder, combinedLightIn, verticalFillFrom.getX(), highestToY, verticalFillFrom.getZ(), verticalFillTo.getX(), maxY, verticalFillTo.getZ());
        }
    }

    private static int TRANSPARENCY_TIMER = 25;
    private static float TIMER_OVER_SINE_WAVE = (float)Math.PI / (float)TRANSPARENCY_TIMER;
    private static int getTransparencyFromWorldTime()
    {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }

        return (int)Math.floor(80 * (MathHelper.sin((Minecraft.getInstance().world.getDayTime() % TRANSPARENCY_TIMER) * TIMER_OVER_SINE_WAVE)));
    }

    private static boolean isTargeted(FluidStack goo, BlockPos pos)
    {
        if (Minecraft.getInstance().getRenderViewEntity() == null) {
            return false;
        }

        Entity e = Minecraft.getInstance().getRenderViewEntity();

        RayTracing.INSTANCE.fire();
        if (!RayTracing.INSTANCE.hasTarget()) {
            return false;
        }

        BlockRayTraceResult target = RayTracing.INSTANCE.target();
        if (!target.getPos().equals(pos)) {
            return false;
        }
        ClientWorld world = (ClientWorld)e.getEntityWorld();

        BlockState state = world.getBlockState(target.getPos());
        if (!(state.getBlock() instanceof GooBulbAbstraction)) {
            return false;
        }
        TileEntity t = world.getTileEntity(target.getPos());
        if (!(t instanceof GooContainerAbstraction)) {
            return false;
        }
        FluidStack targetGoo = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(target);
        if (targetGoo.isFluidEqual(goo)) {
            return true;
        }
        return false;
    }

    // vertical fill graphics scale width to the intensity of the fill which decays after a short time
    private static final float FROM_VERTICAL_FILL_PORT_WIDTH_BASE = 0.125f;
    private static final float verticalFillHorizontalOffset(float intensity) {
        return (1f / 2f) - (FROM_VERTICAL_FILL_PORT_WIDTH_BASE * intensity / 2f);
    };
    private static final float verticalFillHorizontalFrom(float intensity) {return 16 * verticalFillHorizontalOffset(intensity); }
    private static final float verticalFillHorizontalTo(float intensity) { return  16 - verticalFillHorizontalFrom(intensity); }
    private static final Vector3f verticalFillFromVector(float intensity) { return new Vector3f(verticalFillHorizontalFrom(intensity), FROM_SCALED_VERTICAL, verticalFillHorizontalFrom(intensity)); }
    private static final Vector3f verticalFillToVector(float intensity) { return new Vector3f(verticalFillHorizontalTo(intensity), TO_SCALED_VERTICAL, verticalFillHorizontalTo(intensity)); }

    public static void register() {
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE.get(), GooBulbRenderer::new);
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE_MK2.get(), GooBulbRenderer::new);
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE_MK3.get(), GooBulbRenderer::new);
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE_MK4.get(), GooBulbRenderer::new);
        ClientRegistry.bindTileEntityRenderer(Registry.GOO_BULB_TILE_MK5.get(), GooBulbRenderer::new);
    }
}
