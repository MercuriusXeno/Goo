package com.xeno.goo.enchantments;

import com.xeno.goo.GooMod;
import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.enchantment.PowerEnchantment;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Holding extends Enchantment
{

    public Holding()
    {
        super(Rarity.COMMON, EnchantmentTypes.VALID_FOR_HOLDING, new EquipmentSlotType[] {EquipmentSlotType.MAINHAND});
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel)
    {
        return 1 + (enchantmentLevel - 1) * 6;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return getMinEnchantability(enchantmentLevel) + 6;
    }

    @Override
    public int getMaxLevel()
    {
        return 4;
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof Basin || stack.getItem() instanceof Gauntlet || stack.getItem().equals(Registry.GOO_BULB_ITEM.get());
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
        return true;
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
