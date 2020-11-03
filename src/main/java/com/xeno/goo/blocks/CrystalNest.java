package com.xeno.goo.blocks;

import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.tiles.CrystalNestTile;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

public class CrystalNest extends ContainerBlock {
    public static final DirectionProperty FACING = HorizontalBlock.HORIZONTAL_FACING;
    public static final BooleanProperty GOO_FULL = BooleanProperty.create("nest_goo_full");
    public CrystalNest() {
        super(Properties.create(Material.GLASS)
                .sound(SoundType.GLASS)
                .hardnessAndResistance(0.5f)
                .notSolid()
        );
        this.setDefaultState(this.stateContainer.getBaseState().with(GOO_FULL, false).with(FACING, Direction.NORTH));
    }

    public boolean hasComparatorInputOverride(BlockState state) {
        return true;
    }

    public int getComparatorInputOverride(BlockState blockState, World worldIn, BlockPos pos) {
        return blockState.get(GOO_FULL) ? 1 : 0;
    }

    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing().getOpposite());
    }

    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(GOO_FULL, FACING);
    }

    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void harvestBlock(World worldIn, PlayerEntity player, BlockPos pos, BlockState state, @Nullable TileEntity te, ItemStack stack) {
        super.harvestBlock(worldIn, player, pos, state, te, stack);
        if (!worldIn.isRemote && te instanceof CrystalNestTile) {
            CrystalNestTile crystalHive = (CrystalNestTile)te;
            if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) == 0) {
                crystalHive.scareBees(player, state, CrystalNestTile.State.PANIC);
                worldIn.updateComparatorOutputLevel(pos, this);
            }
        }
    }

    public static void dropHoneyComb(World world, BlockPos pos) {
        TileEntity e = world.getTileEntity(pos);
        if (e instanceof CrystalNestTile) {
            ((CrystalNestTile) e).resetGooAmount();;
            spawnAsEntity(world, pos, new ItemStack(ItemsRegistry.CrystalComb.get(), 3));
        }
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        ItemStack itemstack = player.getHeldItem(handIn);
        if (state.get(GOO_FULL)) {
            if (itemstack.getItem() == Items.SHEARS) {
                worldIn.playSound(player, player.getPosX(), player.getPosY(), player.getPosZ(), SoundEvents.BLOCK_BEEHIVE_SHEAR, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                dropHoneyComb(worldIn, pos);
                itemstack.damageItem(1, player, (playerEntity) -> {
                    playerEntity.sendBreakAnimation(handIn);
                });

                return ActionResultType.func_233537_a_(worldIn.isRemote);
            }
        }
        return super.onBlockActivated(state, worldIn, pos, player, handIn, hit);
    }

    @Override
    public void animateTick(BlockState state, World world, BlockPos pos, Random rand) {
        if (state.get(GOO_FULL)) {
            for(int i = 0; i < rand.nextInt(1) + 1; ++i) {
                this.addCrystalParticle(world, pos, state);
            }
        }
    }

    private void addCrystalParticle(World worldIn, BlockPos pos, BlockState stateIn) {
        // TODO
    }

    @Override
    public boolean hasTileEntity(BlockState state) { return true; }

    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) { return new CrystalNestTile(); }

    @Nullable
    public TileEntity createNewTileEntity(IBlockReader worldIn) {
        return new CrystalNestTile();
    }
}
