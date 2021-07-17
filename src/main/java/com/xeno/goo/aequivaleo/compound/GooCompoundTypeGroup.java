package com.xeno.goo.aequivaleo.compound;

import com.ldtteam.aequivaleo.api.compound.CompoundInstance;
import com.ldtteam.aequivaleo.api.compound.container.ICompoundContainer;
import com.ldtteam.aequivaleo.api.compound.type.ICompoundType;
import com.ldtteam.aequivaleo.api.compound.type.group.ICompoundTypeGroup;
import com.ldtteam.aequivaleo.api.mediation.IMediationCandidate;
import com.ldtteam.aequivaleo.api.mediation.IMediationContext;
import com.ldtteam.aequivaleo.api.mediation.IMediationEngine;
import com.ldtteam.aequivaleo.api.recipe.equivalency.IEquivalencyRecipe;
import com.ldtteam.aequivaleo.vanilla.api.recipe.equivalency.ITagEquivalencyRecipe;
import com.xeno.goo.aequivaleo.GooConversionWrapper;
import com.xeno.goo.aequivaleo.GooEntry;
import net.minecraft.block.OreBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistryEntry;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.CallbackI.P;

import java.util.*;
import java.util.function.Function;

public class GooCompoundTypeGroup extends ForgeRegistryEntry<ICompoundTypeGroup> implements ICompoundTypeGroup
{

    @Override
    public @NotNull IMediationEngine getMediationEngine()
    {
        return context -> {
//            if (!context.areTargetParentsAnalyzed())
//                return Optional.of(Collections.emptySet());

            return context
                     .getCandidates()
                     .stream()
                     .min((o1, o2) -> {
                         if (o1.isSourceIncomplete() && !o2.isSourceIncomplete())
                             return 1;

                         if (!o1.isSourceIncomplete() && o2.isSourceIncomplete())
                             return -1;

                         if (o1.getValues().isEmpty() && !o2.getValues().isEmpty())
                             return 1;

                         if (!o1.getValues().isEmpty() && o2.getValues().isEmpty())
                             return -1;

                         return (int) (o1.getValues().stream().mapToDouble(CompoundInstance::getAmount).sum() -
                                         o2.getValues().stream().mapToDouble(CompoundInstance::getAmount).sum());
                     })
                     .map(IMediationCandidate::getValues);
        };
    }

    @Override
    public boolean shouldIncompleteRecipeBeProcessed(@NotNull final IEquivalencyRecipe iEquivalencyRecipe)
    {
        return false;
    }

    @Override
    public boolean canContributeToRecipeAsInput(IEquivalencyRecipe iEquivalencyRecipe, CompoundInstance compoundInstance) {
        return !(iEquivalencyRecipe instanceof ITagEquivalencyRecipe);
    }

    @Override
    public boolean canContributeToRecipeAsOutput(IEquivalencyRecipe iEquivalencyRecipe, CompoundInstance compoundInstance) {
        return !(iEquivalencyRecipe instanceof ITagEquivalencyRecipe);
    }

    public boolean isValidFor(final ICompoundContainer<?> iCompoundContainer, final CompoundInstance compoundInstance)
    {
        Object contents = iCompoundContainer.getContents();
        return contents instanceof ItemStack || contents instanceof Item || contents instanceof FluidStack;
    }

    @Override
    public int compareTo(@NotNull final ICompoundTypeGroup iCompoundTypeGroup)
    {
        return Objects.requireNonNull(getRegistryName()).compareTo(iCompoundTypeGroup.getRegistryName());
    }

    @Override
    public Optional<?> convertToCacheEntry(ICompoundContainer<?> container, Set<CompoundInstance> instances) {
        if (!isValidForGooRecipe(container)) {
            return Optional.empty();
        }
        return Optional.of(new GooConversionWrapper((ItemStack)container.getContents(), new GooEntry(instances)));
    }

    private boolean isValidForGooRecipe(ICompoundContainer<?> container) {
        return container.getContents() instanceof ItemStack;
    }
}
