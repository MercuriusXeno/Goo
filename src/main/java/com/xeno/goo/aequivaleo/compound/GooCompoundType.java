package com.xeno.goo.aequivaleo.compound;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.blobs.GooElement;
import net.minecraftforge.registries.ForgeRegistryEntry;
import java.util.function.Supplier;

public class GooCompoundType extends ForgeRegistryEntry<ICompoundType> implements ICompoundType {
	public final GooElement element;
	public final Supplier<GooCompoundTypeGroup> groupSupplier;

	public GooCompoundType(GooElement gooType, Supplier<GooCompoundTypeGroup> groupSupplier) {
		this.element = gooType;
		this.groupSupplier = groupSupplier;
	}

	@Override
	public ICompoundTypeGroup getGroup() {
		return groupSupplier.get();
	}
}
