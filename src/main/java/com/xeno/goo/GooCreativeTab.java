package com.xeno.goo;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class GooCreativeTab extends CreativeModeTab {
	public static final NonNullList<ItemStack> sortedCreativeItems = NonNullList.create();

	@Override
	public void fillItemList(NonNullList<ItemStack> items) {
		if (sortedCreativeItems.size() == 0) {
			super.fillItemList(items);
			sortedCreativeItems.addAll(items);
		} else {
			items.addAll(sortedCreativeItems);
		}
	}

	public GooCreativeTab(String label) {
		super(label);
	}

	@Override
	public ItemStack makeIcon() {
		return new ItemStack(Items.SLIME_BALL);
	}



}
