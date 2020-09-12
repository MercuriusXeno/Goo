package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.enchantments.Geomancy;
import com.xeno.goo.enchantments.Holding;
import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Gauntlet extends GauntletAbstraction
{
    public Gauntlet()
    {
        super(GooMod.config.gauntletCapacity());
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantments(stack).size() == 0;
    }

    @Override
    public int getItemEnchantability()
    {
        return 30;
    }

    @Override
    public int getItemEnchantability(ItemStack stack)
    {
        return getItemEnchantability();
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return isEnchantable(stack) && (enchantment instanceof Holding || enchantment instanceof Geomancy);
    }
}