package com.xeno.goo.enchantments;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.GooHolder;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Armstrong extends Enchantment
{
    public Armstrong()
    {
        super(Rarity.RARE,
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
        return GooMod.config.maxArmstrongEnchantment();
    }
}
