package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.Objects;

public class GooAndYou extends Item
{
    public GooAndYou()
    {
        super(new Item.Properties()
            .maxStackSize(1)
            .group(GooMod.ITEM_GROUP)
            .isImmuneToFire()
        );
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        if (worldIn.isRemote()) {
            return ActionResult.resultPass(playerIn.getHeldItem(handIn));
        }
        ServerPlayerEntity player = (ServerPlayerEntity)playerIn;
        PatchouliAPI.get().openBookGUI(player, Objects.requireNonNull(ItemsRegistry.GOO_AND_YOU.get().getRegistryName()));
        return super.onItemRightClick(worldIn, playerIn, handIn);
    }
}
