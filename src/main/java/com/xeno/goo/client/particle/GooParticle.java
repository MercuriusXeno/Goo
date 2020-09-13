package com.xeno.goo.client.particle;

import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.xeno.goo.client.render.FluidCuboidHelper;
import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.world.ClientWorld;
public class GooParticle extends Particle
{
    protected GooParticle(ClientWorld world, double x, double y, double z)
    {
        super(world, x, y, z);
    }

    @Override
    public void renderParticle(IVertexBuilder buffer, ActiveRenderInfo renderInfo, float partialTicks)
    {
        // FluidCuboidHelper.renderScaledFluidCuboid();
    }

    @Override
    public IParticleRenderType getRenderType()
    {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
