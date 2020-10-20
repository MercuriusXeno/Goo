package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent;

import java.util.function.Supplier;

public class CrystallizedGooAbstract extends Item {
    private final Supplier<GooFluid> gooType;
    private final Supplier<Item> crystalFrom;
    private final int gooValue;
    public CrystallizedGooAbstract(Supplier<GooFluid> gooType, Supplier<Item> crystalFrom, int value) {
        super(new Properties().group(GooMod.ITEM_GROUP));
        this.gooType = gooType;
        this.crystalFrom = crystalFrom;
        this.gooValue = value;
    }

    public Item source() { return crystalFrom.get(); }

    public Fluid gooType() {
        return gooType.get();
    }

    public int amount() {
        return gooValue;
    }

    @Override
    public int getBurnTime(ItemStack itemStack) {
        return this.gooType.equals(Registry.MOLTEN_GOO) ? gooValue * 20 : 0;
    }
}
