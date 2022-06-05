package com.xeno.goo.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public class Light extends AbstractElement {

	public Light(Supplier<AbstractGooEffect> effectSupplier, TagKey<EntityType<?>> immunityTag, int debuffDuration, int debuffMaxStacks) {

		super(effectSupplier, immunityTag, debuffDuration);
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		super.hitEntity(c);
	}
}
