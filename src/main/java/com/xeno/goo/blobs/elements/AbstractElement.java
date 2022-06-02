package com.xeno.goo.blobs.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.function.Supplier;

public abstract class AbstractElement {
	public AbstractElement(Supplier<AbstractGooEffect> effectSupplier, int debuffDuration, int debuffMaxStacks) {
		this.effectSupplier = effectSupplier;
		this.debuffDuration = debuffDuration;
		this.debuffMaxStacks = debuffMaxStacks;
	}
	private final Supplier<AbstractGooEffect> effectSupplier;
	private final int debuffDuration;
	private final int debuffMaxStacks;

	public void hitEntity(WeaponizedBlobHitContext c) {
		tryStackingDebuff(c);

	}

	public void tryStackingDebuff(WeaponizedBlobHitContext c) {
		if (debuff() == null) {
			return;
		}
		var entity = c.entityHit();
		var amp = 0;
		if (debuffMaxStacks() > 0) {
			if (entity.getActiveEffectsMap().containsKey(debuff())) {
				var existingEffect = entity.getActiveEffectsMap().get(debuff());
				// already at max stacks, attempt to do a "max-plus-ultra" effect if the element has one.
				// petrification shattering etc
				if (existingEffect.getAmplifier() == debuffMaxStacks()) {
					tryMaxStackEffect(c);
				}
				amp = Math.min(debuffMaxStacks(), existingEffect.getAmplifier() + 1);
			}
		}

		c.entityHit().addEffect(new MobEffectInstance(debuff(), debuffDuration(), amp, false, false, false));
	}

	public AbstractGooEffect debuff() {
		return this.effectSupplier.get();
	}

	public int debuffDuration() {
		return this.debuffDuration;
	}

	public int debuffMaxStacks() {
		return this.debuffMaxStacks;
	}

	abstract void tryMaxStackEffect(WeaponizedBlobHitContext c);
}
