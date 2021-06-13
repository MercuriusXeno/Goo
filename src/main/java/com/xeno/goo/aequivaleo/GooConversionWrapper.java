package com.xeno.goo.aequivaleo;

import com.xeno.goo.aequivaleo.GooEntry;
import com.xeno.goo.jei.GooIngredient;
import com.xeno.goo.jei.GooifierRecipe;
import com.xeno.goo.jei.SolidifierRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GooConversionWrapper {
	private static final GooConversionWrapper INVALID = new GooConversionWrapper();

	private final ItemStack item;
	private final List<GooIngredient> goo;
	private final boolean isSolidifiable;
	private final boolean isGooifiable;
	private final boolean isForbidden;
	private final Optional<SolidifierRecipe> solidifierRecipe;
	private final Optional<GooifierRecipe> gooifierRecipe;

	public GooConversionWrapper() {
		this.isForbidden = true;
		this.item = ItemStack.EMPTY;
		this.goo = new ArrayList<>();
		this.isSolidifiable = false;
		this.isGooifiable = false;
		solidifierRecipe = toSolidifierRecipe();
		gooifierRecipe = toGooifierRecipe();
	}

	public GooConversionWrapper(ItemStack i, GooEntry g) {
		this.item = i;
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
		this.isForbidden = false;
		solidifierRecipe = toSolidifierRecipe();
		gooifierRecipe = toGooifierRecipe();
	}

	public GooConversionWrapper(Item contents, GooEntry g) {
		this.item = new ItemStack(contents);
		this.goo = g.inputsAsGooIngredient();
		this.isSolidifiable = !g.deniesSolidification();
		this.isGooifiable = !g.isEmpty() && !g.isUnusable();
		this.isForbidden = false;
		solidifierRecipe = toSolidifierRecipe();
		gooifierRecipe = toGooifierRecipe();
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

	private Optional<SolidifierRecipe> toSolidifierRecipe() {
		if (!this.isSolidifiable) {
			return Optional.empty();
		}
		return Optional.of(new SolidifierRecipe(this.item, this));
	}

	private boolean isForbidden() {
		return isForbidden;
	}

	private Optional<GooifierRecipe> toGooifierRecipe() {
		if (!this.isGooifiable) {
			return Optional.empty();
		}
		return Optional.of(new GooifierRecipe(this.item, this));
	}

	public Optional<SolidifierRecipe> solidifierRecipe() {
		return solidifierRecipe;
	}

	public Optional<GooifierRecipe> gooifierRecipe() {
		return gooifierRecipe;
	}
}
