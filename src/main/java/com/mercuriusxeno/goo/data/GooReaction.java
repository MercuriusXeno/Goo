package com.mercuriusxeno.goo.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A single reactor reaction: consumes inputs, produces outputs at a
 * ratio. Loaded from datapack JSON under {@code data/<ns>/goo_reactions/}.
 *
 * @param id      the datapack resource identifier
 * @param inputs  fluids consumed per batch
 * @param outputs fluids produced per batch (amounts scaled by rate)
 * @param rate    output multiplier ratio
 */
public record GooReaction(
        Identifier id,
        List<FluidEntry> inputs,
        List<FluidEntry> outputs,
        int rate
) {

    /**
     * A fluid + amount pair used in recipe inputs and outputs.
     *
     * @param fluid  the fluid
     * @param amount mB consumed or produced per batch
     */
    public record FluidEntry(Fluid fluid, int amount) {

        /** Codec for a single fluid entry. */
        public static final Codec<FluidEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BuiltInRegistries.FLUID.byNameCodec()
                        .fieldOf("fluid").forGetter(FluidEntry::fluid),
                Codec.INT.fieldOf("amount").forGetter(FluidEntry::amount)
        ).apply(inst, FluidEntry::new));
    }

    /** Placeholder id used during codec parsing; replaced by filename in the loader. */
    private static final Identifier PLACEHOLDER_ID = Identifier.withDefaultNamespace("unknown");

    /** Codec for the reaction JSON. The id is not in the JSON; it comes from the filename. */
    public static final Codec<GooReaction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            FluidEntry.CODEC.listOf().fieldOf("inputs").forGetter(GooReaction::inputs),
            FluidEntry.CODEC.listOf().fieldOf("outputs").forGetter(GooReaction::outputs),
            Codec.INT.fieldOf("rate").forGetter(GooReaction::rate)
    ).apply(inst, (inputs, outputs, rate) -> new GooReaction(PLACEHOLDER_ID, inputs, outputs, rate)));

    /**
     * Returns the set of input fluid types (ignoring amounts).
     *
     * @return the input type set
     */
    public Set<Fluid> inputTypeSet() {
        return inputs.stream().map(FluidEntry::fluid).collect(Collectors.toSet());
    }

    /**
     * Returns a copy with the datapack resource id set.
     *
     * @param recipeId the resource identifier
     * @return the reaction with id applied
     */
    public GooReaction withId(Identifier recipeId) {
        return new GooReaction(recipeId, inputs, outputs, rate);
    }
}
