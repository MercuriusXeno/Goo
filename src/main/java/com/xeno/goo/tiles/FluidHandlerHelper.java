package com.xeno.goo.tiles;

import com.xeno.goo.items.BasinAbstractionCapability;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;

public abstract class FluidHandlerHelper
{
    public static LazyOptional<IFluidHandler> capabilityOfSelf(TileEntity t, Direction dir)
    {
        return t.getCapability(FLUID_HANDLER_CAPABILITY, dir);
    }

    public static LazyOptional<IFluidHandler> capabilityOfNeighbor(TileEntity t, Direction dir)
    {
        // check the tile in this direction, if it's not another tile, pass;
        TileEntity tile = FluidHandlerHelper.tileAtDirection(t, dir);
        if (tile == null) {
            return LazyOptional.empty();
        }
        // whatever the direction we fetched the neighbor in, the capability we're asking for is the inverse.
        return tile.getCapability(FLUID_HANDLER_CAPABILITY, dir.getOpposite());
    }

    public static LazyOptional<IFluidHandler> capability(World world, BlockPos blockPos, Direction d)
    {
        // check the tile in this direction, if it's not another tile, pass;
        TileEntity tile = FluidHandlerHelper.tileAt(world, blockPos);
        if (tile == null) {
            return LazyOptional.empty();
        }
        return tile.getCapability(FLUID_HANDLER_CAPABILITY, d);
    }

    public static IFluidHandlerItem capability(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        // before caps load, this is null :|
        if (CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY == null) {
            return null;
        }
        LazyOptional<IFluidHandlerItem> lazyCap = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY);
        if (lazyCap.isPresent()) {
            // shut up linter, it's present.
            return lazyCap.orElse(null);
        }

        return null;
    }

    public static TileEntity tileAtDirection(TileEntity e, Direction d)
    {
        return tileAtDirection(e.getWorld(), e.getPos(), d);
    }

    private static TileEntity tileAtDirection(World world, BlockPos pos, Direction d)
    {
        if (world == null) {
            return null;
        }
        TileEntity t = world.getTileEntity(pos.offset(d));
        return t;
    }

    private static TileEntity tileAt(World world, BlockPos pos)
    {
        if (world == null) {
            return null;
        }
        TileEntity t = world.getTileEntity(pos);
        return t;
    }

    public static CompoundNBT getOrCreateTileTag(ItemStack i, String tileEntityId) {
        // many things don't need tile tags. Abort if we don't.
        if (!isTileTagWorthy(i)) {
            return i.getTag();
        }

        CompoundNBT itemTag = new CompoundNBT();
        if (!i.hasTag() || i.getTag() == null) {
            i.setTag(itemTag);
        } else {
            itemTag = i.getTag();
        }

        if (!itemTag.contains("BlockEntityTag")) {
            itemTag.put("BlockEntityTag", new CompoundNBT());
        }

        CompoundNBT bulbTag = itemTag.getCompound("BlockEntityTag");
        bulbTag.putString("id", tileEntityId);
        if (!bulbTag.contains("goo")) {
            CompoundNBT gooTag = new CompoundNBT();
            gooTag.putInt("count", 0);
            bulbTag.put("goo", gooTag);
        }
        itemTag.put("BlockEntityTag", bulbTag);
        i.setTag(itemTag);

        return bulbTag;
    }

    private static boolean isTileTagWorthy(ItemStack i) {
        return i.getItem().equals(ItemsRegistry.SOLIDIFIER.get())
                || i.getItem().equals(ItemsRegistry.GOOIFIER.get())
                || i.getItem().equals(ItemsRegistry.GOO_BULB.get())
                || i.getItem().equals(ItemsRegistry.CRUCIBLE.get())
                || i.getItem().equals(ItemsRegistry.MIXER.get())
                || i.getItem().equals(ItemsRegistry.TROUGH.get());

    }

    public static List<FluidStack> contentsOfTileStack(ItemStack currentStack) {
        String id = "";
        Item item = currentStack.getItem();
        if (item.equals(ItemsRegistry.MIXER.get())) {
            id = Objects.requireNonNull(Registry.MIXER_TILE.get().getRegistryName()).toString();
        } else if (item.equals(ItemsRegistry.CRUCIBLE.get())) {
            id = Objects.requireNonNull(Registry.CRUCIBLE_TILE.get().getRegistryName()).toString();
        } else if (item.equals(ItemsRegistry.GOO_BULB.get())) {
            id = Objects.requireNonNull(Registry.GOO_BULB_TILE.get().getRegistryName()).toString();
        } else if (item.equals(ItemsRegistry.TROUGH.get())) {
            id = Objects.requireNonNull(Registry.TROUGH_TILE.get().getRegistryName()).toString();
        }
        if (id.equals("")) {
            return new ArrayList<>();
        }
        CompoundNBT containerTag = FluidHandlerHelper.getOrCreateTileTag(currentStack, id);
        if (containerTag == null) {
            return new ArrayList<>();
        }
        CompoundNBT gooTag = containerTag.getCompound("goo");
        List<FluidStack> containerValues = GooContainerAbstraction.deserializeGooForDisplay(gooTag);
        containerValues.sort((v, v2) -> v2.getAmount() - v.getAmount());
        return containerValues;
    }

    public static List<FluidStack> contentsOfItemStack(ItemStack currentStack) {
        List<FluidStack> values = new ArrayList<>();

        IFluidHandlerItem cap = FluidHandlerHelper.capability(currentStack);
        if (cap == null) {
            return values;
        }

        // basins have a more elaborate contents list than gauntlets
        if (cap instanceof BasinAbstractionCapability) {
            List<FluidStack> fluids = new ArrayList<>(((BasinAbstractionCapability) cap).getFluids());
            fluids.removeIf(FluidStack::isEmpty);
            if (fluids.size() == 0) {
                return values;
            }
            values.addAll(fluids);
        } else {
            if (cap.getFluidInTank(0).isEmpty()) {
                return values;
            }

            values.add(cap.getFluidInTank(0));
        }
        return values;
    }
}
