package com.xeno.goo.enchantments;

import com.xeno.goo.GooMod;
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
                EnchantmentType.create("gauntlet", i -> i.getItem() instanceof Gauntlet),
                new EquipmentSlotType[] { EquipmentSlotType.MAINHAND });
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof Gauntlet;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return GooMod.config.maxArmstrongEnchantment();
    }
}
