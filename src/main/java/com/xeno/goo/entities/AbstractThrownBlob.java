package com.xeno.goo.entities;

import com.xeno.goo.GooMod;
import com.xeno.goo.blobs.GooElement;
import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public abstract class AbstractThrownBlob extends ThrowableItemProjectile {
	public AbstractThrownBlob(EntityType<? extends AbstractThrownBlob> entityType, Level level) {
		super(entityType, level);
	}

	public AbstractThrownBlob(EntityType<? extends AbstractThrownBlob> type, Level level, LivingEntity sender) {
		super(type, sender, level);
	}

	public AbstractThrownBlob(EntityType<? extends AbstractThrownBlob> type, Level level, double x, double y, double z) {
		super(type, x, y, z, level);
	}

	protected abstract Item getDefaultItem();
	protected abstract GooElement getElement();

	private ParticleOptions getParticle() {
		ItemStack itemstack = this.getItemRaw();
		return (itemstack.isEmpty() ? ParticleTypes.ITEM_SLIME : new ItemParticleOption(ParticleTypes.ITEM, itemstack));
	}

	private void doParticles() {
		ParticleOptions particleoptions = this.getParticle();

		for(int i = 0; i < 8; ++i) {
			this.level.addParticle(particleoptions, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
		}
	}

	@Override
	protected void onHitEntity(EntityHitResult hitResult) {
		if (!this.level.isClientSide) {
			if (hitResult.getEntity() instanceof LivingEntity livingEntity) {
				WeaponizedBlobHitContext context;
				if (this.getOwner() instanceof LivingEntity livingOwner) {
					context = new WeaponizedBlobHitContext(livingEntity, livingOwner, getElement());
				} else {
					context = new WeaponizedBlobHitContext(livingEntity, null, getElement());
				}
				getElement().getWeaponizedEffect().resolve(context);

				doParticles();
				this.remove(RemovalReason.DISCARDED);
			}
		} else {
			GooMod.LOGGER.debug("Unclear what's happening here but it's crap");
		}
		super.onHitEntity(hitResult);
	}

	@Override
	protected void onInsideBlock(BlockState pState) {
		//if (!this.level.isClientSide) {
		if (!pState.isAir()) {
			this.discard();
		}
		//}
		super.onInsideBlock(pState);
	}
}
