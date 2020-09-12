package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.enchantments.Geomancy;
import com.xeno.goo.enchantments.Holding;
import com.xeno.goo.events.TooltipHandler;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import javax.annotation.Nullable;
import java.util.List;

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
        return isEnchantable(stack) && (enchantment instanceof Holding || enchantment instanceof Geomancy);
    }

    public static int holding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING.get(), stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        int holdsAmount = GauntletAbstractionCapability.storageForDisplay(stack);
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds")
                .append(TooltipHandler.getGooAmountForDisplay(holdsAmount))
                .append(new TranslationTextComponent("goo.common.mb"))
        );
    }
}