package com.xeno.goo.enchantments;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;

public class Geomancy extends Enchantment
{
    public Geomancy()
    {
        super(Rarity.VERY_RARE, EnchantmentTypes.VALID_FOR_GEOMANCY, new EquipmentSlotType[] {EquipmentSlotType.MAINHAND});
    }
}
