package com.xeno.goo.client.particle;


import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.GooRenderHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;

import java.util.Random;

public class VaporParticle  extends SpriteTexturedParticle {
    private final Fluid fluid;
    private final boolean isChromatic;
    protected boolean brightnessThingy;
    public float initScale = 0;
    public float initAlpha = 0;
    private final float initialParticleGravity = -0.25f;
    public VaporParticle(ClientWorld worldIn, double x, double y, double z, Fluid f, float alpha, float scale) {
        super(worldIn, x, y, z,0,0,0);
        this.particleGravity = initialParticleGravity; // floats
        this.fluid = f;
        this.initScale = scale;
        this.particleScale = scale;
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
        return GooRenderHelper.VAPOR_RENDER;
    }

    public static class VaporGooFactory implements IParticleFactory<BasicParticleType> {
        private static final Random rand = new Random();
        protected final IAnimatedSprite spriteSet;
        protected final Fluid fluid;

        public VaporGooFactory(IAnimatedSprite animSprite, Fluid f) {
            this.spriteSet = animSprite;
            this.fluid = f;
        }

        public Particle makeParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            float alpha = rand.nextFloat() / 10f + 0.05f;
            VaporParticle vaporParticle = new VaporParticle(worldIn, x, y, z, this.fluid, alpha, (float)zSpeed);
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
        this.motionX *= 0.2d;
        this.motionZ *= 0.2d;
        if (this.motionY > 0.02d) {
            this.motionY = 0.02d;
        }
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
