package com.xeno.goo.blocks;

import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.events.TooltipHandler;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTileAbstraction;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class GooBulbItem extends BlockItem
{
    public GooBulbItem(Block blockIn, Properties builder)
    {
        super(blockIn, builder);
    }

    @Override
    public boolean isEnchantable(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantments(stack).size() == 0;
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

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        int holdsAmount = GooBulbTileAbstraction.storageForDisplay(holding(stack));
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds")
                .append(TooltipHandler.getGooAmountForDisplay(holdsAmount))
                .append(new TranslationTextComponent("goo.common.mb"))
        );
    }

    private int holding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
    }
}
