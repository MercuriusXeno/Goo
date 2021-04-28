package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
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


	public BlobHitContext(LivingEntity entityHit, World world, GooBlob entity, Fluid fluid) {
		this.world = world;
		//noinspection OptionalGetWithoutIsPresent
		this.fluidHandler = entity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY).resolve().get();
		this.fluid = fluid;
		this.blob = entity;
		this.entityHit = entityHit;
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
}
