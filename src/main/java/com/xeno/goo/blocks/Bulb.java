package com.xeno.goo.blocks;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.items.CrystallizedGooAbstract;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.overlay.RayTraceTargetSource;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.BulbTile;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class Bulb extends BlockWithConnections
{

    private static final int BUCKET_AMOUNT = 1000;

    public Bulb()
    {
        super(Properties.create(Material.GLASS)
                .sound(SoundType.GLASS)
                .hardnessAndResistance(1.0f)
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
        return this.canCollide ? VoxelShapes.fullCube() : VoxelShapes.empty();
    }

    /**
     * This method is used for outline raytraces, highlighter edges will be drawn on this shape's borders
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context)
    {
        return VoxelShapes.fullCube();
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
        return new BulbTile();
    }

    @Override
    public void onBlockPlacedBy(World worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.onBlockPlacedBy(worldIn, pos, state, placer, stack);
        int containment = BulbItem.containment(stack);
        BulbTile tile = (BulbTile)worldIn.getTileEntity(pos);
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
            if (tileentity instanceof BulbTile) {
                ((BulbTile) tileentity).spewItems();
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
        if (!(tile instanceof BulbTile)) {
            return ActionResultType.FAIL;
        }

        BulbTile bulb = ((BulbTile) tile);
        ActionResultType bucketInteracted = tryBucketInteraction(bulb, state, worldIn, pos, player, handIn, hit);
        if (bucketInteracted != ActionResultType.FAIL) {
            return bucketInteracted;
        }
        return tryBidirectionalCrystalInteraction(worldIn, player, handIn, hit, (BulbTile) tile, bulb);
    }

    private ActionResultType tryBucketInteraction(BulbTile bulb, BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn,
            BlockRayTraceResult hit) {

        ActionResultType emptyBucketFill = tryFillingEmptyBucket(bulb, player, handIn, hit);
        if (emptyBucketFill != ActionResultType.FAIL) {
            return emptyBucketFill;
        }
        return tryEmptyingFilledBucket(bulb, player, handIn, hit);


    }

    private ActionResultType tryEmptyingFilledBucket(BulbTile bulb, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {

        if (!(player.getHeldItem(handIn).getItem() instanceof BucketItem)) {
            return ActionResultType.FAIL;
        }

        BucketItem bucket = (BucketItem)player.getHeldItem(handIn).getItem();
        if (!(bucket.getFluid() instanceof GooFluid)) {
            return ActionResultType.FAIL;
        }

        IFluidHandler cap = bulb.getCapabilityFromRayTraceResult(hit.getHitVec(), hit.getFace(), RayTraceTargetSource.BUCKET);
        if (cap == null) {
            return ActionResultType.FAIL;
        }

        FluidStack bucketStack = new FluidStack(bucket.getFluid(), BUCKET_AMOUNT);
        int amountTransferred = cap.fill(bucketStack, FluidAction.SIMULATE);
        if (amountTransferred < BUCKET_AMOUNT) {
            return ActionResultType.FAIL;
        }

        if (!player.world.isRemote) {
            cap.fill(bucketStack, FluidAction.EXECUTE);
            player.setHeldItem(handIn, new ItemStack(Items.BUCKET));
        }
        AudioHelper.playerAudioEvent(player, Registry.GOO_DEPOSIT_SOUND.get(), PitchFormulas.HalfToOne.get());
        return ActionResultType.func_233537_a_(player.world.isRemote);
    }

    @NotNull
    private ActionResultType tryFillingEmptyBucket(BulbTile bulb, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {

        if (player.getHeldItem(handIn).getItem() != Items.BUCKET) {
            return ActionResultType.FAIL;
        }
        IFluidHandler cap = bulb.getCapabilityFromRayTraceResult(hit.getHitVec(), hit.getFace(), RayTraceTargetSource.BUCKET);
        if (cap == null) {
            return ActionResultType.FAIL;
        }

        FluidStack simulate = cap.drain(BUCKET_AMOUNT, FluidAction.SIMULATE);
        if (simulate.isEmpty() || simulate.getAmount() < BUCKET_AMOUNT) {
            return ActionResultType.FAIL;
        } else {
            if (!player.world.isRemote) {
                FluidStack execute = cap.drain(BUCKET_AMOUNT, FluidAction.EXECUTE);
                ItemStack filledBucket = new ItemStack(execute.getFluid().getFilledBucket());
                player.getHeldItem(handIn).shrink(1);
                if (player.getHeldItem(handIn).isEmpty()) {
                    player.setHeldItem(handIn, filledBucket);
                } else {
                    player.addItemStackToInventory(filledBucket);
                }
            }
            AudioHelper.playerAudioEvent(player, Registry.GOO_WITHDRAW_SOUND.get(), PitchFormulas.HalfToOne.get());
            return ActionResultType.func_233537_a_(player.world.isRemote);
        }
    }

    @NotNull
    private ActionResultType tryBidirectionalCrystalInteraction(World worldIn, PlayerEntity player, Hand handIn, BlockRayTraceResult hit, BulbTile tile, BulbTile bulb) {

        if (bulb.hasCrystal()) {
            if (!worldIn.isRemote) {
                bulb.spitOutCrystal(player, hit.getFace());
            }
            return ActionResultType.func_233537_a_(worldIn.isRemote);
        } else {
            return tryFeedingBulbCrystal(worldIn, player, handIn, tile);
        }
    }

    @NotNull
    private ActionResultType tryFeedingBulbCrystal(World worldIn, PlayerEntity player, Hand handIn, BulbTile tile) {
        // bulb is empty so it can take a crystal if you're holding one.
        Item item = player.getHeldItem(handIn).getItem();
        if (!item.equals(Items.QUARTZ) && !(item instanceof CrystallizedGooAbstract)) {
            return ActionResultType.FAIL;
        }
        if (!worldIn.isRemote) {
            player.getHeldItem(handIn).shrink(1);
            tile.addCrystal(item);
        }
        return ActionResultType.func_233537_a_(worldIn.isRemote);
    }
}
