package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.item.DepletedBlazeRodItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Item model property returning 0.0-7.0 for eight depletion stages of a fuel rod.
 * Stage 0 (nub) through stage 7 (barely depleted). Used by range_dispatch
 * to select progressively shorter blaze rod models.
 */
public class FuelRemainingProperty implements RangeSelectItemModelProperty {

    /**
     * Codec for deserialization (no config parameters, singleton).
     *
     * @return the result
     */
    public static final MapCodec<FuelRemainingProperty> MAP_CODEC =
        MapCodec.unit(new FuelRemainingProperty());

    /** Number of visual depletion stages. */
    private static final int STAGES = 8;

    /** Ticks per stage boundary (1200 / 8 = 150). */
    private static final int TICKS_PER_STAGE = DepletedBlazeRodItem.FULL_FUEL_TICKS / STAGES;

    /**
     * Returns a float 0.0-7.0 representing the fuel rod's visual stage.
     * 0.0 = nearly empty (nub), 7.0 = barely depleted (almost full).
     *
     * @param stack the item stack
     * @param level the current level
     * @param owner the entity holding the item, or null
     * @param seed the random seed for model variation
     * @return the value
     */
    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level,
            @Nullable ItemOwner owner, int seed) {
        if (!(stack.getItem() instanceof DepletedBlazeRodItem)) { return 0f; }
        int remaining = DepletedBlazeRodItem.getTicksRemaining(stack);
        return Math.min(remaining / TICKS_PER_STAGE, STAGES - 1);
    }

    @Override
    public MapCodec<FuelRemainingProperty> type() {
        return MAP_CODEC;
    }
}
