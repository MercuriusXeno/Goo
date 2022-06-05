package com.xeno.goo.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

public abstract class AbstractElement {
	private final Supplier<AbstractGooEffect> effectSupplier;
	private final int debuffDuration;
	private final TagKey<EntityType<?>> immunityTag;
	public AbstractElement(Supplier<AbstractGooEffect> effectSupplier, TagKey<EntityType<?>> immunityTag, int debuffDuration) {
		this.effectSupplier = effectSupplier;
		this.debuffDuration = debuffDuration;
		this.immunityTag = immunityTag;
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		tryDebuff(c);
	}

	public void tryDebuff(WeaponizedBlobHitContext c) {
		if (debuff() == null) {
			return;
		}
		if (isMobImmune(c.entityHit(), c.element())) {
			return;
		}
		c.entityHit().addEffect(new MobEffectInstance(debuff(), debuffDuration(), 0, false, false, false));
	}

	private boolean isMobImmune(LivingEntity entityHit, ElementEnum element) {
		return element.immunityTag() != null && entityHit.getType().is(element.immunityTag());
	}

	public AbstractGooEffect debuff() {
		return this.effectSupplier.get();
	}

	public int debuffDuration() {
		return this.debuffDuration;
	}

	public TagKey<EntityType<?>> immunityTag() {
		return this.immunityTag;
	}
}
