package com.xeno.goo.enchantments;

import com.xeno.goo.items.ComboGauntlet;
import com.xeno.goo.items.Crucible;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.MobiusCrucible;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;

public class Armstrong extends Enchantment
{
    public Armstrong()
    {
        super(Rarity.RARE,
                EnchantmentType.create("gauntlet", i -> i.getItem() instanceof Gauntlet || i.getItem() instanceof ComboGauntlet),
                new EquipmentSlotType[] { EquipmentSlotType.MAINHAND });
    }
}
