package com.xeno.goo.items;

import com.xeno.goo.entities.GooEntity;
import com.xeno.goo.fluids.GooBase;
import com.xeno.goo.library.Compare;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.BulbFluidHandler;
import com.xeno.goo.tiles.GooBulbTile;
import javafx.collections.FXCollections;
import javafx.collections.transformation.SortedList;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.BlockState;
import net.minecraft.block.IBucketPickupHandler;
import net.minecraft.client.renderer.FluidBlockRenderer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
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

    public int baseCapacity(ItemStack stack) {
        return ((GooHolder)stack.getItem()).capacity();
    }

    public int holdingMultiplier(ItemStack stack) {
        return (int)Math.ceil(Math.pow(((GooHolder)stack.getItem()).holdingMultiplier(), holding(stack)));
    }

    private float baseThrownSpeed(ItemStack stack) {
        return ((GooHolder)stack.getItem()).thrownSpeed();
    }

    private float  armstrongMultiplier(ItemStack stack)
    {
        return (float)Math.pow(((GooHolder)stack.getItem()).armstrongMultiplier(), armstrong(stack));
    }

    public int holding(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Registry.HOLDING_ENCHANTMENT.get(), stack);
    }

    public int armstrong(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Registry.ARMSTRONG_ENCHANTMENT.get(), stack);
    }

    public float thrownSpeed(ItemStack stack)
    {
        return armstrongMultiplier(stack) * baseThrownSpeed(stack);
    }

    protected static BlockRayTraceResult rayTrace(World worldIn, PlayerEntity player, RayTraceContext.FluidMode fluidMode) {
        float f = player.rotationPitch;
        float f1 = player.rotationYaw;
        Vector3d vector3d = player.getEyePosition(1.0F);
        float f2 = MathHelper.cos(-f1 * ((float)Math.PI / 180F) - (float)Math.PI);
        float f3 = MathHelper.sin(-f1 * ((float)Math.PI / 180F) - (float)Math.PI);
        float f4 = -MathHelper.cos(-f * ((float)Math.PI / 180F));
        float f5 = MathHelper.sin(-f * ((float)Math.PI / 180F));
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        double d0 = player.getAttribute(net.minecraftforge.common.ForgeMod.REACH_DISTANCE.get()).getValue();;
        Vector3d vector3d1 = vector3d.add((double)f6 * d0, (double)f5 * d0, (double)f7 * d0);
        return worldIn.rayTraceBlocks(new RayTraceContext(vector3d, vector3d1, RayTraceContext.BlockMode.OUTLINE, fluidMode, player));
    }

    public ActionResult<ItemStack> tryBucketishDrain(World worldIn, PlayerEntity playerIn, Hand handIn) {
        ItemStack itemstack = playerIn.getHeldItem(handIn);
        RayTraceResult raytraceresult = rayTrace(worldIn, playerIn, this.heldGoo.getFluid() == Fluids.EMPTY ? RayTraceContext.FluidMode.SOURCE_ONLY : RayTraceContext.FluidMode.NONE);
        ActionResult<ItemStack> ret = net.minecraftforge.event.ForgeEventFactory.onBucketUse(playerIn, worldIn, itemstack, raytraceresult);
        if (ret != null) return ret;
        if (raytraceresult.getType() == RayTraceResult.Type.MISS) {
            return ActionResult.resultPass(itemstack);
        } else if (raytraceresult.getType() != RayTraceResult.Type.BLOCK) {
            return ActionResult.resultPass(itemstack);
        } else {
            BlockRayTraceResult blockraytraceresult = (BlockRayTraceResult)raytraceresult;
            BlockPos blockpos = blockraytraceresult.getPos();
            Direction direction = blockraytraceresult.getFace();
            BlockPos blockpos1 = blockpos.offset(direction);
            if (worldIn.isBlockModifiable(playerIn, blockpos) && playerIn.canPlayerEdit(blockpos1, direction, itemstack)) {
                if (this.heldGoo.getFluid() == Fluids.EMPTY && !playerIn.isHandActive()) {
                    BlockState blockstate1 = worldIn.getBlockState(blockpos);
                    if (blockstate1.getBlock() instanceof IBucketPickupHandler) {
                        Fluid fluid = ((IBucketPickupHandler)blockstate1.getBlock()).pickupFluid(worldIn, blockpos, blockstate1);
                        if (fluid != Fluids.EMPTY) {
                            playerIn.addStat(Stats.ITEM_USED.get(this));

                            SoundEvent soundevent = this.heldGoo.getFluid().getAttributes().getEmptySound();
                            if (soundevent == null) soundevent = fluid.isIn(FluidTags.LAVA) ? SoundEvents.ITEM_BUCKET_FILL_LAVA : SoundEvents.ITEM_BUCKET_FILL;
                            playerIn.playSound(soundevent, 1.0F, 1.0F);
                            ItemStack itemstack1 = DrinkHelper.func_241445_a_(itemstack, playerIn, new ItemStack(fluid.getFilledBucket()));
                            if (!worldIn.isRemote) {
                                CriteriaTriggers.FILLED_BUCKET.trigger((ServerPlayerEntity)playerIn, new ItemStack(fluid.getFilledBucket()));
                            }

                            return ActionResult.func_233538_a_(itemstack1, worldIn.isRemote());
                        }
                    }

                    return ActionResult.resultFail(itemstack);
                }
            } else {
                return ActionResult.resultFail(itemstack);
            }
        }
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
            IFluidHandler bulbCap = BulbFluidHandler.bulbCapability(bulb, Direction.DOWN);

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

    public GooEntity trySpawningGoo(World worldIn, LivingEntity livingEntityIn, Hand handIn)
    {
        if (worldIn.isRemote()) {
            return null;
        }

        if (heldGoo.isEmpty()) {
            return null;
        }

        GooBase whichGoo = (GooBase)heldGoo.getFluid();
        GooEntity e = whichGoo.createEntity(worldIn, livingEntityIn, this.heldGoo, handIn);
        ((GooHolder)livingEntityIn.getHeldItem(handIn).getItem())
                .data(livingEntityIn.getHeldItem(handIn))
                .drain(livingEntityIn.getHeldItem(handIn), heldGoo.getAmount(), IFluidHandler.FluidAction.EXECUTE);
        return e;
    }

    public FluidStack heldGoo()
    {
        return heldGoo;
    }
}
