package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import javax.annotation.Nonnull;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GooHolder
{
    public GooHolder(int tanks) {
        this.selected = Objects.requireNonNull(Fluids.EMPTY.getRegistryName()).toString();
        goo = NonNullList.withSize(tanks, FluidStack.EMPTY);
    }

    private NonNullList<FluidStack> goo;
    private String selected;

    public static GooHolder read(ItemStack stack)
    {
        CompoundNBT cap = stack.getOrCreateTag();
        int tanks = 1;
        if (stack.getItem() instanceof MobiusCrucible) {
            tanks = 8;
        }
        GooHolder result = new GooHolder(tanks);
        result.deserializeNBT(stack.getTag());
        return result;
    }

    public NonNullList<FluidStack> goo() {
        return goo;
    }



    @Nonnull
    public FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action)
    {
        for(FluidStack stack : goo()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.getFluid().isEquivalentTo(resource.getFluid())) {
                continue;
            }

            FluidStack result = stack.copy();
            result.setAmount(result.getAmount() - Math.min(result.getAmount(), resource.getAmount()));
            if (action == IFluidHandler.FluidAction.EXECUTE) {
                stack.setAmount(result.getAmount());
            }

            return result;
        }
        return FluidStack.EMPTY;
    }

    @Nonnull
    public FluidStack drain(int maxDrain, IFluidHandler.FluidAction action)
    {
        if (selected.equals(Objects.requireNonNull(Fluids.EMPTY.getRegistryName()).toString())) {
            return FluidStack.EMPTY;
        }
        for (int i = 0; i < goo.size(); i++) {
            FluidStack stack = goo.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (!Objects.requireNonNull(stack.getFluid().getRegistryName()).toString().equals(selected)) {
                continue;
            }

            FluidStack result = stack.copy();
            result.setAmount(result.getAmount() - Math.min(result.getAmount(), maxDrain));
            if (action == IFluidHandler.FluidAction.EXECUTE) {
                stack.setAmount(result.getAmount());
                if (stack.getAmount() == 0) {
                    goo.set(i, FluidStack.EMPTY);
                }
            }

            return result;
        }
        return FluidStack.EMPTY;
    }

    public void deserializeNBT(CompoundNBT tag)
    {
        if (tag == null) {
            return;
        }

        this.selected = tag.getString("selected");

        CompoundNBT gooTag = tag.getCompound("goo");
        int count = gooTag.getInt("count");
        for (int i = 0; i < count; i++) {
            Fluid f = Registry.getFluid(gooTag.getString("fluid_name" + i));
            if (f == null) {
                f = Fluids.EMPTY;
            }
            FluidStack s = new FluidStack(f, gooTag.getInt("amount" + i));
            goo.set(i, s);
        }
    }

    public CompoundNBT serializeNBT()
    {
        CompoundNBT tag = new CompoundNBT();
        updateSelected();

        tag.putString("selected", selected);
        CompoundNBT gooTag = new CompoundNBT();
        gooTag.putInt("count", goo.size());
        for (int i = 0; i < goo.size(); i++) {
            gooTag.putString("fluid_name" + i, Objects.requireNonNull(goo.get(i).getFluid().getRegistryName()).toString());
            gooTag.putInt("amount" + i, goo.get(i).getAmount());
        }
        tag.put("goo", gooTag);

        return tag;
    }

    private static int getHolding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING_ENCHANTMENT.get(), stack);
    }

    protected int getCapacity(ItemStack stack, FluidStack resource)
    {
        for (int i = 0; i < goo.size(); i++) {
            if (goo.get(i).isEmpty() || goo.get(i).getFluid().isEquivalentTo(resource.getFluid())) {
                return GooMod.mainConfig.crucibleBaseCapacity() * (int)Math.pow(GooMod.mainConfig.crucibleHoldingMultiplier(), getHolding(stack));
            }
        }
        return 0;
    }

    public int fill(ItemStack stack, FluidStack resource, IFluidHandler.FluidAction action)
    {
        int spaceLeft = this.getCapacity(stack, resource) - this.getGooQuantity(resource);
        int amountTransferred = Math.min(spaceLeft, resource.getAmount());
        if (amountTransferred <= 0) {
            return 0;
        }

        if (action == IFluidHandler.FluidAction.EXECUTE) {
            selected = Objects.requireNonNull(resource.getFluid().getRegistryName()).toString();
            for(FluidStack f : goo) {
                if (f.isFluidEqual(resource)) {
                    f.setAmount(f.getAmount() + amountTransferred);
                    return amountTransferred;
                }
            }
            for (int i = 0; i < goo.size(); i++) {
                FluidStack f = goo.get(i);
                if (f.isEmpty()) {
                    goo.set(i, new FluidStack(resource.getFluid(), amountTransferred));
                    return amountTransferred;
                }
            }
        }
        return amountTransferred;
    }

    private int getGooQuantity(FluidStack resource)
    {
        return goo.stream().filter(g -> g.getFluid().isEquivalentTo(resource.getFluid())).mapToInt(FluidStack::getAmount).sum();
    }

    public void addInformation(List<ITextComponent> tooltip)
    {
        int index = 0;
        int displayIndex = 0;
        IFormattableTextComponent fluidAmount = null;
        // struggling with values sorting stupidly. Trying to do fix sort by doing this:
        List<FluidStack> sortedValues = new SortedList<>(FXCollections.observableArrayList(goo), Compare.fluidAmountComparator.thenComparing(Compare.fluidNameComparator));
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

    public String selected()
    {
        return selected;
    }

    public void updateSelected()
    {
        if (goo.size() == 1) {
            if (goo.get(0).isEmpty()) {
                selected = "";
            } else {
                selected = Objects.requireNonNull(goo.get(0).getFluid().getRegistryName()).toString();
            }
        } else {
            if (Objects.equals(selected, "") || goo.stream().noneMatch(f -> Objects.requireNonNull(f.getFluid().getRegistryName()).toString().equals(selected) && f.getAmount() > 0)) {
                selected = Objects.requireNonNull(goo.stream().max(Compare.fluidAmountComparator).orElse(FluidStack.EMPTY).getFluid().getRegistryName()).toString();
            }
        }
    }
}
