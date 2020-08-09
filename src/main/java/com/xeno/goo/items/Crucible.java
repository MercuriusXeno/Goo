package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

import javax.annotation.Nullable;

public class Crucible extends Item
{
    public Crucible()
    {
        super(new Item.Properties()
                .maxStackSize(1)
                .group(GooMod.ITEM_GROUP));
    }


    @Override
    public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(ItemStack stack, @Nullable net.minecraft.nbt.CompoundNBT nbt) {
        if (this.getClass() == Crucible.class)
            return new CrucibleFluidHandler(stack);
        else
            return super.initCapabilities(stack, nbt);
    }

    @Override
    public ActionResultType onItemUseFirst(ItemStack stack, ItemUseContext context)
    {
        BlockPos posHit = context.getPos();
        Vector3d hitVec = context.getHitVec();
        // context sensitive select goo from tank and pull it, if empty
        // or a Mobius (Mobius pulls anytime as long as it has space)

        TileEntity te = context.getWorld().getTileEntity(posHit);
        if (te instanceof GooBulbTile) {
            GooBulbTile bulb = (GooBulbTile)te;
            int split = (int)bulb.goo().stream().filter(g -> !g.isEmpty()).count();
            double dividend = split / 16d;

        }



    }
}
