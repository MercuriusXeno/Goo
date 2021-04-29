package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.DamageSource;
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

	public void hurt(float v) {
		victim().attackEntityFrom(damageSource(), v);
	}
}
