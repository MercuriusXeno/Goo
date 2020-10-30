package com.xeno.goo.blocks;

import com.mojang.datafixers.types.Func;
import com.xeno.goo.items.ItemsRegistry;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.CrystalNestTile;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.IntegerProperty;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.village.PointOfInterest;
import net.minecraft.village.PointOfInterestType;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

public class CrystalNest extends BeehiveBlock {
    public static final DirectionProperty FACING = HorizontalBlock.HORIZONTAL_FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.HONEY_LEVEL;
    public CrystalNest() {
        super(Properties.create(Material.GLASS)
                .sound(SoundType.GLASS)
                .hardnessAndResistance(0.5f)
        );
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
        spawnAsEntity(world, pos, new ItemStack(ItemsRegistry.CrystalComb.get(), 3));
    }

    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        ItemStack itemstack = player.getHeldItem(handIn);
        int i = state.get(HONEY_LEVEL);
        if (i >= 5) {
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

    public void animateTick(BlockState stateIn, World worldIn, BlockPos pos, Random rand) {
        if (stateIn.get(HONEY_LEVEL) >= 5) {
            for(int i = 0; i < rand.nextInt(1) + 1; ++i) {
                this.addCrystalParticle(worldIn, pos, stateIn);
            }
        }

    }

    private void addCrystalParticle(World worldIn, BlockPos pos, BlockState stateIn) {
        // TODO
    }

    @Override
    public boolean hasTileEntity(BlockState state) {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public TileEntity createTileEntity(BlockState state, IBlockReader world) {
        return new CrystalNestTile();
    }
}
