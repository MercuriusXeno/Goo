package com.xeno.goo.interactions;

import com.xeno.goo.GooMod;
import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.entities.GooBlob;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.block.SoundType;
import net.minecraft.block.TorchBlock;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootContext;
import net.minecraft.loot.LootParameters;
import net.minecraft.particles.BlockParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class Radiant
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.RADIANT_GOO.get(), "radiant_light", Radiant::radiantLight, Radiant::isValidForLightLocation);
    }

    private static boolean radiantLight(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        BlockState state = context.world().getBlockState(blockPos);
        return context.world().setBlockState(blockPos, BlocksRegistry.RadiantLight.get().getDefaultState()
            .with(BlockStateProperties.FACING, context.sideHit()));
    }

    private static boolean isValidForLightLocation(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        BlockState state = context.world().getBlockState(blockPos);
        return state.isAir(context.world(), blockPos) && context.blockState().isSolidSide(context.world(), context.blockPos(), context.sideHit());
    }

}
