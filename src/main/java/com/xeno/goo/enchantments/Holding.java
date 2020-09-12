package com.xeno.goo.enchantments;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;

public class Holding extends Enchantment
{

    public Holding()
    {
        super(Rarity.COMMON, EnchantmentTypes.VALID_FOR_HOLDING, new EquipmentSlotType[] {EquipmentSlotType.MAINHAND});
    }


}