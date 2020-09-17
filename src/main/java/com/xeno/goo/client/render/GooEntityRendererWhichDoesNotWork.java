package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class GooEntityRendererWhichDoesNotWork extends EntityRenderer<GooEntity>
{
    protected GooEntityRendererWhichDoesNotWork(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO_ENTITY.get(), GooEntityRendererWhichDoesNotWork::new);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return entity.getFluidInTank(0).getFluid().getAttributes().getStillTexture();
    }

    @Override
    public void render(GooEntity entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer bufferType, int light)
    {
        stack.push();

//        Quaternion rot = GooRenderHelper.yawToQuat(entity.rotationYaw);
//        stack.rotate(rot);
//        stack.translate(entity.getPosX(), entity.getPosY(), entity.getPosZ());
        float cubicRadius = entity.cubicSize() * 8f;
        Vector3d from = entity.getPositionVec().add(-cubicRadius, -cubicRadius, -cubicRadius);
        Vector3d to = entity.getPositionVec().add(cubicRadius, cubicRadius, cubicRadius);
        FluidCuboidHelper.renderFluidCuboid(entity.goo, stack,
                bufferType.getBuffer(GooRenderHelper.GOO), light,
                (float)from.x, (float)from.y, (float)from.z, (float)to.x, (float)to.y, (float)to.z);
        stack.pop();
    }
}
