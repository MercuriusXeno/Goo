package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Immutable single-fluid data component for canister items.
 * Stores one fluid type and its volume in microblobs (mB).
 * Accepts any registered fluid (goo types, water, lava, etc.).
 *
 * @param fluid  the stored fluid, or {@link Fluids#EMPTY} if none
 * @param amount the volume in microblobs (mB), 0 if empty
 */
public record CanisterFluidContent(Fluid fluid, int amount) {

    /** Empty canister with no fluid. */
    public static final CanisterFluidContent EMPTY = new CanisterFluidContent(Fluids.EMPTY, 0);

    /** Persistent codec: fluid as registry name string, amount as int. */
    public static final Codec<CanisterFluidContent> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(CanisterFluidContent::fluid),
            Codec.INT.fieldOf("amount").forGetter(CanisterFluidContent::amount)
        ).apply(instance, CanisterFluidContent::new)
    );

    /** Network codec: fluid as registry int ID, amount as VAR_INT. */
    public static final StreamCodec<ByteBuf, CanisterFluidContent> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public CanisterFluidContent decode(ByteBuf buf) {
                int fluidId = ByteBufCodecs.VAR_INT.decode(buf);
                int vol = ByteBufCodecs.VAR_INT.decode(buf);
                Fluid f = BuiltInRegistries.FLUID.byId(fluidId);
                return new CanisterFluidContent(f, vol);
            }

            @Override
            public void encode(ByteBuf buf, CanisterFluidContent value) {
                ByteBufCodecs.VAR_INT.encode(buf, BuiltInRegistries.FLUID.getId(value.fluid));
                ByteBufCodecs.VAR_INT.encode(buf, value.amount);
            }
        };

    /** Normalizes: empty fluid or non-positive amount both produce EMPTY state. */
    public CanisterFluidContent {
        if (fluid == Fluids.EMPTY || amount <= 0) {
            fluid = Fluids.EMPTY;
            amount = 0;
        }
    }

    /**
     * Returns true if no fluid is stored.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return fluid == Fluids.EMPTY || amount <= 0;
    }

    /**
     * Returns the goo type if this holds a goo fluid, or null for vanilla fluids.
     *
     * @return the goo type, or null
     */
    @Nullable
    public GooType getGooType() {
        return GooFluids.getTypeFromFluid(fluid);
    }

    /**
     * Returns a new content with the given volume added. Fluid must match
     * the current fluid (or current must be empty).
     *
     * @param addFluid the fluid to add
     * @param addAmount the volume to add in microblobs
     * @return new content with the addition, or this if incompatible
     */
    public CanisterFluidContent withAdded(Fluid addFluid, int addAmount) {
        if (addAmount <= 0) { return this; }
        if (isEmpty()) { return new CanisterFluidContent(addFluid, addAmount); }
        if (fluid != addFluid) { return this; }
        return new CanisterFluidContent(fluid, amount + addAmount);
    }

    /**
     * Returns a new content with the given volume removed.
     *
     * @param removeAmount the volume to remove in microblobs
     * @return new content with the removal applied
     */
    public CanisterFluidContent withRemoved(int removeAmount) {
        if (removeAmount <= 0 || isEmpty()) { return this; }
        int remaining = amount - removeAmount;
        return remaining > 0 ? new CanisterFluidContent(fluid, remaining) : EMPTY;
    }

    /**
     * Returns a new content with the given volume added, capped by capacity.
     *
     * @param addFluid the fluid to add
     * @param addAmount the requested volume
     * @param capacity the total capacity of the container
     * @return new content with the capped addition
     */
    public CanisterFluidContent withCappedAdd(Fluid addFluid, int addAmount, int capacity) {
        if (addAmount <= 0) { return this; }
        if (!isEmpty() && fluid != addFluid) { return this; }
        int currentAmount = isEmpty() ? 0 : amount;
        int space = capacity - currentAmount;
        if (space <= 0) { return this; }
        int accepted = Math.min(addAmount, space);
        Fluid target = isEmpty() ? addFluid : fluid;
        return new CanisterFluidContent(target, currentAmount + accepted);
    }

    /**
     * Returns how much of the requested amount would be accepted by withCappedAdd.
     *
     * @param addFluid the fluid to add
     * @param addAmount the requested volume
     * @param capacity the total capacity of the container
     * @return the amount that would be accepted
     */
    public int cappedAddAmount(Fluid addFluid, int addAmount, int capacity) {
        if (addAmount <= 0) { return 0; }
        if (!isEmpty() && fluid != addFluid) { return 0; }
        int currentAmount = isEmpty() ? 0 : amount;
        int space = capacity - currentAmount;
        if (space <= 0) { return 0; }
        return Math.min(addAmount, space);
    }
}
