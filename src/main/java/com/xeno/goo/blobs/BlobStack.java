package com.xeno.goo.blobs;

import com.xeno.goo.aequivaleo.compound.GooCompoundType;
import net.minecraft.resources.ResourceLocation;

public class BlobStack {

	/***
	 * The amount of goo in the stack.
	 */
	private int amount;

	/***
	 * What type of goo it is.
	 */
	private GooCompoundType type;

	/**
	 * General constructor for a new stack of goo.
	 * @param amount How much goo
	 * @param type What kind of goo is it.
	 */
	public BlobStack(int amount, GooCompoundType type) {

		this.amount = amount;
		this.type = type;
	}

	public void decreaseAmount(int reduceBy) {
		this.amount -= reduceBy;
	}

	public int amount() {
		return this.amount;
	}

	public GooCompoundType type() {
		return this.type;
	}

	public ResourceLocation blobTypeName() {
		return this.type.getRegistryName();
	}

	public boolean addStack(BlobStack stack) {
		if (this.type != stack.type) {
			return false;
		}
		this.amount += stack.amount();
		return true;
	}


}
