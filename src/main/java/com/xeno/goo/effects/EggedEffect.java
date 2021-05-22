package com.xeno.goo.effects;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.shrink.api.ShrinkAPI;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierManager;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.EntityRayTraceResult;

public class EggedEffect extends Effect {
	public static EggedEffect instance = new EggedEffect();
	private static final float SCALE_REDUCTION_PER_TICK = 0.025f;

	protected EggedEffect() {
		super(EffectType.NEUTRAL, 0xffffffff);
	}

	@Override
	public void performEffect(LivingEntity e, int amplifier) {
		e.getCapability(ShrinkAPI.SHRINK_CAPABILITY).ifPresent(s ->
			{
				if (!s.isShrunk()) {
					s.shrink(e);
				}
				s.setScale(s.scale() - 0.025f);
			}
		);
	}

	@Override
	public void removeAttributesModifiersFromEntity(LivingEntity entityLivingBaseIn, AttributeModifierManager attributeMapIn, int amplifier) {
		if (entityLivingBaseIn.world.isRemote()) {
			AudioHelper.entityAudioEvent(entityLivingBaseIn, Registry.PRIMORDIAL_WARP_SOUND.get(), SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
			return;
		}
		spawnEggForEntity(entityLivingBaseIn);
		entityLivingBaseIn.remove();
	}

	private static double volumeOfEntity(LivingEntity e) {
		return e.size.width * e.size.width * e.size.height;
	}

	private static double scaleTarget(LivingEntity e) {
		return VOLUME_OF_EGG / volumeOfEntity(e);
	}

	public static int durationOfEffect(LivingEntity e) {
		return (int)Math.ceil(Math.max(1, (1f - scaleTarget(e)) / SCALE_REDUCTION_PER_TICK));
	}

	// this is ballparked/made up, but bear with me.
	private static final float RADIUS_OF_EGG = 1f / 4f;
	private static final float VOLUME_OF_EGG = (float)Math.PI * RADIUS_OF_EGG * RADIUS_OF_EGG * RADIUS_OF_EGG * 4f / 3f;

	private void spawnEggForEntity(LivingEntity victim) {
		ItemStack maybeEgg = victim.getPickedResult(new EntityRayTraceResult(victim));
		if (!maybeEgg.isEmpty()) {
			// generate an egg at the victim location and boop them to dead. spawn some particles in lieu of a laser lightshow or something more interesting.
			victim.world.addEntity(new ItemEntity(victim.world, victim.getPosX(), victim.getPosY() + victim.getHeight() / 2d, victim.getPosZ(), maybeEgg));
			for (int i = 0; i < 8; i++) {
				double dx = victim.world.rand.nextDouble() - 0.5d;
				double dy = victim.world.rand.nextDouble() - 0.5d;
				double dz = victim.world.rand.nextDouble() - 0.5d;
				victim.world.addParticle(ParticleTypes.END_ROD, victim.getPosX() + dx, victim.getPosY() + dy, victim.getPosZ() + dz,
						dx / 3d, dy / 3d, dz / 3d);
			}
			return;
		}
	}

	public boolean isReady(int duration, int amplifier) {
		return true;
	}
}
