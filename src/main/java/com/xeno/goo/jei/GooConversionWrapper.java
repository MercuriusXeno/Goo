package com.xeno.goo.jei;

import com.xeno.goo.aequivaleo.GooEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GooConversionWrapper {
	private final ItemStack item;
	private final List<GooIngredient> goo;
	private final boolean isSolidifiable;
	private final boolean isGooifiable;
	private final boolean isForbidden;

	public GooConversionWrapper() {
		this.isForbidden = true;
		this.item = ItemStack.EMPTY;
		this.goo = new ArrayList<>();
		this.isSolidifiable = false;
		this.isGooifiable = false;
	}

	public GooConversionWrapper(ItemStack i, GooEntry g) {
		this.item = i;
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
		this.isForbidden = false;
	}

	public GooConversionWrapper(Item contents, GooEntry g) {
		this.item = new ItemStack(contents);
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
		this.isForbidden = false;
	}

	public ItemStack item() {
		return item;
	}

	public List<GooIngredient> goo() {
		return goo;
	}

	public boolean isSolidifiable() {
		return isSolidifiable;
	}

	public boolean isGooifiable() {
		return isGooifiable;
	}

	public SolidifierRecipe toSolidifierRecipe() {
		if (this.isForbidden()) {
			return null;
		}
		return new SolidifierRecipe(this.item, this);
	}

	private boolean isForbidden() {
		return isForbidden;
	}

	public GooifierRecipe toGooifierRecipe() {
		return new GooifierRecipe(this.item, this);
	}
}
