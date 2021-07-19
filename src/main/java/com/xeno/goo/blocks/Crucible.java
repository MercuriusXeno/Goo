package com.xeno.goo.blocks;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.tiles.BulbTile;
import com.xeno.goo.tiles.CrucibleTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

public class Crucible extends Block {
	// cloned from cauldron, it's the same shape-ish
	// counter-intuitively, the inside of the crucible is a 1/16th of a block wider in every direction so it's easier
	// to dunk yourself into.
	private static final VoxelShape INSIDE = makeCuboidShape(1.0D, 4.0D, 1.0D, 15.0D, 16.0D, 15.0D);
	private static final VoxelShape INSIDE_VISUAL = makeCuboidShape(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D);
	protected static final VoxelShape SHAPE = VoxelShapes
			.combineAndSimplify(
					VoxelShapes.fullCube(),
					// feet exclusions (negative space) and inner space
					VoxelShapes.or(makeCuboidShape(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D),
							makeCuboidShape(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D),
							makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D),
							INSIDE),
					IBooleanFunction.ONLY_FIRST);
	protected static final VoxelShape SHAPE_VISUAL = VoxelShapes
			.combineAndSimplify(
					VoxelShapes.fullCube(),
					// feet exclusions (negative space) and inner space
					VoxelShapes.or(makeCuboidShape(0.0D, 0.0D, 4.0D, 16.0D, 3.0D, 12.0D),
							makeCuboidShape(4.0D, 0.0D, 0.0D, 12.0D, 3.0D, 16.0D),
							makeCuboidShape(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D),
							INSIDE_VISUAL),
					IBooleanFunction.ONLY_FIRST);


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
		if (isFluidItemHeld(context)) {
			return VoxelShapes.fullCube();
		}
		return SHAPE_VISUAL;
	}

	private boolean isFluidItemHeld(ISelectionContext context) {
		return context.hasItem(Items.BUCKET)
				|| context.hasItem(ItemsRegistry.VESSEL.get())
				|| context.hasItem(ItemsRegistry.GAUNTLET.get());
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRenderShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
		return SHAPE_VISUAL;
	}

	/**
	 * This method is used for visual raytraces, so we report what the outline shape is
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRayTraceShape(BlockState state, IBlockReader reader, BlockPos pos, ISelectionContext context) {
		return VoxelShapes.fullCube();
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

	@Override
	public void onEntityCollision(BlockState state, World worldIn, BlockPos pos, Entity entityIn) {
		super.onEntityCollision(state, worldIn, pos, entityIn);
		if (entityIn instanceof GooBlob) {
			((GooBlob) entityIn).tryFluidHandlerInteraction(pos, Direction.UP);
		}
	}

	@Override
	public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
		TileEntity t = worldIn.getTileEntity(pos);
		if (!(t instanceof CrucibleTile)) {
			return ActionResultType.FAIL;
		}
		ItemStack currentItem = ((CrucibleTile) t).currentItem();
		if (currentItem == null || currentItem.isEmpty()) {
			return ActionResultType.FAIL;
		}
		if (!worldIn.isRemote()) {
			((CrucibleTile) t).tryTakingItemFromCrucible(player);
		}
		return super.onBlockActivated(state, worldIn, pos, player, handIn, hit);
	}

	@Override
	public boolean hasComparatorInputOverride(BlockState state) {
		return true;
	}

	@Override
	public int getComparatorInputOverride(BlockState blockState, World worldIn, BlockPos pos) {
		TileEntity t = worldIn.getTileEntity(pos);
		if (t instanceof CrucibleTile) {
			return ((CrucibleTile)t).getComparatorFullness();
		}
		return 0;
	}
}
