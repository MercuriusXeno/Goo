package com.xeno.goo.blobs;

import com.xeno.goo.blobs.elements.*;

public enum GooElement {
	EARTH("earth", Earth::hitEntity),
	AIR("air", Air::hitEntity),
	FIRE("fire", Fire::hitEntity),
	WATER("water", Water::hitEntity),
	ICE("ice", Ice::hitEntity),
	LIGHTNING("lightning", Lightning::hitEntity),
	METAL("metal", Metal::hitEntity),
	CRYSTAL("crystal", Crystal::hitEntity),
	DARK("dark", Dark::hitEntity),
	LIGHT("light", Light::hitEntity),
	NATURE("nature", Nature::hitEntity),
	ENDER("ender", Ender::hitEntity),
	NETHER("nether", Nether::hitEntity),
	FORBIDDEN("forbidden", (c) -> false);

	private final String name;
	private final IWeaponizedBlobEffect weaponizedEffect;

	GooElement(String name, IWeaponizedBlobEffect weaponizedEffect) {
		this.name = name;
		this.weaponizedEffect = weaponizedEffect;
	}

	public IWeaponizedBlobEffect getWeaponizedEffect() {
		return this.weaponizedEffect;
	}
}
