package com.xeno.goo.interactions;

import com.xeno.goo.blocks.BlocksRegistry;
import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.library.AudioHelper.PitchFormulas;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.BlockState;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

public class Radiant
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.RADIANT_GOO;
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "radiant_light", Radiant::radiantLight, Radiant::isValidForLightLocation);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "radiant_hit", Radiant::entityHit);
    }

    private static boolean entityHit(BlobHitContext c) {
        // searing light on undead, lasts a sufficiently long time, deals extra damage
        if (c.victim().isEntityUndead()) {
            c.victim().setFire(60);
            c.damageVictim(5f);
        } else {
            c.damageVictim(3f);
        }
        for(int i = 0; i < 4; i++) {
            c.world().addParticle(ParticleTypes.SMOKE, c.blob().getPosX(), c.blob().getPosY(), c.blob().getPosZ(), 0d, 0.1d, 0d);
        }
        AudioHelper.entityAudioEvent(c.blob(), Registry.GOO_SIZZLE_SOUND.get(), SoundCategory.NEUTRAL, 1.0f, PitchFormulas.HalfToOne);
        c.knockback(1f);
        return true;
    }

    private static boolean radiantLight(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        return context.world().setBlockState(blockPos, BlocksRegistry.RadiantLight.get().getDefaultState()
            .with(BlockStateProperties.FACING, context.sideHit()));
    }

    private static boolean isValidForLightLocation(SplatContext context) {
        BlockPos blockPos = context.blockPos().offset(context.sideHit());
        BlockState state = context.world().getBlockState(blockPos);
        boolean isAir = state.isAir(context.world(), blockPos);
        boolean isReplaceable = state.getMaterial().isReplaceable();
        return (isAir || isReplaceable) && context.blockState().isSolidSide(context.world(), context.blockPos(), context.sideHit());
    }

}
