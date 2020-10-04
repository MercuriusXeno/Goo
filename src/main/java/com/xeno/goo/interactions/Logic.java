package com.xeno.goo.interactions;

import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.state.properties.AttachFace;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

public class Logic
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.LOGIC_GOO.get(), "logic_pulse", Logic::logicPulse);

        GooInteractions.registerPassThroughPredicate(Registry.LOGIC_GOO.get(), Logic::blobPassThroughPredicate);
    }

    private static boolean logicPulse(SplatContext context) {
        BlockPos pos = context.splat().getPosition();
        BlockState state = context.world().getBlockState(pos);
        if (context.world().getGameTime() % 20 != 0) {
            return false;
        }
        if (context.world() instanceof ServerWorld) {
            Vector3d particlePos = context.splat().getPositionVec();
            AxisAlignedBB bounds = context.splat().getBoundingBox();
            // vec representing the "domain" of the bounding box.
            Vector3d rangeVec = new Vector3d(
                    bounds.maxX - bounds.minX,
                    bounds.maxY - bounds.minY,
                    bounds.maxZ - bounds.minZ);
            for (int i = 0; i < 5; i++) {
                Vector3d finalPos = particlePos.add(
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.x,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.y,
                        (context.world().rand.nextDouble() - 0.5d) * rangeVec.z
                );
                ((ServerWorld) context.world()).spawnParticle(RedstoneParticleData.REDSTONE_DUST,
                        finalPos.x, finalPos.y, finalPos.z, 1, 0d, 0d, 0d, 0d);
            }
        }
        if (isValidLogicBlock(state)) {
            if (!isLegalStateAndSideHitCombo(state, context)) {
                return false;
            }
            // if it's a button or plate we bypass it if powered
            if (!(state.getBlock() instanceof LeverBlock) && state.get(BlockStateProperties.POWERED)) {
                return false;
            }
            // toggle the powered states based on what kind of powered state it needs/behaviors
            // specific to each block. Right now only basic mechanisms capable of sending
            // manual signals are supported.
            if (state.getBlock() instanceof LeverBlock) {
                toggleLever(state, context.world(), pos);
            } else if (state.getBlock() instanceof AbstractPressurePlateBlock) {
                togglePressurePlate(state, context.world(), pos, context);
            } else if (state.getBlock() instanceof AbstractButtonBlock) {
                toggleButton(state, context.world(), pos);
            }
            return true;
        }
        return false;
    }

    private static boolean isLegalStateAndSideHitCombo(BlockState state, SplatContext context) {
        if (state.getBlock() instanceof AbstractButtonBlock || state.getBlock() instanceof LeverBlock) {
            // has to be "in" the logical switch to do something to it, for consistency/style.
            if (context.sideHit() != matchAttachmentFace(state.get(BlockStateProperties.HORIZONTAL_FACING),
                    state.get(BlockStateProperties.FACE))) {
                return false;
            }
            return true;
        } else {
            if (context.sideHit() != Direction.UP) {
                return false;
            }
            return true;
        }
    }

    private static Direction matchAttachmentFace(Direction face, AttachFace attachFace) {
        switch(attachFace) {
            case WALL:
                return face;
            case CEILING:
                return Direction.DOWN;
            case FLOOR:
                return Direction.UP;
        }
        return Direction.UP; // ????
    }

    private static void toggleButton(BlockState state, World world, BlockPos pos) {
        AbstractButtonBlock button = (AbstractButtonBlock) state.getBlock();
        if (world.isRemote) {
            BlockState newState = state.func_235896_a_(BlockStateProperties.POWERED);
            if (newState.get(BlockStateProperties.POWERED)) {
                doLeverParticles(newState, world, pos, 1.0F);
            }
        } else {
            button.func_226910_d_(state, world, pos);
            SoundEvent stoneOrWood = button instanceof StoneButtonBlock ? SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON
                    : SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON;
            AudioHelper.headlessAudioEvent(world, pos, stoneOrWood, SoundCategory.BLOCKS, 0.3F,
                    AudioHelper.PitchFormulas.FlatOne);
        }
    }

    private static void togglePressurePlate(BlockState state, World world, BlockPos pos, SplatContext context) {
        AbstractPressurePlateBlock plate = (AbstractPressurePlateBlock) state.getBlock();
        plate.onEntityCollision(state, world, pos, context.splat());
    }

    private static void toggleLever(BlockState state, World world, BlockPos pos) {
        LeverBlock lever = (LeverBlock) state.getBlock();
        if (world.isRemote) {
            BlockState newState = state.func_235896_a_(BlockStateProperties.POWERED);
            if (newState.get(BlockStateProperties.POWERED)) {
                doLeverParticles(newState, world, pos, 1.0F);
            }
        } else {
            BlockState blockstate = lever.setPowered(state, world, pos);
            float f = blockstate.get(BlockStateProperties.POWERED) ? 0.6F : 0.5F;
            AudioHelper.headlessAudioEvent(world, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.3F, () -> f);
        }
    }

    private static void doLeverParticles(BlockState state, IWorld worldIn, BlockPos pos, float alpha) {
        Direction direction = state.get(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        Direction direction1 = getFacing(state).getOpposite();
        double d0 = (double)pos.getX() + 0.5D + 0.1D * (double)direction.getXOffset() + 0.2D * (double)direction1.getXOffset();
        double d1 = (double)pos.getY() + 0.5D + 0.1D * (double)direction.getYOffset() + 0.2D * (double)direction1.getYOffset();
        double d2 = (double)pos.getZ() + 0.5D + 0.1D * (double)direction.getZOffset() + 0.2D * (double)direction1.getZOffset();
        worldIn.addParticle(new RedstoneParticleData(1.0F, 0.0F, 0.0F, alpha), d0, d1, d2, 0.0D, 0.0D, 0.0D);
    }

    protected static Direction getFacing(BlockState state) {
        switch(state.get(BlockStateProperties.FACE)) {
            case CEILING:
                return Direction.DOWN;
            case FLOOR:
                return Direction.UP;
            default:
                return state.get(BlockStateProperties.HORIZONTAL_FACING);
        }
    }

    private static Boolean blobPassThroughPredicate(BlockRayTraceResult blockRayTraceResult, GooBlob gooBlob) {
        BlockState state = gooBlob.world.getBlockState(blockRayTraceResult.getPos());
        if (state.getBlock().hasTileEntity(state)) {
            return false;
        }

        return isValidLogicBlock(state);
    }

    private static Boolean isValidLogicBlock(BlockState state) {
        return state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof AbstractPressurePlateBlock
                || state.getBlock() instanceof AbstractButtonBlock;
    }
}
