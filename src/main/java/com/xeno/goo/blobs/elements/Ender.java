package com.xeno.goo.blobs.elements;

import com.xeno.goo.blobs.WeaponizedBlobHitContext;
import com.xeno.goo.effects.AbstractGooEffect;

import java.util.function.Supplier;

public class Ender extends AbstractElement {

	public Ender(Supplier<AbstractGooEffect> effectSupplier, int debuffDuration, int debuffMaxStacks) {

		super(effectSupplier, debuffDuration, debuffMaxStacks);
	}

	public void hitEntity(WeaponizedBlobHitContext c) {
		super.hitEntity(c);
	}

	@Override
	void tryMaxStackEffect(WeaponizedBlobHitContext c) {

	}
}
