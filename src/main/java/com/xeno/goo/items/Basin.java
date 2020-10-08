package com.xeno.goo.items;

import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class Basin extends BasinAbstraction
{
    public Basin()
    {
        super();
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantments(stack).size() == 0;
    }

    // enchantment effect makes it really hard to see goo colors, so turn that off.
    @Override
    public boolean hasEffect(ItemStack stack)
    {
        return false;
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
        return isEnchantable(stack) && enchantment instanceof Containment;
    }

    public static int holding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        int holdsAmount = BasinAbstractionCapability.storageForDisplay(stack);
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds")
                .append(TargetingHandler.getGooAmountForDisplay(holdsAmount))
                .append(new TranslationTextComponent("goo.common.mb"))
        );
    }
}