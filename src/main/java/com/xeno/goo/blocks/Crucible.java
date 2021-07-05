package com.xeno.goo.blocks;

import com.xeno.goo.tiles.BulbTile;
import com.xeno.goo.tiles.CrucibleTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;

public class Crucible extends Block {
	// cloned from cauldron, it's the same shape
	private static final VoxelShape INSIDE = makeCuboidShape(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D);
	protected static final VoxelShape SHAPE = VoxelShapes
			.combineAndSimplify(VoxelShapes.fullCube(), VoxelShapes.or(makeCuboidShape(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D), makeCuboidShape(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D), makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D), INSIDE), IBooleanFunction.ONLY_FIRST);


	public Crucible() {
		super(
				Properties.create(Material.IRON)
						.hardnessAndResistance(1.0f)
						.sound(SoundType.METAL)
						.notSolid()
		);
	}

	/**
	 * This method is used for the collision shape
	 * returning a full cube here so the player doesn't stand on quarter-pixel protrusions
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
		return this.canCollide ? SHAPE : VoxelShapes.empty();
	}

	/**
	 * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
	{
		return SHAPE;
	}

	/**
	 * This method is used for visual raytraces, so we report what the outline shape is
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRayTraceShape(BlockState state, IBlockReader reader, BlockPos pos, ISelectionContext context) {
		return getShape(state, reader, pos, context);
	}

	/**
	 * This is the override shape used by the raytracer in *all* modes, it changes what face the raytracer reports was hit.
	 * We want small protrusions to act like they're *not* protrusions when you hit the thin edges, thus return a larger shape here.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRaytraceShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
		return VoxelShapes.fullCube();
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world)
	{
		return new CrucibleTile();
	}

	@Override
	public boolean hasTileEntity(BlockState state)
	{
		return true;
	}
}
