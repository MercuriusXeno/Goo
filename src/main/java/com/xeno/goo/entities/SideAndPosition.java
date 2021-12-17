package com.xeno.goo.entities;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;

public class SideAndPosition {
	public BlockPos pos;
	public Direction side;

	public Direction side() {
		return side;
	}

	public BlockPos pos() {
		return pos;
	}

	public SideAndPosition(Direction d, BlockPos pos) {
		this.pos = pos;
		this.side = d;
	}
}
