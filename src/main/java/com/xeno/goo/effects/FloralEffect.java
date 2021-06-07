package com.xeno.goo.effects;

import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectType;

public class FloralEffect extends Effect {
	public FloralEffect() {

		super(EffectType.BENEFICIAL, 0xff22ff22);
	}

	public boolean isReady(int duration, int amplifier) {
		return false;
	}
}
