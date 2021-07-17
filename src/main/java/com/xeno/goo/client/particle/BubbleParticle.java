package com.xeno.goo.client.particle;


import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.client.render.RenderHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.particle.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;

import java.util.Random;

public class BubbleParticle extends SpriteTexturedParticle {
    private final IAnimatedSprite spriteSet;
    private final Fluid fluid;
    private final boolean isChromatic;
    protected boolean brightnessThingy;
    public float initScale = 0;
    public float initAlpha = 0;
    private final float initialParticleGravity = -0.25f;
    public BubbleParticle(ClientWorld world, double x, double y, double z, Fluid f, IAnimatedSprite spriteSet) {
        super(world, x, y, z, 0D, 0D, 0D);
        this.spriteSet = spriteSet;
        setSize(0.02F, 0.02F);
        particleScale = 0.5F + (world.rand.nextFloat() - 0.5F) * 0.4F;
        motionX = 0D;
        motionY = 0D;
        motionZ = 0D;
        this.fluid = f;
        this.maxAge = 20;
        this.age = 0;
        if (f.getFluid().equals(Registry.ENERGETIC_GOO) || f.getFluid().equals(Registry.MOLTEN_GOO)) {
            this.brightnessThingy = true;
        }
        this.isChromatic = f.equals(Registry.CHROMATIC_GOO.get());
        this.setColor();
    }

    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class BubbleFactory implements IParticleFactory<BasicParticleType> {
        protected final IAnimatedSprite spriteSet;
        protected final Fluid fluid;

        public BubbleFactory(IAnimatedSprite animSprite, Fluid f) {
            this.spriteSet = animSprite;
            this.fluid = f;
        }

        public Particle makeParticle(BasicParticleType typeIn, ClientWorld worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            BubbleParticle bubble = new BubbleParticle(worldIn, x, y, z, this.fluid, spriteSet);
            bubble.setSprite(spriteSet.get(bubble.age, bubble.maxAge));
            return bubble;
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
    public void tick() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        // setAlphaF((float) age / (float) maxAge);
        if (isChromatic) {
            updateChromatic();
        }
        if (age++ >= maxAge) {
            setExpired();
        } else {
            selectSpriteWithAge(spriteSet);
        }
    }

    @Override
    public float getScale(float partialTicks) {
        return 0.1F * particleScale * (1.0F + (float) age / 20F);
    }

    private static int FRAMES_OF_EACH_POP_STAGE = 2;
    private static int POP_STAGES = 3;

    @Override
    public void selectSpriteWithAge(IAnimatedSprite animatedSprite) {
        int ageFromDeath = maxAge - age;
        if (ageFromDeath <= FRAMES_OF_EACH_POP_STAGE * POP_STAGES) {
            this.setSprite(animatedSprite.get(3 - (ageFromDeath / FRAMES_OF_EACH_POP_STAGE), POP_STAGES));
        } else {
            this.setSprite(animatedSprite.get(0, maxAge));
        }
    }

    @Override
    public boolean isAlive() {
        return this.age < this.maxAge;
    }
}
