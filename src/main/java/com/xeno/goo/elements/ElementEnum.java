package com.xeno.goo.elements;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.IWeaponizedBlobEffect;
import com.xeno.goo.datagen.GooEntityTags;
import com.xeno.goo.entities.*;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public enum ElementEnum {
	EARTH("earth", new Earth(Registry.PETRIFICATION_EFFECT, GooEntityTags.CONVERSION_IMMUNE_ENTITY_TYPES, 600, 2)),
	AIR("air", new Air(() -> null, null,0, 0)),
	FIRE("fire", new Fire(() -> null, null, 0, 0)),
	WATER("water", new Water(() -> null, null, 0, 0)),
	ICE("ice", new Ice(() -> null, null, 0, 0)),
	LIGHTNING("lightning", new Lightning(() -> null, null, 0, 0)),
	METAL("metal", new Metal(() -> null, null, 0, 0)),
	CRYSTAL("crystal", new Crystal(() -> null, null, 0, 0)),
	DARK("dark", new Dark(() -> null, null, 0, 0)),
	LIGHT("light", new Light(() -> null, null, 0, 0)),
	NATURE("nature", new Nature(() -> null, null, 0, 0)),
	ENDER("ender", new Ender(() -> null, null, 0, 0)),
	NETHER("nether", new Nether(() -> null, null, 0, 0)),
	FORBIDDEN("forbidden", null);

	private final String name;
	private final AbstractElement elementImpl;

	ElementEnum(String name, AbstractElement element) {
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

	public TagKey<EntityType<?>> immunityTag() {
		return this.element().immunityTag();
	}
}
