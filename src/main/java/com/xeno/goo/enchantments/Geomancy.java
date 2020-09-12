package com.xeno.goo.enchantments;

import com.xeno.goo.items.Gauntlet;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Geomancy extends Enchantment
{
    public Geomancy()
    {
        super(Rarity.UNCOMMON, EnchantmentTypes.VALID_FOR_GEOMANCY, new EquipmentSlotType[] {EquipmentSlotType.MAINHAND});
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel)
    {
        return 25;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return getMinEnchantability(enchantmentLevel) + 35;
    }

    @Override
    public int getMaxLevel()
    {
        return 1;
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof Gauntlet;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack)
    {
        return canApply(stack);
    }

    @Override
    public boolean canGenerateInLoot()
    {
        return  false;
    }

    @Override
    public boolean canVillagerTrade()
    {
        return false;
    }

    // legit there's only like one other thing at the time of writing this will jive with, just return true.
    @Override
    protected boolean canApplyTogether(Enchantment ench)
    {
        return ench instanceof Holding;
    }

    // I don't want to make a mess of books, to be honest. There's enough results in ench already without
    // gumming them up.
    @Override
    public boolean isAllowedOnBooks()
    {
        return false;
    }

    @Override
    public boolean isCurse()
    {
        return false;
    }

    @Override
    public int getMinLevel()
    {
        return 1;
    }
}
