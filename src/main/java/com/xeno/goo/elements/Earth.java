package com.xeno.goo.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.function.Supplier;

public class Earth extends AbstractElement {
	public Earth(Supplier<AbstractGooEffect> effectSupplier, TagKey<EntityType<?>> immunityTag, int debuffDuration, int debuffMaxStacks) {

		super(effectSupplier, immunityTag, debuffDuration);
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		super.hitEntity(c);
	}
}
