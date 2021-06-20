package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.items.Vessel;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.overlay.RayTracing;
import com.xeno.goo.setup.Resources;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

public class HighlightingHelper
{
    private static int TRANSPARENCY_TIMER = 25;
    private static float TRANSPARENCY_TIMER_OVER_SINE_WAVE = (float)Math.PI / (float)TRANSPARENCY_TIMER;

    private static int COLORIZER_TIMER = 25;
    private static float COLORIZER_TIMER_OVER_SINE_WAVE = (float)Math.PI / (float)COLORIZER_TIMER;
    public static int getTransparencyFromWorldTime()
    {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }

        return (int)Math.floor(80 * (MathHelper.sin((Minecraft.getInstance().world.getDayTime() % TRANSPARENCY_TIMER) * TRANSPARENCY_TIMER_OVER_SINE_WAVE))) + 80;

    }

    public static int getColorFromWorldTime()
    {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }

        int c = 255 - (int)Math.floor(192 * MathHelper.sin((Minecraft.getInstance().world.getDayTime() % COLORIZER_TIMER) * COLORIZER_TIMER_OVER_SINE_WAVE));
        return c << 16 | c << 8 | c;
    }

    public static boolean isTargeted(Fluid goo, BlockPos pos)
    {
        if (Minecraft.getInstance().getRenderViewEntity() == null) {
            return false;
        }

        Entity e = Minecraft.getInstance().getRenderViewEntity();

        if (!needsHighlightForItemHeld(e)) {
            return false;
        }

        RayTracing.INSTANCE.fire();
        if (!RayTracing.INSTANCE.hasTarget()) {
            return false;
        }

        BlockRayTraceResult target = RayTracing.INSTANCE.blockTarget();
        if (target == null) {
            return false;
        }
        if (!target.getPos().equals(pos)) {
            return false;
        }
        World world = e.getEntityWorld();

        TileEntity t = world.getTileEntity(target.getPos());
        if (!(t instanceof GooContainerAbstraction)) {
            return false;
        }
        FluidStack targetGoo = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(target, RayTraceTargetSource.JUST_LOOKING);
        if (targetGoo.getFluid().equals(goo)) {
            return true;
        }
        return false;
    }

    public static boolean needsHighlightForItemHeld(Entity e)
    {
        if (e.isSneaking()) {
            return true;
        }

        // don't render highlights unless the player is
        // 1) a player lol
        // 2) sneaking or holding gauntlet/vessel
        if (!(e instanceof PlayerEntity)) {
            return false;
        }
        return needsHighlight(((PlayerEntity)e).getHeldItemOffhand()) ||
                needsHighlight(((PlayerEntity)e).getHeldItemMainhand());
    }

    private static boolean needsHighlight(ItemStack stack)
    {
        return stack.getItem() instanceof Vessel || stack.getItem() instanceof Gauntlet;
    }

    public static void renderHighlightAsNeeded(Fluid goo, BlockPos pos, MatrixStack matrixStack, IVertexBuilder builder, int combinedLightIn, Vector3f from, float fromY, Vector3f to, float toY) {
        if (goo.equals(Fluids.EMPTY)) {
            return;
        }
        if (HighlightingHelper.isTargeted(goo, pos)) {
            int transparency = HighlightingHelper.getTransparencyFromWorldTime();
            int overlayColor = HighlightingHelper.getColorFromWorldTime();
            int overlayColorizer = overlayColor | transparency << 24;
            ResourceLocation hoverFlowing = Resources.Flowing.OVERLAY;
            ResourceLocation hoverStill = Resources.Still.OVERLAY;
            TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverStill);
            TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverFlowing);
            FluidCuboidHelper.renderFluidCuboid(still, flowing, overlayColorizer, matrixStack, builder, combinedLightIn, (from.getX() / 16f) - 0.0001f, (fromY / 16f) - 0.0001f, (from.getZ() / 16f) - 0.0001f, (to.getX() / 16f) + 0.0001f, (toY / 16f) + 0.0001f, (to.getZ() / 16f) + 0.0001f);
        }
    }
}
