package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;

public class GooEntityRenderer extends EntityRenderer<GooEntity>
{
    protected GooEntityRenderer(EntityRendererManager renderManager)
    {
        super(renderManager);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return entity.getFluidInTank(0).getFluid().getAttributes().getStillTexture();
    }
    @Override
    public void render(GooEntity entity, float entityYaw, float partialTicks, MatrixStack stack, IRenderTypeBuffer bufferType, int light)
    {

    }
}
