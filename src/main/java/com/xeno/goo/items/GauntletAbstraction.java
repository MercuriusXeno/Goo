package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooContainerAbstraction;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.ItemFluidContainer;

public class GauntletAbstraction extends ItemFluidContainer
{
    public GauntletAbstraction(int capacity)
    {
        super(
                new Item.Properties()
                        .maxStackSize(1)
                        .isBurnable()
                        .group(GooMod.ITEM_GROUP),
                capacity);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, CompoundNBT nbt)
    {
        return new GauntletAbstractionCapability(stack, this.capacity);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }
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
            return tryCoatingBareGauntlet(cap, tileCap, hitFluid);
        }
        // the fluid we contain isn't the type hit or it is, but our receptacle is full so the intent is inverted.
        if (!cap.getFluidInTank(0).isFluidEqual(hitFluid) || cap.getFluidInTank(0).getAmount() == cap.getTankCapacity(0)) {
            return tryFillingGooContainer(cap, tileCap, hitFluid);
        }

        return tryCoatingGauntletWithSameFluid(cap, tileCap, hitFluid);
    }

    private ActionResultType tryFillingGooContainer(IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
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
        tileCap.fill(cap.drain(sendingFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryCoatingGauntletWithSameFluid(IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
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
        cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return ActionResultType.SUCCESS;
    }

    private ActionResultType tryCoatingBareGauntlet(IFluidHandlerItem cap, IFluidHandler tileCap, FluidStack hitFluid)
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
        cap.fill(tileCap.drain(requestFluid, IFluidHandler.FluidAction.EXECUTE), IFluidHandler.FluidAction.EXECUTE);
        return ActionResultType.SUCCESS;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, PlayerEntity playerIn, Hand handIn)
    {
        if (worldIn.isRemote()) {
            return ActionResult.resultPass(playerIn.getHeldItem(handIn));
        }

        // tryFlingingGoo();

        return ActionResult.resultPass(playerIn.getHeldItem(handIn));
    }
}
