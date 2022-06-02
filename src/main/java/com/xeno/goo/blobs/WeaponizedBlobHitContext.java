package com.xeno.goo.blobs;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class WeaponizedBlobHitContext {
	private final Level level;
	private final LivingEntity entityHit;
	private final LivingEntity owner;
	private final GooElement element;

	public WeaponizedBlobHitContext(LivingEntity entityHit, LivingEntity owner, GooElement element) {
		this.level = entityHit.level;
		this.entityHit = entityHit;
		this.owner = owner;
		this.element = element;
	}

	public LivingEntity entityHit() {
		return this.entityHit;
	}
}
