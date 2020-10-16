package com.xeno.goo.blocks;

import com.xeno.goo.client.render.PumpRenderMode;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.tiles.GooPumpTile;
import com.xeno.goo.tiles.GooifierTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
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
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.state.properties.BlockStateProperties.FACING;
import static net.minecraft.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.util.Direction.*;
import static net.minecraft.util.Direction.DOWN;
import static net.minecraft.util.Direction.UP;

public class GooPump extends BlockWithConnections
{
    VoxelShape[] shapes;
    public static final EnumProperty<PumpRenderMode> RENDER = EnumProperty.create("render", PumpRenderMode.class);

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return getCollisionShape(state, worldIn, pos);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, IBlockReader reader, BlockPos pos)
    {
        int index = getIndexFromState(state.get(FACING));
        return  shapes[index];
    }

    private int getIndexFromState(Direction direction)
    {
        switch(direction) {
            case UP:
                return 0;
            case DOWN:
                return 1;
            case EAST:
                return 2;
            case WEST:
                return 3;
            case SOUTH:
                return 4;
            case NORTH:
                return 5;
        }
        return 0;
    }

    private VoxelShape[] makeShapes()
    {
        // up
        Vector3d ubs = new Vector3d(0d, 0d, 0d);
        Vector3d ube = new Vector3d(16d, 8d, 16d);
        Vector3d ups = new Vector3d(5d, 8d, 5d);
        Vector3d upe = new Vector3d(11d, 16d, 11d);
        VoxelShape[] upShapes = combo(ubs, ube, ups, upe);
        // down
        Vector3d dbs = new Vector3d(0d, 8d, 0d);
        Vector3d dbe = new Vector3d(16d, 16d, 16d);
        Vector3d dps = new Vector3d(5d, 0d, 5d);
        Vector3d dpe = new Vector3d(11d, 8d, 11d);
        VoxelShape[] downShapes = combo(dbs, dbe, dps, dpe);
        // west
        Vector3d wbs = new Vector3d(0d, 0d, 0d);
        Vector3d wbe = new Vector3d(8d, 16d, 16d);
        Vector3d wps = new Vector3d(8d, 5d, 5d);
        Vector3d wpe = new Vector3d(16d, 11d, 11d);
        VoxelShape[] westShapes = combo(wbs, wbe, wps, wpe);
        // east
        Vector3d ebs = new Vector3d(8d, 0d, 0d);
        Vector3d ebe = new Vector3d(16d, 16d, 16d);
        Vector3d eps = new Vector3d(0d, 5d, 5d);
        Vector3d epe = new Vector3d(8d, 11d, 11d);
        VoxelShape[] eastShapes = combo(ebs, ebe, eps, epe);
        // south
        Vector3d sbs = new Vector3d(0d, 0d, 0d);
        Vector3d sbe = new Vector3d(16d, 16d, 8d);
        Vector3d sps = new Vector3d(5d, 5d, 8d);
        Vector3d spe = new Vector3d(11d, 11d, 16d);
        VoxelShape[] southShapes = combo(sbs, sbe, sps, spe);
        // north
        Vector3d nbs = new Vector3d(0d, 0d, 8d);
        Vector3d nbe = new Vector3d(16d, 16d, 16d);
        Vector3d nps = new Vector3d(5d, 5d, 0d);
        Vector3d npe = new Vector3d(11d, 11d, 8d);
        VoxelShape[] northShapes = combo(nbs, nbe, nps, npe);
        return new VoxelShape[] {
                fabricateAlignedShape(upShapes),
                fabricateAlignedShape(downShapes),
                fabricateAlignedShape(westShapes),
                fabricateAlignedShape(eastShapes),
                fabricateAlignedShape(southShapes),
                fabricateAlignedShape(northShapes)
        };
    }

    private VoxelShape[] combo(Vector3d bs, Vector3d be, Vector3d ps, Vector3d pe)
    {
        VoxelShape vs = Block.makeCuboidShape(bs.x, bs.y, bs.z, be.x, be.y, be.z);
        VoxelShape vs1 = Block.makeCuboidShape(ps.x, ps.y, ps.z, pe.x, pe.y, pe.z);
        return new VoxelShape[] {vs, vs1};
    }

    private VoxelShape fabricateAlignedShape(VoxelShape[] shapes)
    {
        VoxelShape combo = VoxelShapes.empty();
        for(int i = 0; i < shapes.length; i++) {
            combo = VoxelShapes.combine(combo, shapes[i], IBooleanFunction.OR);
        }
        return combo;
    }

    public GooPump()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        shapes = makeShapes();
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world)
    {
        return new GooPumpTile();
    }

    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return getDefaultState()
                .with(FACING, context.getFace().getOpposite())
                .with(RENDER, PumpRenderMode.STATIC);
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, RENDER);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        if (state.get(FACING).getAxis() == hit.getFace().getAxis()) {
            return ActionResultType.PASS;
        }
        boolean isClient = false;
        if (worldIn != null && worldIn.isRemote()) {
            isClient = true;
        }

        if (worldIn != null) {
            TileEntity tile = worldIn.getTileEntity(pos);
            if (!(tile instanceof GooPumpTile)) {
                return ActionResultType.SUCCESS;
            }

            Item itemToSwap = player.getHeldItem(handIn).isEmpty() || player.isSneaking() ? Items.AIR : player.getHeldItem(handIn).getItem();
            if(!isClient) {
                ((GooPumpTile) tile).changeTargetItem(itemToSwap);
            }

            return ActionResultType.SUCCESS;
        }
        if (!player.isSneaking()) {
            return ActionResultType.PASS;
        }
        return ActionResultType.SUCCESS;
    }

    public static final Map<Direction, Direction[]> RELEVANT_DIRECTIONS = new HashMap<>();
    static {
        for(Direction d : Direction.values()) {
            RELEVANT_DIRECTIONS.put(d, new Direction[] { d, d.getOpposite()});
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return RELEVANT_DIRECTIONS.get(state.get(FACING));
    }
}
