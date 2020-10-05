package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.ItemFluidContainer;

public class GauntletAbstraction extends ItemFluidContainer
{
    public GauntletAbstraction()
    {
        super(
                new Item.Properties()
                        .maxStackSize(1)
                        .isImmuneToFire()
                        .group(GooMod.ITEM_GROUP), 0);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt)
    {
        return new GauntletAbstractionCapability(stack);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return ActionResultType.PASS;
        }

        ActionResultType result = tryBlockInteraction(cap, context);
        return result;
    }

    private ActionResultType tryBlockInteraction(IFluidHandlerItem cap, ItemUseContext context)
    {
        TileEntity t = context.getWorld().getTileEntity(context.getPos());
        if (!(t instanceof GooContainerAbstraction)) {
            return ActionResultType.PASS;
        }

        // special caller for getting the "right" capability, this is mainly for *mixers* having two caps
        IFluidHandler tileCap = ((GooContainerAbstraction)t).getCapabilityFromRayTraceResult(context.getHitVec(), context.getFace(), RayTraceTargetSource.GAUNTLET);

        FluidStack hitFluid = ((GooContainerAbstraction) t).getGooFromTargetRayTraceResult(context.getHitVec(), context.getFace(), RayTraceTargetSource.GAUNTLET);
        // if cap is empty try a drain.
        if (cap.getFluidInTank(0).isEmpty()) {
            return tryCoatingBareGauntlet(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
        }

        boolean isAltBehavior = context.getPlayer() != null && context.getPlayer().isSneaking();

        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!isAltBehavior || !cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.getFluidInTank(0).getAmount() == cap.getTankCapacity(0)) {
            return tryFillingGooContainer(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
        }

        return tryCoatingGauntletWithSameFluid(context.getWorld(), context.getHitVec(), context.getPlayer(), cap, tileCap, hitFluid);
    }

    private ActionResultType tryFillingGooContainer(World world, Vector3d pos, PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack sendingFluid = cap.getFluidInTank(0).copy();
        int amountSent = tileCap.fill(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (amountSent == 0) {
            return ActionResultType.PASS;
        }
        if (amountSent < sendingFluid.getAmount()) {
            sendingFluid.setAmount(amountSent);
        }
        FluidStack drainResult = cap.drain(sendingFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return ActionResultType.PASS;
        }

        if (!world.isRemote()) {
            tileCap.fill(cap.drain(sendingFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_DEPOSIT_SOUND.get(), 1.0f);
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryCoatingGauntletWithSameFluid(World world, Vector3d pos, PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        int amountRequested = cap.getTankCapacity(0) - cap.getFluidInTank(0).getAmount();
        FluidStack requestFluid = hitFluid.copy();
        requestFluid.setAmount(Math.min(requestFluid.getAmount(), amountRequested));
        FluidStack drainResult = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (drainResult.isEmpty()) {
            return ActionResultType.PASS;
        }
        int fillResult = cap.fill(drainResult, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return ActionResultType.PASS;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryCoatingBareGauntlet(World world, Vector3d pos,  PlayerEntity player,
            IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
    {
        FluidStack requestFluid = hitFluid.copy();
        if (requestFluid.getAmount() > cap.getTankCapacity(0)) {
            requestFluid.setAmount(cap.getTankCapacity(0));
        }
        FluidStack result = tileCap.drain(requestFluid, IFluidHandler.FluidAction.SIMULATE);
        if (result.isEmpty()) {
            return ActionResultType.PASS;
        }
        int fillResult = cap.fill(result, IFluidHandler.FluidAction.SIMULATE);
        if (fillResult == 0) {
            return ActionResultType.PASS;
        }

        if (!world.isRemote()) {
            cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), 1.0f);
        return ActionResultType.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand)
    {
        if (world.isRemote()) {
            return ActionResult.resultPass(player.getHeldItem(hand));
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(player.getHeldItem(hand));
        if (cap == null) {
            return ActionResult.resultPass(player.getHeldItem(hand));
        }

        if (cap.getFluidInTank(0).isEmpty()) {
            tryRaidingInventoryForGoo(player, cap);
            return ActionResult.resultPass(player.getHeldItem(hand));
        }

        // we try to get the full amount of drain but a smaller fluidstack just means a smaller, weaker projectile
        int drainAmountThrown = GooMod.config.thrownGooAmount(cap.getFluidInTank(0).getFluid());
        // -1 is disabled
        if (drainAmountThrown == -1) {
            return ActionResult.resultPass(player.getHeldItem(hand));
        }
        FluidStack thrownStack = cap.drain(drainAmountThrown, IFluidHandler.FluidAction.EXECUTE);
        world.addEntity(new GooBlob(Registry.GOO_BLOB.get(), world, player, thrownStack));
        AudioHelper.playerAudioEvent(player, Registry.GOO_LOB_SOUND.get(), 1.0f);
        return ActionResult.resultSuccess(player.getHeldItem(hand));
    }

    private void tryRaidingInventoryForGoo(PlayerEntity player,IFluidHandlerItem cap)
    {
        for(ItemStack i : player.inventory.mainInventory) {
            if (!(i.getItem() instanceof Basin)) {
                continue;
            }
            LazyOptional<IFluidHandlerItem> basinCap = i.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
            boolean[] foundFluid = {false};
            basinCap.ifPresent((c) ->
                    foundFluid[0] = tryDrain(c, cap)
            );
            if (foundFluid[0]) {
                break;
            }
        }
    }

    private boolean tryDrain(IFluidHandlerItem source, IFluidHandlerItem destination)
    {
        if (source.drain(destination.getTankCapacity(0), IFluidHandler.FluidAction.SIMULATE).isEmpty()) {
            return false;
        }
        destination.fill(source.drain(destination.getTankCapacity(0), IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    @Override
    public int getMaxDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null) {
            return 0;
        }
        return fh.getTankCapacity(0) + 1;
    }

    @Override
    public int getDamage(ItemStack stack)
    {
        IFluidHandlerItem fh = FluidHandlerHelper.capability(stack);
        if (fh == null || fh.getFluidInTank(0).isEmpty()) {
            return 0;
        }
        return fh.getTankCapacity(0) - fh.getFluidInTank(0).getAmount();
    }
}
