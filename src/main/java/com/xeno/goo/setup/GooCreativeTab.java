package com.xeno.goo.setup;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.blocks.CrystalBlock;
import com.xeno.goo.items.CrystallizedGooAbstract;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import java.util.Comparator;
import java.util.function.Function;

public class GooCreativeTab extends ItemGroup
{
    public GooCreativeTab(String label)
    {
        super(label);
    }

    @Override
    public ItemStack createIcon()
    {
        return new ItemStack(BlocksRegistry.Solidifier.get());
    }

    public static final NonNullList<ItemStack> sortedCreativeItems = NonNullList.create();

    private static final Function<ItemStack, Boolean> isNotShard = (i) -> !(i.getItem() instanceof CrystallizedGooAbstract);
    private static final Function<ItemStack, Boolean> isNotBlock = (i) -> !(i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof CrystalBlock);
    private static final Function<ItemStack, Boolean> isNotShardOrBlockOrBucket = (i) -> !(i.getItem() instanceof CrystallizedGooAbstract) &&
            !(i.getItem() instanceof BlockItem && ((BlockItem)i.getItem()).getBlock() instanceof CrystalBlock) && !(i.getItem() instanceof BucketItem);
    private static final Function<ItemStack, Integer> shardSize = (i) -> (i.getItem() instanceof CrystallizedGooAbstract ? ((CrystallizedGooAbstract)i.getItem()).amount() : 0);
    private static final Function<ItemStack, String> registryName = (i) -> i.getItem().getRegistryName().toString();
    private static final Function<ItemStack, String> gooTypeName = (i) ->
    {
        boolean isShard = !isNotShard.apply(i);
        String regName = i.getItem().getRegistryName().toString();
        int subStr = isShard ? regName.indexOf("_goo_") : 0;
        return isShard ? regName.substring(0, subStr - 1) : regName;
    };

    @Override
    public void fill(NonNullList<ItemStack> items)
    {
        if (sortedCreativeItems.size() == 0) {
            super.fill(items);
            items.sort(Comparator
                    .comparing(isNotShardOrBlockOrBucket).reversed()
                    .thenComparing(gooTypeName)
                    .thenComparing(shardSize)
                    .thenComparing(registryName)
            );
            sortedCreativeItems.addAll(items);
        } else {
            items.addAll(sortedCreativeItems);
        }
    }

}
