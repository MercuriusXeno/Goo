package com.xeno.goo.enchantments;

import com.xeno.goo.items.Crucible;
import com.xeno.goo.items.MobiusCrucible;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.inventory.EquipmentSlotType;

public class Holding extends Enchantment
{
    public Holding()
    {
        super(Rarity.RARE,
                EnchantmentType.create("crucible", i -> i.getItem() instanceof Crucible || i.getItem() instanceof MobiusCrucible),
                new EquipmentSlotType[] { EquipmentSlotType.OFFHAND });
    }
}
