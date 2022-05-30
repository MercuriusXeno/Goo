package com.xeno.goo.entities;

import com.xeno.goo.Registry;
import com.xeno.goo.blobs.GooElement;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class ThrownCrystalBlob extends AbstractThrownBlob {

	public ThrownCrystalBlob(EntityType<? extends AbstractThrownBlob> entityType, Level level) {
		super(entityType, level);
	}

	public ThrownCrystalBlob(Level level, LivingEntity sender) {
		super(Registry.THROWN_CRYSTAL_BLOB.get(), level, sender);
	}

	public ThrownCrystalBlob(Level level, double x, double y, double z) {
		super(Registry.THROWN_CRYSTAL_BLOB.get(), level, x, y, z);
	}

	@Override
	protected Item getDefaultItem() {
		return Registry.CRYSTAL_BLOB_ITEM.get();
	}

	@Override
	protected GooElement getElement() {
		return GooElement.CRYSTAL;
	}
}
