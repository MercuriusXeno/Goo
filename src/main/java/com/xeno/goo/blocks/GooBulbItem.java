package com.xeno.goo.blocks;

import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTileAbstraction;
import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
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

    public static CompoundNBT getOrCreateTileTag(ItemStack i, String tileEntityId) {
        CompoundNBT itemTag = new CompoundNBT();
        if (!i.hasTag() || i.getTag() == null) {
            i.setTag(itemTag);
        } else {
            itemTag = i.getTag();
        }

        if (!itemTag.contains("BlockEntityTag")) {
            itemTag.put("BlockEntityTag", new CompoundNBT());
        }

        CompoundNBT bulbTag = itemTag.getCompound("BlockEntityTag");
        bulbTag.putString("id", tileEntityId);
        if (!bulbTag.contains("goo")) {
            CompoundNBT gooTag = new CompoundNBT();
            gooTag.putInt("count", 0);
            bulbTag.put("goo", gooTag);
        }
        itemTag.put("BlockEntityTag", bulbTag);
        i.setTag(itemTag);

        return bulbTag;
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
        if (worldIn == null) {
            return;
        }
        int holdsAmount = GooBulbTileAbstraction.storageForDisplay(holding(stack));
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds")
                .append(TargetingHandler.getGooAmountForDisplay(holdsAmount))
                .append(new TranslationTextComponent("goo.common.mb"))
        );
    }

    private int holding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
    }
}
