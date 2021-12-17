package com.xeno.goo.interactions;

import com.xeno.goo.entities.HexController;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class BlobHitContext {
	private World world;
	private IFluidHandler fluidHandler;
	private LivingEntity entityHit;
	private Fluid fluid;
	private HexController blob;
	private String interactionKey;
	// optional if fired intelligently
	private LivingEntity owner;
	// optional for non-empty hit results with blocks
	private BlockRayTraceResult hitResult;
	private BlockPos blockPos;
	private Vector3d blockCenterVec;
	private Direction sideHit;
	private BlockState blockState;



	public BlobHitContext(LivingEntity entityHit, LivingEntity owner, HexController gooBlob, Fluid fluid) {
		this.world = entityHit.world;
		LazyOptional<IFluidHandler> cap = gooBlob.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
		if (cap.resolve().isPresent()) {
			this.fluidHandler = cap.resolve().get();
		} else {
			this.fluidHandler = null;
		}
		this.fluid = fluid;
		this.blob = gooBlob;
		this.entityHit = entityHit;
		this.owner = owner;
	}

	public BlobHitContext(BlockPos blockHitPos, HexController e) {

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

	public HexController blob()
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
		victim().applyKnockback(v, -blob().getMotion().x, -blob().getMotion().z);
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

	public void ricochet() {
		Vector3d victimCenter = victim().getBoundingBox().getCenter();

		Vector3d blobCenter = blob().getBoundingBox().getCenter();

		Vector3d bounceVector = victimCenter.subtract(blobCenter).normalize();

		blob().setMotion(blob().getMotion().mul(bounceVector));
	}

	public LivingEntity owner() {
		return owner;
	}

	public Vector3d victimCenterVec() {
		return victim().getBoundingBox().getCenter();
	}

	public BlockState blockState()
	{
		return this.blockState;
	}

	public BlockRayTraceResult hitResult()
	{
		return this.hitResult;
	}

	public BlockPos blockPos()
	{
		return this.blockPos;
	}

	public Block block()
	{
		return this.blockState.getBlock();
	}

	public boolean setBlockState(BlockState newState)
	{
		return this.world.setBlockState(this.blockPos, newState);
	}

	public FluidState fluidState()
	{
		return this.blockState.getFluidState();
	}

	public Vector3d blockCenterVec()
	{
		return this.blockCenterVec;
	}

	public Direction sideHit()
	{
		return this.sideHit;
	}

	public boolean isBlockAboveAir() {
		return world.getBlockState(blockPos().offset(Direction.UP)).isAir(world(), blockPos());
	}

	public boolean setBlockStateAbove(BlockState newState) {
		return this.world.setBlockState(this.blockPos.offset(Direction.UP), newState);
	}

	public boolean isBlockBelowAir() {
		return world.getBlockState(blockPos().offset(Direction.DOWN)).isAir(world(), blockPos());
	}

	public boolean setBlockStateBelow(BlockState newState) {
		return this.world.setBlockState(this.blockPos.offset(Direction.DOWN), newState);
	}

	public boolean isBlock(Block... blocks) {
		for(Block block : blocks) {
			if (block().equals(block)) {
				return true;
			}
		}
		return false;
	}
}
