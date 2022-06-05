package com.xeno.goo.entities;

import com.xeno.goo.Registry;
import com.xeno.goo.elements.ElementEnum;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ThrownNetherBlob extends AbstractThrownBlob {

	public ThrownNetherBlob(EntityType<? extends AbstractThrownBlob> entityType, Level level) {
		super(entityType, level);
		this.setItem(new ItemStack(Registry.NETHER_BLOB_ITEM.get()));
	}

	public ThrownNetherBlob(Level level, LivingEntity sender) {
		super(Registry.THROWN_NETHER_BLOB.get(), level, sender);
		this.setItem(new ItemStack(Registry.NETHER_BLOB_ITEM.get()));
	}

	public ThrownNetherBlob(Level level, double x, double y, double z) {
		super(Registry.THROWN_NETHER_BLOB.get(), level, x, y, z);
		this.setItem(new ItemStack(Registry.NETHER_BLOB_ITEM.get()));
	}

	@Override
	protected Item getDefaultItem() {
		return Registry.NETHER_BLOB_ITEM.get();
	}

	@Override
	protected ElementEnum getElement() {
		return ElementEnum.NETHER;
	}
}
