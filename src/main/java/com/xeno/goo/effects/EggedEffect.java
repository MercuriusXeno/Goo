package com.xeno.goo.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.EffectType;

public class EggedEffect extends Effect {

	protected EggedEffect() {
		super(EffectType.NEUTRAL, 0xffffffff);
	}

	@Override
	public void performEffect(LivingEntity e, int amplifier) {

	}

	public boolean isReady(int duration, int amplifier) {
		return duration % 4 == 0;
	}
}
