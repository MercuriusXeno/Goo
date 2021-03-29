package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUseContext;
import net.minecraft.tileentity.MobSpawnerTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.AbstractSpawner;

import java.util.Objects;

public class GooSnailCaptured extends Item {
    public GooSnailCaptured() {
        super(
                new Item.Properties()
                        .maxStackSize(1)
                        .group(GooMod.ITEM_GROUP)
        );
    }

    @Override
    public ActionResultType onItemUse(ItemUseContext context) {
        World world = context.getWorld();
        if (!world.isRemote) {
            ItemStack itemstack = context.getItem();
            BlockPos blockpos = context.getPos();
            Direction direction = context.getFace();
            BlockState blockstate = world.getBlockState(blockpos);
            BlockPos offsetPos;
            if (blockstate.getCollisionShape(world, blockpos).isEmpty()) {
                offsetPos = blockpos;
            } else {
                offsetPos = blockpos.offset(direction);
            }

            if (Registry.GOO_SNAIL.get().spawn((ServerWorld) world, itemstack, context.getPlayer(), offsetPos, SpawnReason.EVENT, true, !Objects.equals(blockpos, offsetPos) && direction == Direction.UP) != null) {
                itemstack.shrink(1);
            }

        }
        return ActionResultType.SUCCESS;
    }
}
