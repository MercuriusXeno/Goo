package com.xeno.goo.blocks;

import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.BulbTile;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class BulbItem extends BlockItem
{
    public BulbItem(Block blockIn, Properties builder)
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
        return 15;
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
        if (worldIn == null) {
            return;
        }
        int holdsAmount = BulbTile.storageForDisplay(containment(stack));
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds_amount", TargetingHandler.getGooAmountForDisplay(holdsAmount)));
    }

    @Override
    public void inventoryTick(ItemStack stack, World worldIn, Entity entityIn, int itemSlot, boolean isSelected) {
        super.inventoryTick(stack, worldIn, entityIn, itemSlot, isSelected);

        reconcileEnchantmentLevelFromBlockEntityTag(stack);
    }

    private void reconcileEnchantmentLevelFromBlockEntityTag(ItemStack stack) {
        int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
        if (enchantmentLevel > 0) {
            return;
        }
        if (stack.hasTag()) {
            CompoundNBT tag = stack.getTag();
            if (tag.contains("BlockEntityTag")) {
                CompoundNBT eTag = tag.getCompound("BlockEntityTag");
                if (eTag.contains(Containment.id())) {
                    int blockEntityEnchantmentLevel = eTag.getInt(Containment.id());
                    if (enchantmentLevel != blockEntityEnchantmentLevel) {
                        stack.addEnchantment(Registry.CONTAINMENT.get(), blockEntityEnchantmentLevel);
                        // remove the tag so that we can't accidentally create an EXP exploit
                        eTag.remove(Containment.id());
                        tag.put("BlockEntityTag", eTag);
                        stack.setTag(tag);
                    }
                }
            }
        }
    }

    public static int containment(ItemStack stack)
    {
        int enchantmentLevel = EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
        if (stack.hasTag()) {
            CompoundNBT tag = stack.getTag();
            if (tag.contains("BlockEntityTag")) {
                CompoundNBT eTag = tag.getCompound("BlockEntityTag");
                if (eTag.contains(Containment.id())) {
                    return eTag.getInt(Containment.id());
                }
            }
        }
        return enchantmentLevel;
    }
}
