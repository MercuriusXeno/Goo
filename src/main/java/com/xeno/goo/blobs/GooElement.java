package com.xeno.goo.blobs;

import com.xeno.goo.blobs.elements.*;
import com.xeno.goo.entities.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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

	public String elementName() {
		return this.name;
	}

	public AbstractThrownBlob createThrownBlob(Level level, Player player) {
		return switch(this) {
			case EARTH -> new ThrownEarthBlob(level, player);
			case AIR -> new ThrownAirBlob(level, player);
			case FIRE -> new ThrownFireBlob(level, player);
			case WATER -> new ThrownWaterBlob(level, player);
			case ICE -> new ThrownIceBlob(level, player);
			case LIGHTNING -> new ThrownLightningBlob(level, player);
			case DARK -> new ThrownDarkBlob(level, player);
			case LIGHT -> new ThrownLightBlob(level, player);
			case CRYSTAL -> new ThrownCrystalBlob(level, player);
			case METAL -> new ThrownMetalBlob(level, player);
			case NATURE -> new ThrownNatureBlob(level, player);
			case ENDER -> new ThrownEnderBlob(level, player);
			case NETHER -> new ThrownNetherBlob(level, player);
			default -> null;
		};
	}
}
