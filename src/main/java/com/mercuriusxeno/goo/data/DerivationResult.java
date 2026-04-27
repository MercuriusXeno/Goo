package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;

/**
 * Immutable result of one full derivation run.
 *
 * @param derivedValues      values computed from recipes
 * @param derivationSources  winning recipe input per output item
 * @param effectiveValues    final values after LCD comparison with base
 * @param conflicts          items where base and recipe values diverged
 * @param cycles             recipe cycles detected during derivation
 * @param divisibilityLosses items with integer division rounding loss
 */
public record DerivationResult(
        Map<Identifier, GooValue> derivedValues,
        Map<Identifier, RecipeInput> derivationSources,
        Map<Identifier, GooValue> effectiveValues,
        List<GooValueRegistry.ValueConflict> conflicts,
        List<GooValueRegistry.RecipeCycle> cycles,
        List<GooValueRegistry.DivisibilityLoss> divisibilityLosses
) {
}
