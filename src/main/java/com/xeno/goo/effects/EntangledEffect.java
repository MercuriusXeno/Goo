package com.xeno.goo.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.entity.ai.attributes.AttributeModifierManager;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;
import net.minecraft.util.DamageSource;

public class EntangledEffect extends Effect {

	public static final Effect INSTANCE = new EntangledEffect();

	protected EntangledEffect() {
		super(EffectType.HARMFUL, 0xff00dd00);
		this.addAttributesModifier(Attributes.MOVEMENT_SPEED, "445dd17c-b66c-11eb-8529-0242ac130003", -0.15d, Operation.MULTIPLY_TOTAL)
			.addAttributesModifier(Attributes.ATTACK_SPEED, "9e0266f2-b66c-11eb-8529-0242ac130003", -0.1d, Operation.MULTIPLY_TOTAL);
	}

	@Override
	public void performEffect(LivingEntity entityLivingBaseIn, int amplifier) {
		if (entityLivingBaseIn.getHealth() > 1.0F) {
			entityLivingBaseIn.attackEntityFrom(DamageSource.MAGIC, 1.0F);
		}

		super.performEffect(entityLivingBaseIn, amplifier);
	}

	@Override
	public boolean isReady(int duration, int amplifier) {
		int i = 40 >> amplifier;
		if (i > 0) {
			return duration % i == 0;
		} else {
			return true;
		}
	}

	public boolean isInstant() {
		return false;
	}

	@Override
	public boolean shouldRender(EffectInstance effect) {
		return false;
	}
}
