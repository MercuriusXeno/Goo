package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.network.DetatchGooFromPlayerPacket;
import com.xeno.goo.network.Networking;
import com.xeno.goo.network.SpawnGooPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
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

public class Gauntlet extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getPlayer() != null && context.getPlayer().isHandActive()) {
            return ActionResultType.PASS;
        }
        ActionResultType result = data(stack).tryGooDrainBehavior(stack, context);
        makeAndAttachGoo(context.getPlayer().world, context.getPlayer(), context.getHand());
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
        if (worldIn.isRemote())
        {
            GooMod.debug("Detaching goo from player/launching");
            Networking.sendToServer(new DetatchGooFromPlayerPacket(true), (ClientPlayerEntity) player);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, @NotNull Hand handIn)
    {

        ItemStack stack = player.getHeldItem(handIn);
        makeAndAttachGoo(world, player, handIn);
        return ActionResult.resultSuccess(stack);
    }

    private void makeAndAttachGoo(World world, PlayerEntity player, @NotNull Hand handIn)
    {
        if (!player.isHandActive() && world.isRemote()) {
            if (Minecraft.getInstance().player == null) {
                return;
            }
            player.setActiveHand(handIn);
            GooMod.debug("Creating and attaching goo to player");
            Networking.sendToServer(new SpawnGooPacket(), Minecraft.getInstance().player);
        }
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
