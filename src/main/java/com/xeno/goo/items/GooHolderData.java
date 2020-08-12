package com.xeno.goo.items;

import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class GooHolderData
{
    private FluidStack heldGoo;

    public GooHolderData()
    {
        heldGoo = FluidStack.EMPTY;
    }

    protected IFluidHandler tryGettingBulbCapabilities(GooBulbTile bulb, Direction dir)
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

    @Nonnull
    public FluidStack drain(ItemStack stack, int maxDrain, IFluidHandler.FluidAction action)
    {
        if (heldGoo.isEmpty()) {
            return FluidStack.EMPTY;
        }

        if (heldGoo.getAmount() > maxDrain) {
            FluidStack result = heldGoo.copy();
            result.setAmount(maxDrain);
            if (action == IFluidHandler.FluidAction.SIMULATE) {
                return result;
            }
            heldGoo.setAmount(heldGoo.getAmount() - maxDrain);
            stack.setTag(serializeNBT());
            return result;
        }

        FluidStack result = heldGoo.copy();
        if (action == IFluidHandler.FluidAction.SIMULATE) {
            return result;
        }
        heldGoo = FluidStack.EMPTY;
        stack.setTag(serializeNBT());
        return result;
    }

    public void deserializeNBT(CompoundNBT tag)
    {
        heldGoo = FluidStack.loadFluidStackFromNBT(tag);
    }

    public CompoundNBT serializeNBT()
    {
        return heldGoo.writeToNBT(new CompoundNBT());
    }

    protected int getCapacity(ItemStack stack, FluidStack resource)
    {
        if (heldGoo.isEmpty()) {
            return capacity(stack);
        }

        if (heldGoo.isFluidEqual(resource)) {
            return capacity(stack) - heldGoo.getAmount();
        }

        return 0;
    }

    public int fill(ItemStack stack, FluidStack resource, IFluidHandler.FluidAction action)
    {
        int spaceLeft = this.getCapacity(stack, resource);
        int amountTransferred = Math.min(spaceLeft, resource.getAmount());
        if (amountTransferred <= 0) {
            return 0;
        }

        if (action == IFluidHandler.FluidAction.EXECUTE) {
            if (heldGoo.isFluidEqual(resource)) {
                heldGoo.setAmount(heldGoo.getAmount() + amountTransferred);
                stack.setTag(serializeNBT());
                return amountTransferred;
            }

            if (heldGoo.isEmpty()) {
                heldGoo = new FluidStack(resource.getFluid(), amountTransferred);
                stack.setTag(serializeNBT());
                return amountTransferred;
            }
        }
        return amountTransferred;
    }

    public void addInformation(List<ITextComponent> tooltip)
    {
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<FluidStack> sortedValues = new SortedList<>(FXCollections.observableArrayList(heldGoo), Compare.fluidAmountComparator.thenComparing(Compare.fluidNameComparator));
        for(FluidStack v : sortedValues) {
            index++;
            if (v.isEmpty()) {
                continue;
            }
            String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getAmount()) + " mB";
            String fluidTranslationKey = v.getTranslationKey();
            if (fluidTranslationKey == null) {
                continue;
            }
            displayIndex++;
            if (displayIndex % 2 == 1) {
                fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue);
            } else {
                if (fluidAmount != null) {
                    fluidAmount = fluidAmount.appendString(", ").append(new TranslationTextComponent(fluidTranslationKey).appendString(decimalValue));
                }
            }
            if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                tooltip.add(fluidAmount);
            }
        }
    }

    public int capacity(ItemStack stack)
    {
        return (int)Math.ceil(this.baseCapacity(stack) * this.holdingMultiplier(stack));
    }

    public  GooDrainBehavior behavior() {
        return GooDrainBehavior.UNSPECIFIED;
    }

    public int baseCapacity(ItemStack stack) {
        return ((GooHolder)stack.getItem()).capacity();
    }

    public int holdingMultiplier(ItemStack stack) {
        return (int)Math.ceil(Math.pow(((GooHolder)stack.getItem()).holdingMultiplier(), holding(stack)));
    }

    private double baseThrownSpeed(ItemStack stack) {
        return ((GooHolder)stack.getItem()).thrownSpeed();
    }

    private double armstrongMultiplier(ItemStack stack)
    {
        return Math.pow(((GooHolder)stack.getItem()).armstrongMultiplier(), armstrong(stack));
    }

    public int holding(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING_ENCHANTMENT.get(), stack);
    }

    public double armstrong(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Registry.ARMSTRONG_ENCHANTMENT.get(), stack);
    }

    private double thrownSpeed(ItemStack stack)
    {
        return armstrongMultiplier(stack) * baseThrownSpeed(stack);
    }

    public ActionResultType tryGooDrainBehavior(ItemStack stack, ItemUseContext context) {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }

        if (!context.getItem().equals(stack)) {
            return ActionResultType.PASS;
        }

        BlockPos posHit = context.getPos();
        // context sensitive select goo from tank and pull it, if empty
        // or a Mobius (Mobius pulls anytime as long as it has space)

        TileEntity te = context.getWorld().getTileEntity(posHit);
        if (te instanceof GooBulbTile) {
            GooBulbTile bulb = (GooBulbTile)te;
            IFluidHandler bulbCap = tryGettingBulbCapabilities(bulb, Direction.DOWN);

            if (!heldGoo.isEmpty()) {
                FluidStack bulbGoo = bulb.getSpecificGooType(heldGoo.getFluid());
                int amountToPull = Math.min(getCapacity(stack, bulbGoo), bulbGoo.getAmount());
                if (amountToPull == 0) {
                    // try pushing instead
                    int pushed = bulbCap.fill(heldGoo, IFluidHandler.FluidAction.EXECUTE);
                    drain(stack, pushed, IFluidHandler.FluidAction.EXECUTE);

                } else {
                    FluidStack result = bulbCap.drain(new FluidStack(heldGoo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                // pull!
                Vector3d hitVec = context.getHitVec();
                Direction side = context.getFace();
                FluidStack goo = bulb.getGooCorrespondingTo(hitVec, (ServerPlayerEntity)context.getPlayer(), side);
                int amountToPull = goo.getAmount();
                amountToPull = Math.min(getCapacity(stack, goo), amountToPull);
                if (amountToPull > 0) {
                    FluidStack result = bulbCap.drain(new FluidStack(goo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            }

            return ActionResultType.SUCCESS;
        }
        return ActionResultType.PASS;
    }

    public void tryThrowingGoo(World worldIn, LivingEntity livingEntityIn, ItemStack stack)
    {
        if (worldIn.isRemote()) {
            return;
        }

        if (heldGoo.isEmpty()) {
            return;
        }

        worldIn.addEntity(new GooEntity(worldIn, livingEntityIn, heldGoo, thrownSpeed(stack)));
    }

    public FluidStack heldGoo()
    {
        return heldGoo;
    }
}
