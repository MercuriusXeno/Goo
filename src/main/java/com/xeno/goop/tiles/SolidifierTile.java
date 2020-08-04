package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.GoopMapping;
import com.xeno.goop.setup.Registry;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;

public class SolidifierTile extends TileEntity implements ITickableTileEntity {
    public Item target;
    private ItemStack targetStack;
    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
        target = Items.AIR;
        targetStack = ItemStack.EMPTY;
    }

    @Override
    public void tick() {

    }

    public Direction getHorizontalFacing()
    {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    public ItemStack getDisplayedItem()
    {
        return targetStack;
    }

    public void changeTargetItem(Item item) {
        if (!GoopMod.mappingHandler.has(item)) {
            return;
        }
        GoopMapping mapping = GoopMod.mappingHandler.get(item);
        if (mapping.isUnusable()) {
            return;
        }
        target = item;
        targetStack = item.getDefaultInstance();
    }

    public void clearTargetItem() {
        targetStack = ItemStack.EMPTY;
        target = Items.AIR;
    }
}
