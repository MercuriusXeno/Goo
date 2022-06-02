package com.xeno.goo.blobs;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.elements.*;
import com.xeno.goo.entities.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public enum GooElement {
	EARTH("earth", new Earth(Registry.PETRIFICATION_EFFECT, 600, 2)),
	AIR("air", new Air(() -> null, 0, 0)),
	FIRE("fire", new Fire(() -> null, 0, 0)),
	WATER("water", new Water(() -> null, 0, 0)),
	ICE("ice", new Ice(() -> null, 0, 0)),
	LIGHTNING("lightning", new Lightning(() -> null, 0, 0)),
	METAL("metal", new Metal(() -> null, 0, 0)),
	CRYSTAL("crystal", new Crystal(() -> null, 0, 0)),
	DARK("dark", new Dark(() -> null, 0, 0)),
	LIGHT("light", new Light(() -> null, 0, 0)),
	NATURE("nature", new Nature(() -> null, 0, 0)),
	ENDER("ender", new Ender(() -> null, 0, 0)),
	NETHER("nether", new Nether(() -> null, 0, 0)),
	FORBIDDEN("forbidden", null);

	private final String name;
	private final AbstractElement elementImpl;

	GooElement(String name, AbstractElement element) {
		this.name = name;
		this.elementImpl = element;
	}

	public AbstractElement element() {
		return this.elementImpl;
	}

	public IWeaponizedBlobEffect getWeaponizedEffect() {
		return this.elementImpl::hitEntity;
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
