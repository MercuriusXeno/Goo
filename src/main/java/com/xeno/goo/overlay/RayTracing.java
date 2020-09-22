package com.xeno.goo.overlay;
import com.google.common.collect.Lists;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class RayTracing {

    public static final RayTracing INSTANCE = new RayTracing();
    private RayTraceResult target = null;
    private Minecraft mc = Minecraft.getInstance();

    private RayTracing() {
    }

    public void fire() {
        if (mc.objectMouseOver != null && mc.objectMouseOver.getType() == RayTraceResult.Type.ENTITY) {
            this.target = mc.objectMouseOver;
            return;
        }

        Entity viewpoint = mc.getRenderViewEntity();
        if (viewpoint == null)
            return;

        this.target = this.rayTrace(viewpoint, mc.playerController.getBlockReachDistance(), 0);
    }

    public RayTraceResult rayTrace(Entity entity, double playerReach, float partialTicks) {
        Vector3d eyePosition = entity.getEyePosition(partialTicks);
        Vector3d lookVector = entity.getLook(partialTicks);
        Vector3d traceEnd = eyePosition.add(lookVector.x * playerReach, lookVector.y * playerReach, lookVector.z * playerReach);

        RayTraceContext.FluidMode fluidView = RayTraceContext.FluidMode.ANY;
        RayTraceContext context = new RayTraceContext(eyePosition, traceEnd, RayTraceContext.BlockMode.OUTLINE, fluidView, entity);
        return entity.getEntityWorld().rayTraceBlocks(context);
    }

    public BlockRayTraceResult blockTarget()
    {
        if (this.target instanceof BlockRayTraceResult) {
            return (BlockRayTraceResult)this.target;
        }

        return null;
    }

    public EntityRayTraceResult entityTarget()
    {
        if (this.target instanceof EntityRayTraceResult) {
            return (EntityRayTraceResult)this.target;
        }

        return null;
    }

    public boolean hasTarget()
    {
        return this.target != null;
    }
}