package com.xeno.goo.blobs;

import com.xeno.goo.elements.ElementEnum;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class WeaponizedBlobHitContext {
	private final Level level;
	private final LivingEntity entityHit;
	private final LivingEntity owner;
	private final ElementEnum element;

	public WeaponizedBlobHitContext(LivingEntity entityHit, LivingEntity owner, ElementEnum element) {
		this.level = entityHit.level;
		this.entityHit = entityHit;
		this.owner = owner;
		this.element = element;
	}

	public LivingEntity entityHit() {
		return this.entityHit;
	}

	public ElementEnum element() { return this.element; }
}
