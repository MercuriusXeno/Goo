package com.xeno.goo.aequivaleo.compound;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.ldtteam.aequivaleo.api.recipe.equivalency.IEquivalencyRecipe;
import com.ldtteam.aequivaleo.vanilla.api.recipe.equivalency.ITagEquivalencyRecipe;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GooCompoundTypeGroup extends ForgeRegistryEntry<ICompoundTypeGroup> implements ICompoundTypeGroup
{

    @Override
    public Set<CompoundInstance> handleIngredient(
      final Map<? extends ICompoundContainer<?>, Set<CompoundInstance>> map, final boolean b)
    {
        return map
                 .values()
                 .stream().min((compoundInstances, t1) -> (int) (compoundInstances
                                                                   .stream()
                                                                   .mapToDouble(CompoundInstance::getAmount)
                                                                   .sum() - t1
                                                                              .stream()
                                                                              .mapToDouble(CompoundInstance::getAmount)
                                                                              .sum()))
          .orElse(new HashSet<>());
    }

    @Override
    public boolean canContributeToRecipeAsInput(final CompoundInstance compoundInstance, final IEquivalencyRecipe iEquivalencyRecipe)
    {
        return !(iEquivalencyRecipe instanceof ITagEquivalencyRecipe);
    }

    @Override
    public boolean canContributeToRecipeAsOutput(
      final ICompoundContainer<?> iCompoundContainer, final IEquivalencyRecipe iEquivalencyRecipe, final CompoundInstance compoundInstance)
    {
        return !(iEquivalencyRecipe instanceof ITagEquivalencyRecipe);
    }

    @Override
    public boolean isValidFor(final ICompoundContainer<?> iCompoundContainer, final CompoundInstance compoundInstance)
    {
        return true;
    }

    @Override
    public int compareTo(@NotNull final ICompoundTypeGroup iCompoundTypeGroup)
    {
        return Objects.requireNonNull(getRegistryName()).compareTo(iCompoundTypeGroup.getRegistryName());
    }
}
