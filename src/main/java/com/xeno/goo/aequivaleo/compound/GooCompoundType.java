package com.xeno.goo.aequivaleo.compound;

import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.xeno.goo.fluids.GooFluid;
import net.minecraftforge.registries.ForgeRegistryEntry;

import java.util.Objects;
import java.util.function.Supplier;

public class GooCompoundType extends ForgeRegistryEntry<ICompoundType> implements ICompoundType
{
    public Supplier<GooFluid> fluidSupplier;
    public Supplier<GooCompoundTypeGroup> groupSupplier;
    public GooCompoundType(
      final Supplier<GooFluid> fluidSupplier,
      final Supplier<GooCompoundTypeGroup> groupSupplier)
    {
        this.fluidSupplier = fluidSupplier;
        this.groupSupplier = groupSupplier;
    }

    @Override
    public int compareTo(ICompoundType o)
    {
        return Objects.requireNonNull(getRegistryName()).compareTo(Objects.requireNonNull(o.getRegistryName()));
    }

    @Override
    public ICompoundTypeGroup getGroup()
    {
        return groupSupplier.get();
    }

    @Override
    public String toString()
    {
        return "Goo: " + Objects.requireNonNull(getRegistryName()).toString();
    }
}
