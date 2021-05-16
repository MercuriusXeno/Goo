package com.xeno.goo.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.AttributeModifierManager;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;

public class EntangledEffect extends Effect {

	public static final Effect INSTANCE = new EntangledEffect();

	protected EntangledEffect() {
		super(EffectType.HARMFUL, 0xff00dd00);
	}

	@Override
	public void applyAttributesModifiersToEntity(LivingEntity entityLivingBaseIn, AttributeModifierManager attributeMapIn, int amplifier) {

	}

	@Override
	public void removeAttributesModifiersFromEntity(LivingEntity entityLivingBaseIn, AttributeModifierManager attributeMapIn, int amplifier) {

	}

	@Override
	public boolean shouldRender(EffectInstance effect) {
		return false;
	}
}
