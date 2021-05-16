package com.xeno.goo.enchantments;

import com.xeno.goo.items.Basin;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;

public class Containment extends Enchantment
{
    private static String _id = null;
    public static String id() {
        if (!Registry.CONTAINMENT.isPresent()) {
            throw new IllegalStateException("Tried to access Containment enchantment Id before Registry is populated.");
        }

        if (_id == null) {
            _id = Registry.CONTAINMENT.getId().toString();
        }

        return _id;
    }
    public Containment()
    {
        super(Rarity.COMMON, EnchantmentTypes.VALID_FOR_CONTAINMENT, new EquipmentSlotType[] {EquipmentSlotType.MAINHAND});
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel)
    {
        return 1 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel)
    {
        return getMinEnchantability(enchantmentLevel) + 15;
    }

    @Override
    public int getMaxLevel()
    {
        return 4;
    }

    @Override
    public boolean canApply(ItemStack stack)
    {
        return stack.getItem() instanceof Basin || stack.getItem() instanceof Gauntlet || stack.getItem().equals(ItemsRegistry.GOO_BULB.get());
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack)
    {
        return canApply(stack);
    }

    @Override
    public boolean canGenerateInLoot()
    {
        return true;
    }

    @Override
    public boolean canVillagerTrade()
    {
        return false;
    }

    @Override
    protected boolean canApplyTogether(Enchantment ench)
    {
        return false;
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
