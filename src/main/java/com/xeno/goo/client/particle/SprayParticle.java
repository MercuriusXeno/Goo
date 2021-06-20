package com.xeno.goo.client.particle;


import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;

import java.util.Random;

public class SprayParticle extends SpriteTexturedParticle {
    private final Fluid fluid;
    private final boolean isChromatic;
    protected boolean brightnessThingy;
    public float initScale = 0;
    public float initAlpha = 0;
    private final float initialParticleGravity = -0.25f;
    public SprayParticle(ClientWorld worldIn, double x, double y, double z, Fluid f, float alpha, double xSpeed, double ySpeed, double zSpeed) {
        super(worldIn, x, y, z,xSpeed, ySpeed, zSpeed);
        this.motionX = xSpeed;
        this.motionY = ySpeed;
        this.motionZ = zSpeed;
        this.particleGravity = initialParticleGravity; // floats
        this.fluid = f;
        this.initScale = 0.25f;
        this.particleScale = 0.25f;
        this.initAlpha = alpha;
        this.particleAlpha = alpha;
        this.maxAge = 40;
        if (f.getFluid().equals(Registry.ENERGETIC_GOO) || f.getFluid().equals(Registry.MOLTEN_GOO)) {
            this.brightnessThingy = true;
        }
        this.isChromatic = f.equals(Registry.CHROMATIC_GOO.get());
        this.setColor();
    }

    @Override
    public IParticleRenderType getRenderType() {
        return RenderHelper.VAPOR_RENDER;
    }

    public static class SprayGooFactory implements IParticleFactory<BasicParticleType> {
        private static final Random rand = new Random();
        protected final IAnimatedSprite spriteSet;
        protected final Fluid fluid;

        public SprayGooFactory(IAnimatedSprite animSprite, Fluid f) {
            this.spriteSet = animSprite;
            this.fluid = f;
        }

        public Particle makeParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            float alpha = rand.nextFloat() / 10f + 0.05f;
            SprayParticle vaporParticle = new SprayParticle(worldIn, x, y, z, this.fluid, alpha, xSpeed, ySpeed, zSpeed);
            vaporParticle.selectSpriteRandomly(this.spriteSet);
            return vaporParticle;
        }
    }

    private void setColor()
    {
        int tempColor = ParticleColorHelper.getColor(this.fluid, this.isChromatic);
        this.setColor(ParticleColorHelper.red(tempColor),
                ParticleColorHelper.green(tempColor),
                ParticleColorHelper.blue(tempColor));
    }

    private void updateChromatic() {
        int chromaticInt = FluidCuboidHelper.colorizeChromaticGoo();
        this.setColor(ParticleColorHelper.red(chromaticInt),
                ParticleColorHelper.green(chromaticInt),
                ParticleColorHelper.blue(chromaticInt));
    }


    @Override
    public void tick(){

        if (this.world.rand.nextInt(2) == 0){
            this.age++;
        }
        float lifeCoeff = (float)this.age/(float)this.maxAge;
        this.particleScale = initScale - (initScale * lifeCoeff * 0.5f);
        this.particleAlpha = initAlpha * (1.0f - lifeCoeff);
        // floats during its first half of life and then the gravity decays to a standstill
        this.particleGravity = initialParticleGravity * (1f - lifeCoeff);
        this.motionX *= 0.90d;
        this.motionZ *= 0.90d;
        this.motionY *= 0.90d;
        if (this.isChromatic) {
            updateChromatic();
        }
        // particleAngle += 1.0f;
        super.tick();
    }

    @Override
    public boolean isAlive() {
        return this.age < this.maxAge;
    }
}
