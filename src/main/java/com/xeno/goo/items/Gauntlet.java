package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Gauntlet extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }
        if (context.getPlayer() != null && context.getPlayer().isHandActive()) {
            return ActionResultType.PASS;
        }
        ActionResultType result = data(stack).tryGooDrainBehavior(stack, context);
        if (!data(stack).heldGoo().isEmpty()) {
            makeAndAttachGoo(context.getPlayer().world, context.getPlayer(), context.getHand());
        }
        return result;
    }

    @Override
    public UseAction getUseAction(ItemStack stack)
    {
        return UseAction.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return 72000;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World worldIn, LivingEntity player, int timeLeft)
    {
        super.onPlayerStoppedUsing(stack, worldIn, player, timeLeft);
        if (worldIn.isRemote()) {
            return;
        }
        List<GooEntity> entities = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(1.0d), p -> p.isHeld() && p.owner() == player);
        for(GooEntity e : entities) {
            e.detachGooFromSender(true);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, @NotNull Hand handIn)
    {
        if (world.isRemote()) {
            return ActionResult.resultPass(player.getHeldItem(handIn));
        }
        makeAndAttachGoo(world, player, handIn);
        return ActionResult.resultSuccess(player.getHeldItem(handIn));
    }

    private void makeAndAttachGoo(World world, PlayerEntity player, @NotNull Hand handIn)
    {
        if (world.isRemote() || player.isHandActive()) {
            return;
        }
        if (player.getHeldItem(handIn).isEmpty() || !(player.getHeldItem(handIn).getItem() instanceof GooHolder)) {
            return;
        }
        player.setActiveHand(handIn);
        ((GooHolder)player.getHeldItem(handIn).getItem()).data(player.getHeldItem(handIn)).trySpawningGoo(player.world, player, Hand.MAIN_HAND);
    }

    @Override
    public double armstrongMultiplier()
    {
        return GooMod.config.gauntletPowerMultiplier();
    }

    @Override
    public double thrownSpeed()
    {
        return GooMod.config.gauntletLobVelocity();
    }

    @Override
    public int capacity()
    {
        return GooMod.config.gauntletBaseCapacity();
    }

    @Override
    public int holdingMultiplier()
    {
        return GooMod.config.gauntletHoldingMultiplier();
    }
}
