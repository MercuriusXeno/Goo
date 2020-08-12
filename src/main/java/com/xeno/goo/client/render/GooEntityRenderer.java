package com.xeno.goo.client.render;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

import java.util.Objects;

public class GooEntityRenderer extends EntityRenderer<GooEntity>
{
    public GooEntityRenderer(EntityRendererManager manager) {

        super(manager);
    }

    public static void register()
    {
        RenderingRegistry.registerEntityRenderingHandler(Registry.GOO.get(), GooEntityRenderer::new);
    }

    @Override
    public ResourceLocation getEntityTexture(GooEntity entity)
    {
        return entity.goo.getFluid().getAttributes().getStillTexture();
    }

    @Override
    public void render(GooEntity e, float yaw, float pTicks, MatrixStack m, IRenderTypeBuffer buf, int light)
    {
        super.render(e, yaw, pTicks, m, buf, light);
        // 1000 mB is a 10x10x10 cube, which means 1 block = 10 "units" wide.
        // Thus, take the cube root of the volume and divide by 10.
        if (e.goo == null || e.goo.isEmpty()) {
            return;
        }
        IVertexBuilder b = buf.getBuffer(RenderType.getEntityTranslucent(e.goo.getFluid().getAttributes().getStillTexture()));
        m.push();

        renderWeirdly(e, b, yaw, pTicks, m, buf, light);

        renderSanelyMaybe(e, b, yaw, pTicks, m, buf, light);

        m.pop();
    }

    private void renderSanelyMaybe(GooEntity e, IVertexBuilder b, float yaw, float pTicks, MatrixStack m, IRenderTypeBuffer buf, int light)
    {
        //m.translate(e.getPosX(), e.getPosY(), e.getPosZ());
        float halfSide = (float)Math.cbrt(e.goo.getAmount()) * 16f; // supposed to be 20f
        Vector3f from = new Vector3f(-halfSide, -halfSide, -halfSide);
        Vector3f to = new Vector3f(halfSide, halfSide, halfSide);
        // GooMod.debug("You should be seeing " + e.goo.getTranslationKey() + " right now at x " + e.getPosX() + " y " + e.getPosY() + " z " + e.getPosZ());
        FluidCuboidHelper.renderScaledFluidCuboid(e.goo, m, b, light, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }

    private void renderWeirdly(GooEntity e, IVertexBuilder b, float yaw, float pTicks, MatrixStack m, IRenderTypeBuffer buf, int light)
    {
        m.translate(e.getPosX(), e.getPosY(), e.getPosZ());
        float halfSide = (float)Math.cbrt(e.goo.getAmount()) * 16f; // supposed to be 20f
        Vector3f from = new Vector3f(-halfSide, -halfSide, -halfSide);
        Vector3f to = new Vector3f(halfSide, halfSide, halfSide);
        // GooMod.debug("You should be seeing " + e.goo.getTranslationKey() + " right now at x " + e.getPosX() + " y " + e.getPosY() + " z " + e.getPosZ());
        FluidCuboidHelper.renderScaledFluidCuboid(e.goo, m, b, light, from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ());
    }
}
