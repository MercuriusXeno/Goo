package com.xeno.goo.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public class Air extends AbstractElement {

	public Air(Supplier<AbstractGooEffect> effectSupplier, TagKey<EntityType<?>> immunityTag, int debuffDuration, int debuffMaxStacks) {

		super(effectSupplier, immunityTag, debuffDuration);
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		super.hitEntity(c);
	}
}
