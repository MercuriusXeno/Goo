package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.IComponentValueProvider;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A blaze rod mid-combustion in the crucible. Stores remaining fuel ticks
 * via the FUEL_REMAINING data component. Each tick of active melting
 * decrements the counter by one. Unstackable, not in creative tab.
 * Retains proportional goo value based on remaining fuel.
 */
public class DepletedBlazeRodItem extends Item implements IComponentValueProvider {

    /** Identifier for the vanilla blaze rod, used to look up base goo value. */
    private static final Identifier BLAZE_ROD_ID = Identifier.withDefaultNamespace("blaze_rod");

    /** Total fuel ticks in a fresh blaze rod: 60 seconds at 20 tps. */
    public static final int FULL_FUEL_TICKS = 1200;

    /** Creates a depleted blaze rod item. Unstackable. */
    public DepletedBlazeRodItem(Properties properties) {
        super(properties);
    }

    /** Returns the remaining fuel ticks on the given stack. */
    public static int getTicksRemaining(ItemStack stack) {
        Integer ticks = stack.get(GooDataComponents.FUEL_REMAINING.get());
        return ticks != null ? ticks : 0;
    }

    /** Sets the remaining fuel ticks on the given stack. */
    public static void setTicksRemaining(ItemStack stack, int ticks) {
        stack.set(GooDataComponents.FUEL_REMAINING.get(), ticks);
    }

    /**
     * Consumes one fuel tick from the stack.
     * Returns true if the rod still has fuel remaining after consumption.
     */
    public static boolean consumeTick(ItemStack stack) {
        int remaining = getTicksRemaining(stack) - 1;
        setTicksRemaining(stack, Math.max(0, remaining));
        return remaining > 0;
    }

    /** Creates a fresh depleted blaze rod with full fuel ticks. */
    public static ItemStack createFresh() {
        ItemStack stack = new ItemStack(
            com.mercuriusxeno.goo.registry.GooItems.DEPLETED_BLAZE_ROD.get());
        setTicksRemaining(stack, FULL_FUEL_TICKS);
        return stack;
    }

    /** Returns true if the rod has no fuel remaining. */
    public static boolean isEmpty(ItemStack stack) {
        return getTicksRemaining(stack) <= 0;
    }

    /**
     * Computes partial goo value proportional to remaining fuel ticks.
     * Scales the vanilla blaze rod's goo value by (remaining / full).
     */
    @Override
    @Nullable
    public GooValue computeComponentValue(ItemStack stack, IGooValueLookup registry) {
        int remaining = getTicksRemaining(stack);
        if (remaining <= 0) return GooValue.EMPTY;
        GooValue blazeRodValue = registry.lookup(BLAZE_ROD_ID);
        if (blazeRodValue == null) return null;
        double fraction = (double) remaining / FULL_FUEL_TICKS;
        return blazeRodValue.scale(fraction);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        int remaining = getTicksRemaining(stack);
        return remaining > 0 && remaining < FULL_FUEL_TICKS;
    }

    /** Returns the durability bar width (0-13) based on remaining fuel. */
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(getTicksRemaining(stack) * 13.0f / FULL_FUEL_TICKS);
    }

    /** Returns a warm orange color for the fuel bar. */
    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF6600;
    }
}
