package com.xeno.goo.blocks;

import com.xeno.goo.tiles.SolidifierTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static net.minecraft.util.Direction.*;
import static net.minecraft.util.Direction.UP;

public class Solidifier extends BlockWithConnections {
    public Solidifier() {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .setOpaque(((p_test_1_, p_test_2_, p_test_3_) -> false))
                .hardnessAndResistance(4.0f)
                .notSolid());
        setDefaultState(this.getDefaultState()
                .with(BlockStateProperties.POWERED, true)
                .with(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
        );
    }

    @Override
    public int getLightValue(BlockState state, IBlockReader world, BlockPos pos)
    {
        return !state.get(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    public void addInformation(ItemStack stack, IBlockReader worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn)
    {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        SolidifierTile.addInformation(stack, tooltip);
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new SolidifierTile();
    }

    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        if (!worldIn.isRemote) {
            boolean flag = state.get(BlockStateProperties.POWERED);
            if (flag != worldIn.isBlockPowered(pos)) {
                if (flag) {
                    worldIn.getPendingBlockTicks().scheduleTick(pos, this, 4);
                } else {
                    worldIn.setBlockState(pos, state.func_235896_a_(BlockStateProperties.POWERED), 2);
                }
            }
        }
    }


    public static final Map<Direction.Axis, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction.Axis a : Direction.Axis.values()) {
            switch (a) {
                case Y:
                    break;
                case X:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {NORTH, SOUTH, UP});
                    break;
                case Z:
                    RELEVANT_DIRECTIONS.put(a, new Direction[] {EAST, WEST, UP});
                    break;
            }
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(BlockStateProperties.HORIZONTAL_FACING).getAxis());
    }

    public void tick(BlockState state, ServerWorld worldIn, BlockPos pos, Random rand) {
        if (state.get(BlockStateProperties.POWERED) && !worldIn.isBlockPowered(pos)) {
            worldIn.setBlockState(pos, state.func_235896_a_(BlockStateProperties.POWERED), 2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(BlockStateProperties.POWERED, context.getWorld().isBlockPowered(context.getPos()))
                .with(BlockStateProperties.HORIZONTAL_FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.POWERED);
    }

    @Override
    public void onBlockHarvested(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof SolidifierTile) {
            SolidifierTile solidifier = (SolidifierTile)te;
            if (!world.isRemote) {
                ItemStack stack = solidifier.getSolidifierStack();
                ItemEntity itemEntity = new ItemEntity(world, pos.getX(), pos.getY(), pos.getZ(), stack);
                itemEntity.setDefaultPickupDelay();
                world.addEntity(itemEntity);
            }
        }

        super.onBlockHarvested(world, pos, state, player);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        boolean isClient = false;
        if (worldIn != null && worldIn.isRemote()) {
            isClient = true;
        }

        if (worldIn != null) {
            if (!isInItemFrameBounds(hit)) {
                return ActionResultType.SUCCESS;
            }
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof SolidifierTile)) {
                return ActionResultType.SUCCESS;
            }

            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
            if(!isClient) {
                ((SolidifierTile) tile).changeTargetItem(itemToSwap);
            }

            return ActionResultType.SUCCESS;
        }
        if (!player.isSneaking()) {
            return ActionResultType.PASS;
        }
        return ActionResultType.SUCCESS;
    }

    private static final float horizontalStart = 0.3125f;
    private static final float horizontalEnd = 0.6875f;
    private static final float verticalStart = 0f;
    private static final float verticalEnd = 0.3125f;
    private boolean isInItemFrameBounds(BlockRayTraceResult hit) {
        Direction side = hit.getFace();
        if (side == Direction.UP || side == Direction.DOWN) {
            return false;
        }
        // 'zero out' the hitvec so that we're comparing sort of raw unit values.
        Vector3d adjustedHitVec = hit.getHitVec().add(-hit.getPos().getX(), -hit.getPos().getY(),
                -hit.getPos().getZ());
        // the item frame bounds are between width (x or z) of 0.3125 to 0.6875
        // and height (y) of 0 to 0.3125
        Direction.Axis axis = side.getAxis();
        if (axis == Axis.Z) {
            return isHitInBounds(adjustedHitVec.x, adjustedHitVec.y);
        } else {
            return isHitInBounds(adjustedHitVec.z, adjustedHitVec.y);
        }
    }

    private boolean isHitInBounds(double z, double y) {
        return z >= horizontalStart && z <= horizontalEnd && y >= verticalStart && y <= verticalEnd;
    }
}
