package com.xeno.goo.aequivaleo.compound;

import com.ldtteam.aequivaleo.api.compound.ICompoundType;
import com.xeno.goo.fluids.GooFluid;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

public class GooCompoundType implements ICompoundType
{
    public ResourceLocation registryName;
    public Supplier<GooFluid> fluidSupplier;

    public GooCompoundType(ResourceLocation registryName, Supplier<GooFluid> fluidSupplier) {
        this.registryName = registryName;
        this.fluidSupplier = fluidSupplier;
    }

    @Override
    public int compareTo(@NotNull ICompoundType o)
    {
        return Objects.requireNonNull(getRegistryName()).compareTo(Objects.requireNonNull(o.getRegistryName()));
    }

    @Override
    public ICompoundType setRegistryName(ResourceLocation name)
    {
        this.registryName = name;
        return this;
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName()
    {
        return this.registryName;
    }

    @Override
    public Class<ICompoundType> getRegistryType()
    {
        return ICompoundType.class;
    }
}
