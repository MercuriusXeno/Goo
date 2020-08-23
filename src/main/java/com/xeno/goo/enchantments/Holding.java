package com.xeno.goo.enchantments;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Holding extends Enchantment
{
    public Holding()
    {
        super(Rarity.COMMON,
                GooHolder.ENCHANTMENT_TYPE,
                new EquipmentSlotType[] { EquipmentSlotType.MAINHAND, EquipmentSlotType.OFFHAND });
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof GooHolder;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return 3;
    }
}
