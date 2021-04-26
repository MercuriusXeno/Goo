package com.xeno.goo.util;

import net.minecraft.dispenser.IPosition;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3i;

import java.util.EnumSet;
import java.util.Set;

public class DirectionalPos extends BlockPos {

	private EnumSet<Direction> directions = EnumSet.noneOf(Direction.class);

	public DirectionalPos(int x, int y, int z) {

		super(x, y, z);
	}

	public DirectionalPos(double x, double y, double z) {

		super(x, y, z);
	}

	public DirectionalPos(Vector3d vec) {

		super(vec);
	}

	public DirectionalPos(IPosition position) {

		super(position);
	}

	public DirectionalPos(Vector3i source) {

		super(source);
	}

	/**
	 * Offset this BlockPos 1 block in the given direction
	 */
	public DirectionalPos offset(Direction facing) {

		DirectionalPos next = new DirectionalPos(this.getX() + facing.getXOffset(), this.getY() + facing.getYOffset(), this.getZ() + facing.getZOffset());
		next.directions.add(facing);
		return next;
	}

	/**
	 * Offsets this BlockPos n blocks in the given direction
	 */
	public DirectionalPos offset(Direction facing, int n) {

		if (n == 0)
			return this;
		DirectionalPos next = new DirectionalPos(this.getX() + facing.getXOffset() * n, this.getY() + facing.getYOffset() * n, this.getZ() + facing.getZOffset() * n);
		next.directions.add(facing);
		return next;
	}

	public boolean isFrom(Direction d) {

		return directions.contains(d);
	}

	public Set<Direction> unusedDirections() {

		return EnumSet.complementOf(directions);
	}

}
