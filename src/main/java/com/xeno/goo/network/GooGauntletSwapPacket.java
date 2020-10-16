package com.xeno.goo.network;

import com.xeno.goo.blocks.GooBulbItem;
import com.xeno.goo.items.Gauntlet;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Hand;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class GooGauntletSwapPacket implements IGooModPacket
{
    private FluidStack target;
    public GooGauntletSwapPacket(FluidStack stack) {
        this.target = stack;
    }

    public GooGauntletSwapPacket(PacketBuffer buf) {
        read(buf);
    }

    @Override
    public void read(PacketBuffer buf)
    {
        this.target = buf.readFluidStack();
    }

    @Override
    public void toBytes(PacketBuffer buf)
    {
        buf.writeFluidStack(this.target);
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> supplier)
    {
        supplier.get().enqueueWork(() -> {
            if (supplier.get().getDirection().getReceptionSide() == LogicalSide.SERVER) {
                ServerPlayerEntity player = supplier.get().getSender();
                if (player == null) {
                    return;
                }
                if (player.getHeldItem(Hand.MAIN_HAND).getItem() instanceof Gauntlet) {
                    LazyOptional<IFluidHandlerItem> lazyCap = player.getHeldItem(Hand.MAIN_HAND).getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
                    lazyCap.ifPresent(c -> tryRaidingInventoryForGoo(player, c));
                }
            }
        });

        supplier.get().setPacketHandled(true);
    }


    private void tryRaidingInventoryForGoo(PlayerEntity player, IFluidHandlerItem cap)
    {
        if (this.target.isEmpty()) {
            tryEmptyingGauntletIntoInventoryContainer(player, cap);
        } else {
            tryRaidingInventoryForGooForRealThough(player, cap);
        }
    }

    private void tryRaidingInventoryForGooForRealThough(PlayerEntity player, IFluidHandlerItem cap) {
        // first empty the gauntlet. this is easier than trying to swap fluids in place.
        tryEmptyingGauntletIntoInventoryContainer(player, cap);
        if (!cap.getFluidInTank(0).isEmpty()) {
            return;
        }
        for (int index = 0; index < player.inventory.getSizeInventory(); index++) {
            FluidStack heldGoo = cap.getFluidInTank(0);
            if (heldGoo.getAmount() >= cap.getTankCapacity(0)) {
                return;
            }
            ItemStack i = player.inventory.getStackInSlot(index);
            if (!(i.getItem() instanceof GooBulbItem)) {
                continue;
            }

            CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(i, Objects.requireNonNull(Registry.GOO_BULB_TILE.get().getRegistryName()).toString());
            int amountToDrain = cap.getTankCapacity(0) - heldGoo.getAmount();
            CompoundNBT tag = bulbTag.getCompound("goo");
            List<FluidStack> heldStacks = GooBulbTile.deserializeGooForDisplay(tag);
            for(FluidStack stack : heldStacks) {
                if (!stack.getFluid().equals(target.getFluid())) {
                    continue;
                }

                int availableToDrain = Math.min(amountToDrain, stack.getAmount());
                stack.setAmount(stack.getAmount() - availableToDrain);
                FluidStack fillStack = target.copy();
                fillStack.setAmount(availableToDrain);
                cap.fill(fillStack, IFluidHandler.FluidAction.EXECUTE);
            }
            heldStacks.removeIf(FluidStack::isEmpty);

            // rewrite the goo tag.
            tag = new CompoundNBT();
            for(int g = 0; g < heldStacks.size(); g++) {
                FluidStack stack = heldStacks.get(g);
                tag.put("goo" + g, stack.writeToNBT(new CompoundNBT()));
            }
            tag.putInt("count", heldStacks.size());
            bulbTag.put("goo", tag);
            CompoundNBT itemTag = i.getTag();
            itemTag.put("BlockEntityTag", bulbTag);
            i.setTag(itemTag);
            player.inventory.setInventorySlotContents(index, i);
            player.inventory.markDirty();
        }
    }

    private void tryEmptyingGauntletIntoInventoryContainer(PlayerEntity player, IFluidHandlerItem cap) {
        for (int index = 0; index < player.inventory.getSizeInventory(); index++) {
            FluidStack heldGoo = cap.getFluidInTank(0);
            if (heldGoo.isEmpty()) {
                return;
            }
            ItemStack i = player.inventory.getStackInSlot(index);
            if (!(i.getItem() instanceof GooBulbItem)) {
                continue;
            }

            CompoundNBT bulbTag = FluidHandlerHelper.getOrCreateTileTag(i, Objects.requireNonNull(Registry.GOO_BULB_TILE.get().getRegistryName()).toString());
            CompoundNBT tag = bulbTag.getCompound("goo");
            int size = tag.getInt("count");
            int containment = EnchantmentHelper.getEnchantmentLevel(Registry.CONTAINMENT.get(), i);
            int storageMax = GooBulbTile.storageForDisplay(containment);
            int stored = 0;
            int foundHeldGoo = -1;
            for(int g = 0; g < size; g++) {
                CompoundNBT gooTag = tag.getCompound("goo" + g);
                FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
                stored += stack.getAmount();
                if (stack.getFluid().equals(heldGoo.getFluid())) {
                    foundHeldGoo = g;
                }
            }
            int maxCanStore = storageMax - stored;
            if (maxCanStore <= 0) {
                continue;
            }

            // figure out if we're holding more than this bulb can hold, and reduce the stack
            FluidStack stackToDrain = heldGoo.copy();
            if (stackToDrain.getAmount() > maxCanStore) {
                stackToDrain.setAmount(maxCanStore);
            }

            // while we were iterating over the goo to count it, we never found our goo type
            // add a stack to the nbt tag and we'll write over it at the end.
            if (foundHeldGoo == -1) {
                cap.drain(stackToDrain, IFluidHandler.FluidAction.EXECUTE);
                tag.putInt("count", size + 1);
                CompoundNBT gooTag = new CompoundNBT();
                stackToDrain.writeToNBT(gooTag);
                tag.put("goo" + size, gooTag);
            } else {
                cap.drain(stackToDrain, IFluidHandler.FluidAction.EXECUTE);
                CompoundNBT gooTag = tag.getCompound("goo" + foundHeldGoo);
                FluidStack stack = FluidStack.loadFluidStackFromNBT(gooTag);
                stack.setAmount(stack.getAmount() + stackToDrain.getAmount());
                stack.writeToNBT(gooTag);
                tag.put("goo" + foundHeldGoo, gooTag);
            }

            bulbTag.put("goo", tag);
            CompoundNBT itemTag = i.getTag();
            itemTag.put("BlockEntityTag", bulbTag);
            i.setTag(itemTag);
            player.inventory.setInventorySlotContents(index, i);
            player.inventory.markDirty();
        }
    }
}
