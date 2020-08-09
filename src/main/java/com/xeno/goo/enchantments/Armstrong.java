package com.xeno.goo.enchantments;

import com.xeno.goo.items.ComboGauntlet;
import com.xeno.goo.items.Gauntlet;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Armstrong extends Enchantment
{
    public Armstrong()
    {
        super(Rarity.RARE,
                EnchantmentType.create("gauntlet", i -> i.getItem() instanceof Gauntlet || i.getItem() instanceof ComboGauntlet),
                new EquipmentSlotType[] { EquipmentSlotType.MAINHAND });
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof Gauntlet || stack.getItem() instanceof ComboGauntlet;
    }
}
