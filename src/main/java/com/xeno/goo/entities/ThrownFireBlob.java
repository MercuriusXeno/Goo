package com.xeno.goo.entities;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.GooElement;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ThrownFireBlob extends AbstractThrownBlob {

	public ThrownFireBlob(EntityType<? extends AbstractThrownBlob> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownFireBlob(Level level, LivingEntity sender) {
		super(Registry.THROWN_FIRE_BLOB.get(), level, sender);
	}

	public ThrownFireBlob(Level level, double x, double y, double z) {
		super(Registry.THROWN_FIRE_BLOB.get(), level, x, y, z);
	}

	@Override
	protected Item getDefaultItem() {
		return Registry.FIRE_BLOB_ITEM.get();
	}

	@Override
	protected GooElement getElement() {
		return GooElement.FIRE;
	}
}
