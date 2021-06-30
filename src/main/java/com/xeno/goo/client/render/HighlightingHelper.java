package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.client.models.Model3d;
import com.xeno.goo.client.models.Model3d.SpriteInfo;
import com.xeno.goo.client.render.RenderHelper.FluidType;
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

import java.util.HashMap;
import java.util.Map;

public class HighlightingHelper
{
    private static final int TRANSPARENCY_TIMER = 25;
    private static final float TRANSPARENCY_TIMER_OVER_SINE_WAVE = (float)Math.PI / (float)TRANSPARENCY_TIMER;

    private static final int COLORIZER_TIMER = 25;
    private static final float COLORIZER_TIMER_OVER_SINE_WAVE = (float)Math.PI / (float)COLORIZER_TIMER;

    private static final int HIGHLIGHT_FREQUENCY = 5;
    public static int getTransparencyFromWorldTime()
    {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }

        return (int)Math.floor(80 * (MathHelper.sin((Minecraft.getInstance().world.getGameTime() % TRANSPARENCY_TIMER) * TRANSPARENCY_TIMER_OVER_SINE_WAVE))) + 80;

    }

    public static int getColorFromWorldTime()
    {
        if (Minecraft.getInstance().world == null) {
            return 0;
        }

        int c = 255 - (int)Math.floor(192 * MathHelper.sin((Minecraft.getInstance().world.getGameTime() % COLORIZER_TIMER) * COLORIZER_TIMER_OVER_SINE_WAVE));
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

    public static void renderHighlightAsNeeded(Fluid goo, BlockPos pos, MatrixStack matrixStack, IVertexBuilder builder, int light, int overlay, Model3d model) {
        if (goo.equals(Fluids.EMPTY) || !HighlightingHelper.isTargeted(goo, pos)) {
            return;
        }
        int color = HighlightingHelper.getColorFromWorldTime() | HighlightingHelper.getTransparencyFromWorldTime() << 24;
        RenderHelper.renderCube(getHighlightModel(model), matrixStack, builder, color, light, overlay, false);
    }

    private static final ResourceLocation hoverFlowing = Resources.Flowing.OVERLAY;
    private static final ResourceLocation hoverStill = Resources.Still.OVERLAY;
    private static final TextureAtlasSprite still = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverStill);
    private static final TextureAtlasSprite flowing = Minecraft.getInstance().getAtlasSpriteGetter(PlayerContainer.LOCATION_BLOCKS_TEXTURE).apply(hoverFlowing);
    private static final SpriteInfo[] spriteCache = new SpriteInfo[] { new SpriteInfo(still, 16), new SpriteInfo(still, 16),
            new SpriteInfo(flowing, 16), new SpriteInfo(flowing, 16), new SpriteInfo(flowing, 16), new SpriteInfo(flowing, 16) };
    private static Model3d getHighlightModel(Model3d fluidModel) {
        Model3d model = new Model3d();
        model.setTextures(spriteCache[0], spriteCache[1], spriteCache[2], spriteCache[3], spriteCache[4], spriteCache[5]);

        if (still != null) {
            model.minX = fluidModel.minX - 0.0001f;
            model.minY = fluidModel.minY - 0.0001f;
            model.minZ = fluidModel.minZ - 0.0001f;

            model.maxX = fluidModel.maxX + 0.0001f;
            model.maxY = fluidModel.maxY + 0.0001f;
            model.maxZ = fluidModel.maxZ + 0.0001f;
        }
        return model;
    }
}
