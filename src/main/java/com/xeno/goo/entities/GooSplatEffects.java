package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.item.DyeColor;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

public class GooSplatEffects
{
    private static void attack(LivingEntity attacker, LivingEntity target, float v, boolean isGeomancy)
    {
        if (isGeomancy) {
            v += 2.0f;
        }
        attack(attacker, target, v, false, isGeomancy);
    }

    private static void attack(LivingEntity attacker, LivingEntity target, float v, boolean isFire, boolean isGeomancy)
    {
        if (attacker instanceof PlayerEntity) {
            DamageSource source = DamageSource.causePlayerDamage((PlayerEntity) attacker);
            if (isFire) {
                source.setFireDamage();
                target.setFire((int)v + (isGeomancy ? 3 : 0));
            }
            target.attackEntityFrom(source, v);
        }
    }

    private static void heal(LivingEntity attacker, LivingEntity target, float v, boolean isGeomancy)
    {
        if (isGeomancy) {
            v += 2.0f;
        }
        target.heal(v);
    }

    private static void effect(LivingEntity target, Effect effect, int i, boolean isGeomancy)
    {
        target.addPotionEffect(new EffectInstance(effect, i * (isGeomancy ? 2 : 1)));
    }

    private static void knockback(LivingEntity attacker, LivingEntity target, float v)
    {
        Vector3d attackerCenter = attacker.getPositionVec().add(attacker.getWidth() / 2d, 0d, attacker.getWidth() / 2d);
        Vector3d targetCenter = target.getPositionVec().add(target.getWidth() / 2d, 0d, target.getWidth() / 2d);
        Vector3d knock = attackerCenter.subtract(targetCenter);
        target.applyKnockback(v, knock.getX(), knock.getZ());
    }

    private static BasicParticleType particleTypeFromGoo(FluidStack fluidInTank)
    {
        return Registry.fallingParticleFromFluid(fluidInTank.getFluid());
    }

    private static void spawnParticles(GooEntity e)
    {
        if (!(e.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        if (e.goo.getFluid() instanceof GooFluid) {
            BasicParticleType type = particleTypeFromGoo(e.goo);
            if (type == null) {
                return;
            }
            Vector3d spawnVec = e.getPositionVec();
            // give it a bit of randomness around the hit location
            double offX = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);
            double offZ = (e.cubicSize() / 2d) * (e.getEntityWorld().rand.nextFloat() - 0.5f);

            ((ServerWorld)e.getEntityWorld()).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z, e.goo.getAmount(),
                    offX, e.cubicSize(), offZ, 0.2d);
        }
    }

    private static void tryGooParticles(FluidStack goo, LivingEntity attacker, LivingEntity target)
    {
        if (!(target.getEntityWorld() instanceof ServerWorld)) {
            return;
        }
        // we should be able to guarantee the fluid has goo particles, so spawn a mess of them
        if (goo.getFluid() instanceof GooFluid) {
            BasicParticleType type = particleTypeFromGoo(goo);
            if (type == null) {
                return;
            }
            Vector3d spawnVec = target.getPositionVec();
            // give it a bit of randomness around the critter
            double offX = (target.getWidth() / 2d) * (target.getEntityWorld().rand.nextFloat() - 0.5f);
            double offZ = (target.getWidth() / 2d) * (target.getEntityWorld().rand.nextFloat() - 0.5f);

            ((ServerWorld)target.getEntityWorld()).spawnParticle(type, spawnVec.x, spawnVec.y, spawnVec.z, 12,
                    offX, target.getHeight(), offZ, 1.0d);
        }
    }


    public static void resolve(Entity sender, GooEntity entity, World world, BlockPos pos, Direction face, BlockState state)
    {
        spawnParticles(entity);

        if (sender instanceof PlayerEntity) {
            sender.getEntityWorld().playSound((PlayerEntity)sender,
                    pos.getX(), pos.getY(), pos.getZ(), Registry.GOO_SPLAT_SOUND.get(),
                    SoundCategory.PLAYERS, 1.0f, sender.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f);
        } else {
            sender.getEntityWorld().playSound(pos.getX(), pos.getY(), pos.getZ(),
                    Registry.GOO_SPLAT_SOUND.get(), SoundCategory.PLAYERS, 1.0f,
                    sender.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f, false);
        }

        if (sender.getEntityWorld().isRemote()) {
            return;
        }

        FluidStack goo = entity.goo;
        if (goo.getFluid().equals(Registry.AQUATIC_GOO.get())) {
            aquaSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            chromaSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.CRYSTAL_GOO.get())) {
            crystalSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.DECAY_GOO.get())) {
            decaySplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.EARTHEN_GOO.get())) {
            earthSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.ENERGETIC_GOO.get())) {
            energySplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FAUNAL_GOO.get())) {
            faunaSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FLORAL_GOO.get())) {
            floraSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FUNGAL_GOO.get())) {
            fungiSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.HONEY_GOO.get())) {
            honeySplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.LOGIC_GOO.get())) {
            logicSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.METAL_GOO.get())) {
            metalSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.MOLTEN_GOO.get())) {
            moltenSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.OBSIDIAN_GOO.get())) {
            obsidianSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.REGAL_GOO.get())) {
            regalSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.SLIME_GOO.get())) {
            slimeSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.SNOW_GOO.get())) {
            snowSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.VITAL_GOO.get())) {
            vitalSplat(world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.WEIRD_GOO.get())) {
            weirdSplat(world, pos, face, state);
            return;
        }
    }

    private static void aquaSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        if (state.getBlock().equals(Blocks.FARMLAND) && state.get(FarmlandBlock.MOISTURE) < 7) {
            world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, 7));
            return;
        }

        if (state.getMaterial() == Material.LAVA) {
            // spawn some sizzly smoke and sounds
            world.setBlockState(pos, net.minecraftforge.event.ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, pos, Blocks.OBSIDIAN.getDefaultState()));
            world.playEvent(1501, pos, 0); // sizzly bits
        }
    }

    private static void chromaSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void crystalSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void decaySplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void earthSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void energySplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void faunaSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void floraSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void fungiSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void honeySplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void logicSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void metalSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void moltenSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        BlockState blockstate = world.getBlockState(pos);
        if (CampfireBlock.canBeLit(blockstate)) {
            world.setBlockState(pos, blockstate.with(BlockStateProperties.LIT, Boolean.TRUE), 11);
        } else {
            BlockPos offPos = pos.offset(face);
            if (AbstractFireBlock.canLightBlock(world, offPos)) {
                BlockState offState = AbstractFireBlock.getFireForPlacement(world, offPos);
                world.setBlockState(offPos, offState, 11);
            }
        }
    }

    private static void obsidianSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void regalSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void slimeSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void snowSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void vitalSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }

    private static void weirdSplat(World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
    }
}
