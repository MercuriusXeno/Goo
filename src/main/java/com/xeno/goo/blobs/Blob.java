package com.xeno.goo.blobs;

import com.mojang.math.Vector3d;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.HashMap;

/***
 * Class representing a blob in space, capable of doing things.
 * Blobs are packets of thrown goo, containing one or more types.
 */
public abstract class Blob {
	/***
	 * Where is the blob physically in the world, this is a central point in space (the blob
	 * is around this position, it is not in the corner)
	 */
	private Vector3d position;

	/***
	 * How the blob is moving in space.
	 */
	private Vector3d motion;

	/***
	 * The shape of the blob as a cuboid in space.
	 */
	private VoxelShape shape;

	/***
	 * The level the blob is currently residing.
	 */
	private Level level;

	/***
	 * The player this blob belongs to, if this player is either in a player's inventory or
	 * being held or thrown by a player.
	 */
	private Player playerOwner;

	/***
	 * If this blob belongs to a tile entity that holds blobs, this is the reference to its owner.
	 */
	private final BlockEntity blockOwner;

	/***
	 * The blob stacks that are a member of this blob, keyed by id.
	 */
	private HashMap<ResourceLocation, BlobStack> stacks;

	public Blob (Level level, Player player, Vector3d position, Vector3d motion, BlobStack... stacks)
	{
		this.level = level;
		this.playerOwner = player;
		this.position = position;
		this.motion = motion;
		this.blockOwner = null;
		combineStacks(stacks);
	}

	private void combineStacks(BlobStack[] stacks) {

		for(var i = 0; i < stacks.length; i++) {
			combineStack(stacks[i].blobTypeName(), stacks[i]);
		}
	}

	private void combineStack(ResourceLocation id, BlobStack stack) {
		if (!this.stacks.get(id).addStack(stack)) {
			this.stacks.put(id, stack);
		}
	}

	protected abstract void tick();
}
