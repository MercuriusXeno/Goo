package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.GooBulbTile;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
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
import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class GooHolder extends Item implements IGooHolder
{
    private NonNullList<FluidStack> goo;
    private String selected;
    private int tanks;
    private GooDrainBehavior behavior;

    public GooHolder(int tanks, GooDrainBehavior behavior) {
        super(new Item.Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
        this.tanks = tanks;
        this.behavior = behavior;
        this.selected = Objects.requireNonNull(Fluids.EMPTY.getRegistryName()).toString();
        goo = NonNullList.withSize(tanks, FluidStack.EMPTY);

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

    public static GooHolder read(ItemStack stack)
    {
        Item item = stack.getItem();
        if (!(item instanceof GooHolder)) {
            return null;
        }
        GooHolder result = (GooHolder)item;
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
            result.setAmount(Math.min(result.getAmount(), maxDrain));
            if (action == IFluidHandler.FluidAction.EXECUTE) {
                stack.setAmount(stack.getAmount() - result.getAmount());
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
        this.tanks = tag.getInt("tanks");
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
        tag.putInt("tanks", tanks);
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

    private static int getArmstrong(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.ARMSTRONG_ENCHANTMENT.get(), stack);
    }

    public static int getHolding(ItemStack stack)
    {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING_ENCHANTMENT.get(), stack);
    }

    protected int getCapacity(ItemStack stack, FluidStack resource)
    {
        for (int i = 0; i < goo.size(); i++) {
            if (goo.get(i).isEmpty() || goo.get(i).getFluid().isEquivalentTo(resource.getFluid())) {
                return capacity(stack);
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

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);

        GooHolder cap = GooHolder.read(stack);
        cap.addInformation(tooltip);
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
                selected = Objects.requireNonNull(Fluids.EMPTY.getRegistryName()).toString();
            } else {
                selected = Objects.requireNonNull(goo.get(0).getFluid().getRegistryName()).toString();
            }
        } else {
            if (Objects.equals(selected, "") || goo.stream().noneMatch(f -> Objects.requireNonNull(f.getFluid().getRegistryName()).toString().equals(selected) && f.getAmount() > 0)) {
                selected = Objects.requireNonNull(goo.stream().max(Compare.fluidAmountComparator).orElse(FluidStack.EMPTY).getFluid().getRegistryName()).toString();
            }
        }
    }

    @Override
    public int tanks()
    {
        return this.tanks;
    }

    @Override
    public int capacity(ItemStack stack)
    {
        return (int)Math.ceil(this.baseCapacity() * this.enchantmentFactor(stack));
    }

    @Override
    public GooDrainBehavior behavior() {
        return this.behavior;
    };

    @Override
    public abstract int baseCapacity();

    @Override
    public int enchantmentFactor(ItemStack stack) {
        return enchantmentMultiplier(getHolding(stack), holdingMultiplier());
    }

    public int enchantmentMultiplier(int holding, int mult) {
        return (int)Math.ceil(Math.pow(mult, holding));
    }

    public ActionResultType tryGooDrainBehavior(ItemStack stack, ItemUseContext context) {
        if (context.getWorld().isRemote()) {
            return ActionResultType.PASS;
        }

        BlockPos posHit = context.getPos();
        // context sensitive select goo from tank and pull it, if empty
        // or a Mobius (Mobius pulls anytime as long as it has space)

        TileEntity te = context.getWorld().getTileEntity(posHit);
        if (te instanceof GooBulbTile) {
            GooBulbTile bulb = (GooBulbTile)te;

            GooHolder holder = GooHolder.read(stack);
            IFluidHandler bulbCap = tryGettingBulbCapabilities(bulb, Direction.DOWN);

            if (holder == null) {
                return ActionResultType.PASS;
            }
            Fluid f = Registry.getFluid(holder.selected());
            if (f != null && !f.getFluid().isEquivalentTo(Fluids.EMPTY) && !(f instanceof GooBase)) {
                return ActionResultType.PASS;
            }
            GooBase selected = (GooBase)f;

            // if the crucible is empty, we're definitely taking or doing nothing
            if (!holder.goo().get(0).isEmpty()) {
                FluidStack goo = bulb.getSpecificGooType(selected);
                if (goo.isEmpty()) {
                    goo = bulb.getSpecificGooType(holder.goo().get(0).getFluid());
                }
                int amountToPull = goo.getAmount();
                amountToPull = Math.min(holder.getCapacity(stack, goo), amountToPull);
                if (amountToPull == 0) {
                    // try pushing instead
                    FluidStack pushed = holder.drain(bulb.getSpaceRemaining(), IFluidHandler.FluidAction.EXECUTE);
                    bulbCap.fill(pushed, IFluidHandler.FluidAction.EXECUTE);
                } else {
                    FluidStack result = bulbCap.drain(new FluidStack(goo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    holder.fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                Vector3d hitVec = context.getHitVec();
                Direction side = context.getFace();
                FluidStack goo = bulb.getGooCorrespondingTo(hitVec, context.getPlayer().getEyePosition(0f), side);
                int amountToPull = goo.getAmount();
                amountToPull = Math.min(holder.getCapacity(stack, goo), amountToPull);
                if (amountToPull > 0) {
                    FluidStack result = bulbCap.drain(new FluidStack(goo.getFluid(), amountToPull), IFluidHandler.FluidAction.EXECUTE);
                    holder.fill(stack, result, IFluidHandler.FluidAction.EXECUTE);
                }
            }

            holder.updateSelected();

            stack.setTag(holder.serializeNBT());
            return ActionResultType.SUCCESS;
        }
        return ActionResultType.PASS;
    }
}
