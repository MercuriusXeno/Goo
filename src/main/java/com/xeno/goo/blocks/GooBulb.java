package com.xeno.goo.blocks;

import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.library.VoxelHelper;
import com.xeno.goo.tiles.GooBulbTile;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class GooBulb extends BlockWithConnections
{
    VoxelShape shape;

    public GooBulb()
    {
        super(Properties.create(Material.ROCK)
                .sound(SoundType.STONE)
                .hardnessAndResistance(1.0f)
                .notSolid()
        );
        shape = makeShape();
    }

    double gasketThickness = 0.25d;
    double borderLimit = 16f - gasketThickness;
    double gasketStart = 6d;
    double gasketEnd = 16d - gasketStart;

    private VoxelShape makeShape()
    {
        Vector3d cs = new Vector3d(gasketThickness, gasketThickness, gasketThickness);
        Vector3d ce = new Vector3d(borderLimit, borderLimit, borderLimit);
        Vector3d bs = new Vector3d (gasketStart, 0d, gasketStart);
        Vector3d be = new Vector3d (gasketEnd, gasketThickness, gasketEnd);
        Vector3d ts = new Vector3d (gasketStart, borderLimit, gasketStart);
        Vector3d te = new Vector3d (gasketEnd, 16d, gasketEnd);
        Vector3d es = new Vector3d(borderLimit, gasketStart, gasketStart);
        Vector3d ee = new Vector3d(16d, gasketEnd, gasketEnd);
        Vector3d ws = new Vector3d(0d, gasketStart, gasketStart);
        Vector3d we = new Vector3d(gasketThickness, gasketEnd, gasketEnd);
        Vector3d ss = new Vector3d(gasketStart, gasketStart, borderLimit);
        Vector3d se = new Vector3d(gasketEnd, gasketEnd, 16d);
        Vector3d ns = new Vector3d(gasketStart, gasketStart, 0d);
        Vector3d ne = new Vector3d(gasketEnd, gasketEnd, gasketThickness);

        VoxelShape central = VoxelHelper.cuboid(cs, ce);
        VoxelShape bottom = VoxelHelper.cuboid(bs, be);
        VoxelShape top = VoxelHelper.cuboid(ts, te);
        VoxelShape east = VoxelHelper.cuboid(es, ee);
        VoxelShape west = VoxelHelper.cuboid(ws, we);
        VoxelShape south = VoxelHelper.cuboid(ss, se);
        VoxelShape north = VoxelHelper.cuboid(ns, ne);

        return VoxelHelper.mergeAll(central, top, bottom, east, west, south, north);
    }

    /**
     * This method is used for the collision shape
     * returning a full cube here so the player doesn't stand on quarter-pixel protrusions
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return this.canCollide ? VoxelShapes.fullCube() : VoxelShapes.empty();
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
        return new GooBulbTile();
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        int containment = GooBulbItem.containment(stack);
        GooBulbTile tile = (GooBulbTile)worldIn.getTileEntity(pos);
        tile.setContainmentLevel(containment);
    }

    @Override
    public boolean hasTileEntity(BlockState state)
    {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onReplaced(BlockState state, World worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.matchesBlock(newState.getBlock())) {
            TileEntity tileentity = worldIn.getTileEntity(pos);
            if (tileentity instanceof GooBulbTile) {
                ((GooBulbTile) tileentity).spewItems();
                worldIn.updateComparatorOutputLevel(pos, this);
            }

            super.onReplaced(state, worldIn, pos, newState, isMoving);
        }
    }

    @Override
    protected Direction[] relevantConnectionDirections(BlockState state)
    {
        return Direction.values();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit)
    {
        TileEntity tile = worldIn.getTileEntity(pos);
        if (!(tile instanceof GooBulbTile)) {
            return ActionResultType.FAIL;
        }

        GooBulbTile bulb = ((GooBulbTile) tile);
        if (bulb.hasCrystal()) {
            if (!worldIn.isRemote) {
                bulb.spitOutCrystal(player, hit.getFace());
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote);
        } else {
            // bulb is empty so it can take a crystal if you're holding one.
            Item item = player.getHeldItem(handIn).getItem();
            if (!item.equals(Items.QUARTZ) && !(item instanceof CrystallizedGooAbstract)) {
                return ActionResultType.FAIL;
            }
            if (!worldIn.isRemote) {
                player.getHeldItem(handIn).shrink(1);
                ((GooBulbTile) tile).addCrystal(item);
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote);
        }
    }
}
