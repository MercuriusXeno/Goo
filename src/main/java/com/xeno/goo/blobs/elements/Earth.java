package com.xeno.goo.blobs.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

public class Earth extends AbstractElement {

	private static final int PARTICLES_MAX = 120;

	public Earth(Supplier<AbstractGooEffect> effectSupplier, int debuffDuration, int debuffMaxStacks) {

		super(effectSupplier, debuffDuration, debuffMaxStacks);
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		super.hitEntity(c);
	}

	@Override
	public void tryMaxStackEffect(WeaponizedBlobHitContext c) {
		// "shatter" the mob
		if (c.entityHit().level.isClientSide) {
			var ab = c.entityHit().getBoundingBox();
			var xa = ab.minX;
			var xz = ab.maxX;
			var ya = ab.minY;
			var yz = ab.maxY;
			var za = ab.minZ;
			var zz = ab.maxZ;
			var x = c.entityHit().level.random.doubles(xa, xz).iterator();
			var y = c.entityHit().level.random.doubles(ya, yz).iterator();
			var z = c.entityHit().level.random.doubles(za, zz).iterator();
			for(var i = 0; i < PARTICLES_MAX; i++) {
				c.entityHit().level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()),
						x.nextDouble(), y.nextDouble(), z.nextDouble(), 0d, 0d, 0d);
			}
		} else {
			c.entityHit().level.addFreshEntity(new ExperienceOrb(c.entityHit().level, c.entityHit().getX(), c.entityHit().getY(), c.entityHit().getZ(), 6));
			c.entityHit().remove(RemovalReason.DISCARDED);
		}
	}
}
