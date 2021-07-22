package com.xeno.goo.blocks;

import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.tiles.LobberTile;
import com.xeno.goo.tiles.PadTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.Random;

public class Pad extends BlockWithConnections {
	VoxelShape padShape;
	VoxelShape shape;

	public Pad() {

		super(Properties.create(Material.IRON)
				.sound(SoundType.METAL)
				.hardnessAndResistance(1.0f)
				.notSolid()
		);
		setDefaultState(this.stateContainer.getBaseState()
				.with(BlockStateProperties.TRIGGERED, false)
		);
		padShape = makePad();
		shape = makeShapes();
	}

	protected static final double gasketThickness = 0.25d;
	protected static final double gasketWidth = 2d;
	protected static final double padHeight = 1d;
	protected static final double padWidth = 14d;
	protected static final double lowerGasketStartY = 0d;
	protected static final double lowerGasketEndY = lowerGasketStartY + gasketThickness;
	protected static final double upperGasketStartY = padHeight;
	protected static final double upperGasketEndY = upperGasketStartY + gasketThickness;
	protected static final double halfWidth = 8d;
	protected static final double gasketStartXOrZ = halfWidth - gasketWidth / 2d;
	protected static final double gasketEndXOrZ = halfWidth + gasketWidth / 2d;
	protected static final double padHorizontalStart = halfWidth - padWidth / 2d;
	protected static final double padHorizontalEnd = halfWidth + padWidth / 2d;

	public static final AxisAlignedBB PRESSURE_AABB = new AxisAlignedBB(padHorizontalStart / 16d, 0.0D, padHorizontalStart / 16d,
			padHorizontalEnd / 16d, 0.25D, padHorizontalEnd / 16d);
	private VoxelShape makePad() {
		Vector3d padStart = new Vector3d(padHorizontalStart, gasketThickness, padHorizontalStart);
		Vector3d padEnd = new Vector3d(padStart.x + padWidth, padStart.y + padHeight, padStart.z + padWidth);

		return VoxelHelper.cuboid(padStart, padEnd);
	}

	private VoxelShape makeShapes() {

		Vector3d lowerGasketStart = new Vector3d(gasketStartXOrZ, lowerGasketStartY, gasketStartXOrZ);
		Vector3d lowerGasketEnd = new Vector3d(gasketEndXOrZ, lowerGasketEndY, gasketEndXOrZ);
		Vector3d upperGasketStart = new Vector3d(gasketStartXOrZ, upperGasketStartY, gasketStartXOrZ);
		Vector3d upperGasketEnd = new Vector3d(gasketEndXOrZ, upperGasketEndY, gasketEndXOrZ);

		VoxelShape upper = VoxelHelper.cuboid(upperGasketStart, upperGasketEnd);
		VoxelShape lower = VoxelHelper.cuboid(lowerGasketStart, lowerGasketEnd);
		return VoxelHelper.mergeAll(padShape, upper, lower);
	}

	/**
	 * This method is used for the collision shape
	 * returning pad shape here so the player doesn't stand on quarter-pixel protrusions
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
		return this.canCollide ? padShape : VoxelShapes.empty();
	}

	/**
	 * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
	{
		return shape;
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
	 * We want small protrusions to act like they're *not* protrusions when you hit the thin edges, thus return just the pad shape here.
	 */
	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getRaytraceShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
		return padShape;
	}

	@Override
	public boolean hasTileEntity(BlockState state)
	{
		return true;
	}

	@Override
	public TileEntity createTileEntity(BlockState state, IBlockReader world)
	{
		return new PadTile();
	}

	@Override
	public BlockState getStateForPlacement(BlockItemUseContext context) {
		return getDefaultState()
				.with(BlockStateProperties.TRIGGERED, false);
	}

	@Override
	protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.TRIGGERED);
	}

	@Override
	protected Direction[] relevantConnectionDirections(BlockState state) {
		return new Direction[] { Direction.DOWN };
	}

	// stolen from pressure plate

	protected void playClickOnSound(IWorld worldIn, BlockPos pos) {
		if (this.material != Material.WOOD && this.material != Material.NETHER_WOOD) {
			worldIn.playSound((PlayerEntity)null, pos, SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCKS, 0.3F, 0.6F);
		} else {
			worldIn.playSound((PlayerEntity)null, pos, SoundEvents.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_ON, SoundCategory.BLOCKS, 0.3F, 0.8F);
		}

	}

	protected void playClickOffSound(IWorld worldIn, BlockPos pos) {
		if (this.material != Material.WOOD && this.material != Material.NETHER_WOOD) {
			worldIn.playSound((PlayerEntity)null, pos, SoundEvents.BLOCK_STONE_PRESSURE_PLATE_CLICK_OFF, SoundCategory.BLOCKS, 0.3F, 0.5F);
		} else {
			worldIn.playSound((PlayerEntity)null, pos, SoundEvents.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundCategory.BLOCKS, 0.3F, 0.7F);
		}

	}

	protected int getPoweredDuration() {
		return 20;
	}

	public boolean canSpawnInBlock() {
		return true;
	}

	public BlockState updatePostPlacement(BlockState stateIn, Direction facing, BlockState facingState, IWorld worldIn, BlockPos currentPos, BlockPos facingPos) {
		return facing == Direction.DOWN && !stateIn.isValidPosition(worldIn, currentPos) ? Blocks.AIR.getDefaultState() : super.updatePostPlacement(stateIn, facing, facingState, worldIn, currentPos, facingPos);
	}

	public boolean isValidPosition(BlockState state, IWorldReader worldIn, BlockPos pos) {
		BlockPos blockpos = pos.down();
		return hasSolidSideOnTop(worldIn, blockpos) || hasEnoughSolidSide(worldIn, blockpos, Direction.UP);
	}

	protected boolean detectPressure(World worldIn, BlockPos pos) {
		AxisAlignedBB axisalignedbb = PRESSURE_AABB.offset(pos);
		List<? extends Entity> list = worldIn.getEntitiesWithinAABBExcludingEntity(null, axisalignedbb);

		if (!list.isEmpty()) {
			for(Entity entity : list) {
				if (!entity.doesEntityNotTriggerPressurePlate()) {
					return true;
				}
			}
		}

		return false;
	}

	public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
		boolean isTriggered = state.get(BlockStateProperties.TRIGGERED);
		if (isTriggered) {
			this.updateState(worldIn, pos, state, true);
		}
	}

	public void onEntityCollision(BlockState state, World worldIn, BlockPos pos, Entity entityIn) {
		if (!worldIn.isRemote) {
			if (!state.get(BlockStateProperties.TRIGGERED)) {
				this.updateState(worldIn, pos, state, state.get(BlockStateProperties.TRIGGERED));
			}
		}
	}

	public BlockState setTriggeredState(BlockState originalState, boolean shouldBeTriggered) {
		return originalState.with(BlockStateProperties.TRIGGERED, shouldBeTriggered);
	}

	protected void updateState(World worldIn, BlockPos pos, BlockState state, boolean oldState) {
		boolean shouldBeTriggered = this.detectPressure(worldIn, pos);
		if (shouldBeTriggered != oldState) {
			BlockState newState = this.setTriggeredState(state, shouldBeTriggered);
			worldIn.setBlockState(pos, newState, 2);
			worldIn.markBlockRangeForRenderUpdate(pos, state, newState);
		}
		if (!shouldBeTriggered && oldState) {
			this.playClickOffSound(worldIn, pos);
		} else if (shouldBeTriggered && !oldState) {
			this.playClickOnSound(worldIn, pos);
		}

		if (shouldBeTriggered) {
			worldIn.getPendingBlockTicks().scheduleTick(new BlockPos(pos), this, this.getPoweredDuration());
		}
	}
}