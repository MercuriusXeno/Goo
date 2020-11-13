package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public class Basin extends BasinAbstraction
{
    private static final int RADIAL_MENU_DELAY = 10;

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

    public static int containment(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        int holdsAmount = BasinAbstractionCapability.storageForDisplay(stack);
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds_amount", TargetingHandler.getGooAmountForDisplay(holdsAmount)));
    }

    @Override
    public void onUsingTick(ItemStack stack, LivingEntity player, int count) {
        if (!player.getHeldItem(player.getActiveHand()).getItem().equals(this)) {
            return;
        }
        if (ticksHeld(count) >= RADIAL_MENU_DELAY) {
            GooMod.proxy.openRadialMenu(player);
            player.stopActiveHand();
        }
    }

    private int ticksHeld(int count) {
        return Integer.MAX_VALUE - count;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, LivingEntity player, int timeLeft) {
        if ((player instanceof ClientPlayerEntity)) {
            return;
        }
        if (!player.getHeldItem(player.getActiveHand()).getItem().equals(this)) {
            return;
        }
        if (ticksHeld(timeLeft) >= RADIAL_MENU_DELAY) {
            return;
        }
        if (player instanceof ServerPlayerEntity) {
            BlockRayTraceResult trace = rayTrace(worldIn, (ServerPlayerEntity) player, RayTraceContext.FluidMode.ANY);
            if (GooHandlingHelper.tryBlockInteraction(new ItemUseContext((ServerPlayerEntity)player,
                    player.getActiveHand(), trace))) {
                return;
            }
            GooHandlingHelper.tryUsingGauntletOrBasin((ServerPlayerEntity)player, player.getActiveHand());
        }
        super.onPlayerStoppedUsing(stack, worldIn, player, timeLeft);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);

        playerIn.setActiveHand(handIn);
        return ActionResult.resultFail(itemstack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }
}