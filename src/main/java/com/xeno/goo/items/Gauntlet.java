package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.network.GooGrabPacket;
import com.xeno.goo.network.GooLobPacket;
import com.xeno.goo.network.Networking;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.item.UseAction;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class Gauntlet extends GooHolder
{
    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }
        PlayerEntity player = context.getPlayer();
        if (player == null || player.isHandActive()) {
            return ActionResultType.PASS;
        }
        if (!stack.equals(player.getHeldItem(context.getHand()))) {
            return ActionResultType.PASS;
        }
        // try removing the goo from the container
        ActionResultType result = data(stack).tryGooDrainBehavior(stack, context);
        // if we found some, spawn it into the world and attach it to the player
        if (!data(stack).heldGoo().isEmpty()) {
            Hand hand = context.getHand();
            player.setActiveHand(hand);
            GooHolder gh = (GooHolder)stack.getItem();
            GooEntity e = gh.data(stack).trySpawningGoo(player.world, player, hand);
            if (e != null) {
                e.attachGooToSender(player);
            }
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
        if (!worldIn.isRemote()) {
            return;
        }
        if (!(player instanceof ClientPlayerEntity)) {
            return;
        }
        // request the server to lob the goo, client side handling seems to be more consistent than alternatives.
        Optional<GooEntity> entity = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(8d), p -> p.isHeld() && p.owner() == player).stream().findFirst();
        entity.ifPresent(e -> Networking.sendToServer(new GooLobPacket(e), (ClientPlayerEntity)player));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, @NotNull Hand handIn)
    {
        if (hasGoo(player)) {
            player.setActiveHand(handIn);
            return ActionResult.resultSuccess(player.getHeldItem(handIn));
        } else {
            findAndAttachGoo(player);
            if (hasGoo(player)) {
                player.setActiveHand(handIn);
            }
        }
        return ActionResult.resultSuccess(player.getHeldItem(handIn));
    }

    private boolean hasGoo(PlayerEntity player)
    {
        Optional<GooEntity> entity = player.world.getEntitiesWithinAABB(GooEntity.class, player.getBoundingBox().grow(8d), p -> p.isHeld() && p.owner() == player).stream().findFirst();
        return entity.isPresent();
    }

    private void findAndAttachGoo(PlayerEntity player)
    {
        if (!(player instanceof ClientPlayerEntity)) {
            return;
        }
        Vector3d eyesVector = new Vector3d(player.getPosX(), player.getPosYEye(), player.getPosY());

        AxisAlignedBB eyesBox = new AxisAlignedBB(eyesVector.add(-0.1d, -0.1d, -0.1d), eyesVector.add(0.1d, 0.1d, 0.1d));
        Optional<GooEntity> entity = player.world.getEntitiesWithinAABB(GooEntity.class, eyesBox.expand(player.getLookVec().scale(5f)), p -> !p.isLaunched() && !p.isHeld()).stream().distinct().findFirst();
        entity.ifPresent(e -> Networking.sendToServer(new GooGrabPacket(e), (ClientPlayerEntity)player));
    }

    @Override
    public float armstrongMultiplier()
    {
        return 1.4f;
    }

    @Override
    public float thrownSpeed()
    {
        return 2f;
    }

    @Override
    public int capacity()
    {
        return 125;
    }

    @Override
    public int holdingMultiplier()
    {
        return 2;
    }
}
