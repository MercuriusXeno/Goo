package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BlobHitContext {
	private final World world;
	private final IFluidHandler fluidHandler;
	private final LivingEntity entityHit;
	private final Fluid fluid;
	private final GooBlob blob;
	private String interactionKey;
	private final LivingEntity owner;

	public BlobHitContext(LivingEntity entityHit, LivingEntity owner, GooBlob gooBlob, Fluid fluid) {
		this.world = entityHit.world;
		//noinspection OptionalGetWithoutIsPresent
		this.fluidHandler = gooBlob.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY).resolve().get();
		this.fluid = fluid;
		this.blob = gooBlob;
		this.entityHit = entityHit;
		this.owner = owner;
	}

	public BlobHitContext withKey(String interactionKey) {
		this.interactionKey = interactionKey;
		return this;
	}

	public World world()
	{
		return this.world;
	}

	public boolean isRemote()
	{
		return this.world.isRemote();
	}

	public IFluidHandler fluidHandler()
	{
		return this.fluidHandler;
	}

	public Fluid fluid() { return this.fluid; }

	public String interactionKey() { return this.interactionKey; }

	public GooBlob blob()
	{
		return this.blob;
	}

	public LivingEntity victim() { return this.entityHit; }

	public DamageSource mobDamage() {
		return DamageSource.causeMobDamage(owner);
	}

	public DamageSource damageSource() {
		if (owner == null) {
			return DamageSource.GENERIC;
		}
		if (owner instanceof PlayerEntity) {
			return DamageSource.causePlayerDamage((PlayerEntity)owner);
		}
		return mobDamage();
	}

	public void damageVictim(float v) {
		victim().attackEntityFrom(damageSource(), v);
	}

	public void knockback(float v) {
		victim().applyKnockback(v, blob().getMotion().x, blob().getMotion().z);
	}

	public void healVictim(float v) {
		victim().heal(v);
	}

	public void spawnScatteredFloatingParticles(BasicParticleType pType, int i) {
		for (int p = 0; p < i; p++) {
			double dx = world().rand.nextDouble() - 0.5d;
			double dy = world().rand.nextDouble() - 0.5d;
			double dz = world().rand.nextDouble() - 0.5d;
			Vector3d pos = blob().getPositionVec().add(dx, dy, dz);
			world().addParticle(pType, pos.x, pos.y, pos.z, 0d, 0.12d, 0d);
		}
	}

	public void applyEffect(Effect pEffect, int duration, int amplitude) {
		victim().addPotionEffect(new EffectInstance(pEffect, duration, amplitude));
	}
}
