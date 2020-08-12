package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.EnchantmentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.*;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import javax.annotation.Nullable;
import java.util.List;

public abstract class GooHolder extends Item
{
    public static final EnchantmentType ENCHANTMENT_TYPE = EnchantmentType.create("goo_holder", i -> i.getItem() instanceof GooHolder);

    public GooHolder()
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        data(stack).addInformation(tooltip);
    }

    public GooHolderData data(ItemStack stack) {
        GooHolderData holder = new GooHolderData();
        holder.deserializeNBT(stack.getTag());
        return holder;
    }

    public abstract int capacity();

    public abstract int holdingMultiplier();

    public abstract ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context);

    public abstract ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn);

    public abstract double armstrongMultiplier();

    public abstract double thrownSpeed();

}
