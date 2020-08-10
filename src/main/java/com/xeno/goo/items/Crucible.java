package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

public class Crucible extends Item implements IGooHolder
{
    public Crucible()
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }

    private IFluidHandler tryGettingBulbCapabilities(GooBulbTile bulb, Direction dir)
    {
        LazyOptional<IFluidHandler> lazyCap = bulb.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, dir);
        IFluidHandler cap = null;
        try {
            cap = lazyCap.orElseThrow(() -> new Exception("Fluid handler expected from a tile entity that didn't contain one!"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cap;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        GooHolder cap = GooHolder.read(stack);
        cap.addInformation(tooltip);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        if (context.getWorld().isRemote()) {
            return ActionResultType.SUCCESS;
        }

        BlockPos posHit = context.getPos();
        // context sensitive select goo from tank and pull it, if empty
        // or a Mobius (Mobius pulls anytime as long as it has space)

        TileEntity te = context.getWorld().getTileEntity(posHit);
        if (te instanceof GooBulbTile) {
            GooBulbTile bulb = (GooBulbTile)te;

            GooHolder crucible = GooHolder.read(stack);
            IFluidHandler bulbCap = tryGettingBulbCapabilities(bulb, Direction.DOWN);

            // if the crucible is empty, we're definitely taking or doing nothing
            if (!crucible.goo().get(0).isEmpty()) {
                FluidStack goo = bulb.getSpecificGooType(crucible.goo().get(0).getFluid());
                int amountToPull = goo.getAmount();
                amountToPull = Math.min(crucible.getCapacity(stack, goo), amountToPull);
                if (amountToPull == 0) {
                } else {
                    FluidStack result = bulbCap.drain(new FluidStack(goo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    crucible.fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                Vector3d hitVec = context.getHitVec();
                Direction side = context.getFace();
                FluidStack goo = bulb.getGooCorrespondingTo(hitVec, context.getPlayer().getEyePosition(0f), side);
                int amountToPull = goo.getAmount();
                amountToPull = Math.min(crucible.getCapacity(stack, goo), amountToPull);
                if (amountToPull > 0) {
                    FluidStack result = bulbCap.drain(new FluidStack(goo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    crucible.fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            }

            crucible.updateSelected();

            stack.setTag(crucible.serializeNBT());
        }
        return ActionResultType.SUCCESS;
    }

    @Override
    public int tanks()
    {
        return 1;
    }
}
