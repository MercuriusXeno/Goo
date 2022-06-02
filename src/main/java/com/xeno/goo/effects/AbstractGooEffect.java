package com.xeno.goo.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public abstract class AbstractGooEffect extends MobEffect {

	protected AbstractGooEffect() {
		super(MobEffectCategory.NEUTRAL, 0xffffffff);
	}

	/**
	 * Returns true if the potion has an instant effect instead of a continuous one (eg Harming)
	 */
	public boolean isInstantenous() {
		return false;
	}
}
