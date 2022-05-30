package com.xeno.goo.entities;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.GooElement;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ThrownLightBlob extends AbstractThrownBlob {

	public ThrownLightBlob(EntityType<? extends AbstractThrownBlob> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownLightBlob(Level level, LivingEntity sender) {
		super(Registry.THROWN_LIGHT_BLOB.get(), level, sender);
	}

	public ThrownLightBlob(Level level, double x, double y, double z) {
		super(Registry.THROWN_LIGHT_BLOB.get(), level, x, y, z);
	}

	@Override
	protected Item getDefaultItem() {
		return Registry.LIGHT_BLOB_ITEM.get();
	}

	@Override
	protected GooElement getElement() {
		return GooElement.LIGHT;
	}
}
