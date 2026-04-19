package com.mercuriusxeno.goo.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Sealed hierarchy for ability cost formulas. Each variant computes
 * the mB cost of the Nth blob in a throw sequence, where N is the
 * sequence-aware stack position (landed + in-flight at the target).
 */
public sealed interface AbilityCost {

    String FORMULA_QUADRATIC = "quadratic";
    String FORMULA_BLOCK_COUNT = "block_count";
    String FORMULA_POWER_LAW = "power_law";

    /**
     * Computes the cost in mB for throwing blob number {@code n} in the sequence.
     *
     * @param n the 0-based sequence position (0 = first throw)
     * @return the cost in microblobs
     */
    int costForStack(int n);

    /** Codec that dispatches on the "formula" field. */
    Codec<AbilityCost> CODEC = Codec.STRING.fieldOf("formula")
            .codec()
            .dispatch(AbilityCost::formulaName, AbilityCost::codecForFormula);

    /**
     * Returns the formula name for codec dispatch.
     *
     * @return the formula identifier string
     */
    String formulaName();

    /**
     * Returns the codec for a given formula name.
     *
     * @param name the formula name
     * @return the matching map codec
     */
    static MapCodec<? extends AbilityCost> codecForFormula(String name) {
        return switch (name) {
            case FORMULA_QUADRATIC -> Quadratic.MAP_CODEC;
            case FORMULA_BLOCK_COUNT -> BlockCount.MAP_CODEC;
            case FORMULA_POWER_LAW -> PowerLaw.MAP_CODEC;
            default -> throw new IllegalArgumentException(name);
        };
    }

    /**
     * Quadratic: {@code baseCost * (a * N^2 + b * N + c)}.
     * Covers constant (a=0, b=0, c=1), linear (a=0, b=1, c=1),
     * and quadratic scaling.
     *
     * @param baseCost the base cost in mB
     * @param a        quadratic coefficient
     * @param b        linear coefficient
     * @param c        constant term
     */
    record Quadratic(int baseCost, float a, float b, float c) implements AbilityCost {

        static final MapCodec<Quadratic> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("baseCost").forGetter(Quadratic::baseCost),
                Codec.FLOAT.fieldOf("a").forGetter(Quadratic::a),
                Codec.FLOAT.fieldOf("b").forGetter(Quadratic::b),
                Codec.FLOAT.fieldOf("c").forGetter(Quadratic::c)
        ).apply(inst, Quadratic::new));

        @Override
        public int costForStack(int n) {
            return (int) (baseCost * (a * n * n + b * n + c));
        }

        @Override
        public String formulaName() { return FORMULA_QUADRATIC; }
    }

    /**
     * Block-count: {@code costPerBlock * marginalBlocks(N)}.
     * The caller must supply the marginal block count externally since
     * it depends on the effect's footprint geometry.
     *
     * @param costPerBlock mB cost per additional block in the footprint
     */
    record BlockCount(int costPerBlock) implements AbilityCost {

        static final MapCodec<BlockCount> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("costPerBlock").forGetter(BlockCount::costPerBlock)
        ).apply(inst, BlockCount::new));

        @Override
        public int costForStack(int n) {
            // Caller must use costForBlocks() instead for accurate results.
            // This fallback returns costPerBlock as a minimum.
            return costPerBlock;
        }

        /**
         * Computes cost based on the actual marginal block count.
         *
         * @param marginalBlocks the number of new blocks the next stack adds
         * @return the cost in microblobs
         */
        public int costForBlocks(int marginalBlocks) {
            return costPerBlock * marginalBlocks;
        }

        @Override
        public String formulaName() { return FORMULA_BLOCK_COUNT; }
    }

    /**
     * Power law: {@code baseCost * mult^N}.
     * Each successive stack costs {@code mult} times more than the last.
     *
     * @param baseCost the cost of the first throw (N=0)
     * @param mult     the per-stack multiplier
     */
    record PowerLaw(int baseCost, float mult) implements AbilityCost {

        static final MapCodec<PowerLaw> MAP_CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
                Codec.INT.fieldOf("baseCost").forGetter(PowerLaw::baseCost),
                Codec.FLOAT.fieldOf("mult").forGetter(PowerLaw::mult)
        ).apply(inst, PowerLaw::new));

        @Override
        public int costForStack(int n) {
            return (int) (baseCost * Math.pow(mult, n));
        }

        @Override
        public String formulaName() { return FORMULA_POWER_LAW; }
    }
}
