package com.xeno.goo.entities;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GooBlobShape {
	public BlobState blobState;
	public Vector3d pos;
	public Vector3d shape;
	public Vector3d motion;
	public AxisAlignedBB box;
	public int estAmount;
	public int ticksAlive;
	public int ticksOnGround;
	public int ticksInAir;
	public UUID uuid;
	public final List<UUID> parents = new ArrayList<>();
	public final List<UUID> children = new ArrayList<>();
	public final List<GooBlobBlockProgress> progress = new ArrayList<>();

	public GooBlobShape(BlobState state, double x, double y, double z, double dx, double dy, double dz,
			double mx, double my, double mz, int approximateAmount) {
		this.blobState = state;
		this.pos = new Vector3d(x, y, z);
		this.shape = new Vector3d(dx, dy, dz);
		this.motion = new Vector3d(mx, my, mz);
		this.box = new AxisAlignedBB(pos, shape);
		this.estAmount = approximateAmount;
		this.uuid = UUID.randomUUID();

	}
}
