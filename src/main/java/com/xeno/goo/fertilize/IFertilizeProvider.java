package com.xeno.goo.fertilize;

import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.INBTSerializable;

public interface IFertilizeProvider extends INBTSerializable<CompoundNBT> {
	BlockPos prevBlockPos();
	void setPrevBlockPos(BlockPos pos);
	void sync(LivingEntity e);
}
