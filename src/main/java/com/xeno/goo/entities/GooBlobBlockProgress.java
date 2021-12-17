package com.xeno.goo.entities;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public class GooBlobBlockProgress {
	public final BlockPos pos;
	public final Direction face;
	public int ticks;
	public int amount;

	public GooBlobBlockProgress(BlockPos pos, Direction face) {
		this.pos = pos;
		this.face = face;
		ticks = 0;
		amount = 0;
	}
}
