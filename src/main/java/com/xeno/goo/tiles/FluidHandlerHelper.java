package com.xeno.goo.tiles;

import com.xeno.goo.items.ItemsRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

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
        return i.getItem().equals(ItemsRegistry.Solidifier.get())
                || i.getItem().equals(ItemsRegistry.Gooifier.get())
                || i.getItem().equals(ItemsRegistry.GooBulb.get())
                || i.getItem().equals(ItemsRegistry.Crucible.get())
                || i.getItem().equals(ItemsRegistry.Mixer.get());

    }
}
