package com.xeno.goo.items;

import com.xeno.goo.GooMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import net.minecraftforge.fml.RegistryObject;

import java.util.Objects;

public class DeferredSpawnEgg<T extends Entity> extends Item {

	private final int primaryColor;
	private final int secondaryColor;
	private final RegistryObject<EntityType<T>> type;

	public  DeferredSpawnEgg(RegistryObject<EntityType<T>> entity, int primaryColorIn, int secondaryColorIn) {
		super(new Properties()
				.maxStackSize(1)
				.group(GooMod.ITEM_GROUP));
		type = entity;
		primaryColor = primaryColorIn;
		secondaryColor = secondaryColorIn;
	}

	/**
	 * Called when this item is used when targetting a Block
	 */
	@Override
	public ActionResultType onItemUse(ItemUseContext context) {
		World world = context.getWorld();
		if (world.isRemote) {
			return ActionResultType.SUCCESS;
		} else {
			ItemStack itemstack = context.getItem();
			BlockPos blockpos = context.getPos();
			Direction direction = context.getFace();
			BlockState blockstate = world.getBlockState(blockpos);
			Block block = blockstate.getBlock();
			if (block == Blocks.SPAWNER) {
				TileEntity tileentity = world.getTileEntity(blockpos);
				if (tileentity instanceof MobSpawnerTileEntity) {
					AbstractSpawner abstractspawner = ((MobSpawnerTileEntity)tileentity).getSpawnerBaseLogic();
					abstractspawner.setEntityType(type.get());
					tileentity.markDirty();
					world.notifyBlockUpdate(blockpos, blockstate, blockstate, 3);
					itemstack.shrink(1);
					return ActionResultType.SUCCESS;
				}
			}

			BlockPos blockpos1;
			if (blockstate.getCollisionShape(world, blockpos).isEmpty()) {
				blockpos1 = blockpos;
			} else {
				blockpos1 = blockpos.offset(direction);
			}

			if (type.get().spawn((ServerWorld)world, itemstack, context.getPlayer(), blockpos1, SpawnReason.SPAWN_EGG, true, !Objects.equals(blockpos, blockpos1) && direction == Direction.UP) != null) {
				itemstack.shrink(1);
			}

			return ActionResultType.SUCCESS;
		}
	}

	public int getColor(int p_195983_1_) {
		return p_195983_1_ == 0 ? this.primaryColor : this.secondaryColor;
	}

}
