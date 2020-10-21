package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.gui.GooRadial;
import com.xeno.goo.enchantments.Containment;
import com.xeno.goo.entities.GooSplat;
import com.xeno.goo.events.ForgeClientEvents;
import com.xeno.goo.events.TargetingHandler;
import com.xeno.goo.network.*;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class Gauntlet extends GauntletAbstraction
{
    private static final int RADIAL_MENU_DELAY = 10;

    public Gauntlet()
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
        return isEnchantable(stack) && (enchantment instanceof Containment );
    }

    public static int containment(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), stack);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        int holdsAmount = GauntletAbstractionCapability.storageForDisplay(stack);
        super.addInformation(stack, worldIn, tooltip, flagIn);
        tooltip.add(new TranslationTextComponent("goo.common.holds")
                .append(TargetingHandler.getGooAmountForDisplay(holdsAmount))
                .append(new TranslationTextComponent("goo.common.mb"))
        );
    }

    @Override
    public void onUsingTick(ItemStack stack, LivingEntity player, int count) {
        if (!(player instanceof ClientPlayerEntity)) {
            return;
        }
        if (!player.getHeldItem(player.getActiveHand()).getItem().equals(this)) {
            return;
        }
        if (ticksHeld(count) >= RADIAL_MENU_DELAY && Minecraft.getInstance().currentScreen == null) {
            Minecraft.getInstance().displayGuiScreen(new GooRadial(player.getActiveHand()));
        }
    }

    private int ticksHeld(int count) {
        return Integer.MAX_VALUE - count;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, LivingEntity player, int timeLeft) {
        if (!(player instanceof ClientPlayerEntity)) {
            return;
        }
        if (!player.getHeldItem(player.getActiveHand()).getItem().equals(this)) {
            return;
        }
        if (ticksHeld(timeLeft) >= RADIAL_MENU_DELAY) {
            return;
        }
        GooHandlingHelper.tryUsingGauntletOrBasin((ClientPlayerEntity)player, player.getActiveHand());
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
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context) {
        return GooHandlingHelper.tryBlockInteraction(context);
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