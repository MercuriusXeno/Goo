package com.xeno.goo.enchantments;

import com.xeno.goo.items.ComboGauntlet;
import com.xeno.goo.items.Crucible;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.MobiusCrucible;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Holding extends Enchantment
{
    public Holding()
    {
        super(Rarity.COMMON,
                EnchantmentType.create("crucible", i -> i.getItem() instanceof Crucible || i.getItem() instanceof MobiusCrucible),
                new EquipmentSlotType[] { EquipmentSlotType.OFFHAND });
    }

    @Override
    public boolean canApply(ItemStack stack)
    {

                return stack.getItem() instanceof Gauntlet || stack.getItem() instanceof ComboGauntlet
                        || stack.getItem() instanceof Crucible || stack.getItem() instanceof MobiusCrucible;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return 5;
    }


}
