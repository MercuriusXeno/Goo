package com.xeno.goo.effects;

import com.xeno.goo.shrink.api.IShrinkProvider;
import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import net.minecraft.util.math.EntityRayTraceResult;

public class EggedEffect extends Effect {
	public static EggedEffect instance = new EggedEffect();

	protected EggedEffect() {
		super(EffectType.NEUTRAL, 0xffffffff);
	}

	@Override
	public void performEffect(LivingEntity e, int amplifier) {
		e.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(s ->
			{
				if (!s.isShrunk()) {
					s.shrink(e);
				} else {
					double entityVolume = volumeOfEntity(s);
					if (entityVolume > VOLUME_OF_EGG) {
						s.setScale(s.scale() - 0.025f);
					} else {
						spawnEggForEntity(e);
						e.remove();
					}
				}
			}
		);
	}

	private double volumeOfEntity(IShrinkProvider e) {
		return e.defaultEntitySize().width * e.defaultEntitySize().width * e.defaultEntitySize().height	* e.scale();
	}

	// this is ballparked/made up, but bear with me.
	private float RADIUS_OF_EGG = 1f / 4f;
	private float VOLUME_OF_EGG = (float)Math.PI * RADIUS_OF_EGG * RADIUS_OF_EGG * RADIUS_OF_EGG * 4f / 3f;

	private void spawnEggForEntity(LivingEntity victim) {
		ItemStack maybeEgg = victim.getPickedResult(new EntityRayTraceResult(victim));
		if (!maybeEgg.isEmpty()) {
			// generate an egg at the victim location and boop them to dead. spawn some particles in lieu of a laser lightshow or something more interesting.
			victim.world.addEntity(new ItemEntity(victim.world, victim.getPosX(), victim.getPosY() + victim.getHeight() / 2d, victim.getPosZ(), maybeEgg));
			for (int i = 0; i < 8; i++) {
				double dx = victim.world.rand.nextDouble() - 0.5d;
				double dy = victim.world.rand.nextDouble() - 0.5d;
				double dz = victim.world.rand.nextDouble() - 0.5d;
				victim.world.addParticle(ParticleTypes.END_ROD, victim.getPosX() + dx, victim.getPosY() + dy, victim.getPosZ() + dz, dx, dy, dz);
			}
			return;
		}
	}

	public boolean isReady(int duration, int amplifier) {
		// return duration % 4 == 0;
		return true;
	}
}
