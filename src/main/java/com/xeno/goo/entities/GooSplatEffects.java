package com.xeno.goo.entities;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;

public class GooSplatEffects
{
    private static void attack(LivingEntity attacker, LivingEntity target, float v)
    {
        attack(attacker, target, v, 0);
    }

    private static void attack(LivingEntity attacker, LivingEntity target, float v, int fireDuration)
    {
        if (attacker instanceof PlayerEntity) {
            DamageSource source = DamageSource.causePlayerDamage((PlayerEntity) attacker);
            if (fireDuration > 0) {
                source.setFireDamage();
                target.setFire(fireDuration);
            }
            target.attackEntityFrom(source, v);
        }
    }

    private static void heal(LivingEntity target, float v)
    {
        target.heal(v);
    }

    private static void effect(LivingEntity target, Effect effect, int i)
    {
        target.addPotionEffect(new EffectInstance(effect, i));
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

    public static void spawnParticles(GooBlob e)
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

    public static void resolve(Entity sender, GooBlob entity, World world, BlockPos pos, Direction face, BlockState state)
    {

        if (sender instanceof PlayerEntity) {
            sender.getEntityWorld().playSound((PlayerEntity)sender,
                    pos.getX(), pos.getY(), pos.getZ(), Registry.GOO_SPLAT_SOUND.get(),
                    SoundCategory.PLAYERS, 1.0f, sender.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f);
        } else {
            sender.getEntityWorld().playSound(pos.getX(), pos.getY(), pos.getZ(),
                    Registry.GOO_SPLAT_SOUND.get(), SoundCategory.PLAYERS, 1.0f,
                    sender.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f, false);
        }

        FluidStack goo = entity.goo;
        int intensity = Math.max(1, (int)Math.ceil(Math.sqrt(goo.getAmount())) - 1);
        if (goo.getFluid().equals(Registry.AQUATIC_GOO.get())) {
            aquaSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            chromaSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.CRYSTAL_GOO.get())) {
            crystalSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.DECAY_GOO.get())) {
            decaySplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.EARTHEN_GOO.get())) {
            earthSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.ENERGETIC_GOO.get())) {
            energySplat(intensity, sender, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FAUNAL_GOO.get())) {
            faunaSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FLORAL_GOO.get())) {
            floraSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.FUNGAL_GOO.get())) {
            fungiSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.HONEY_GOO.get())) {
            honeySplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.LOGIC_GOO.get())) {
            logicSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.METAL_GOO.get())) {
            metalSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.MOLTEN_GOO.get())) {
            moltenSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.OBSIDIAN_GOO.get())) {
            obsidianSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.REGAL_GOO.get())) {
            regalSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.SLIME_GOO.get())) {
            slimeSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.SNOW_GOO.get())) {
            snowSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.VITAL_GOO.get())) {
            vitalSplat(intensity, world, pos, face, state);
            return;
        }

        if (goo.getFluid().equals(Registry.WEIRD_GOO.get())) {
            weirdSplat(intensity, world, pos, face, state);
            return;
        }
    }

    private static void aquaSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // hydrate farmland
        if (state.getBlock().equals(Blocks.FARMLAND)) {
            int hydration = state.get(FarmlandBlock.MOISTURE);
            if (hydration < 7) {
                int newHydration = Math.min(7, hydration + intensity);

                if (world.isRemote()) {
                    world.setBlockState(pos, state.with(FarmlandBlock.MOISTURE, newHydration));
                }
            }
        }

        // cool lava
        if (state.getFluidState().getFluid().isEquivalentTo(Fluids.LAVA)) {
            // spawn some sizzly smoke and sounds
            if (world.isRemote()) {
                if (state.getFluidState().isSource()) {
                    world.setBlockState(pos, net.minecraftforge.event.ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, pos, Blocks.OBSIDIAN.getDefaultState()));
                } else {
                    world.setBlockState(pos, net.minecraftforge.event.ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, pos, Blocks.COBBLESTONE.getDefaultState()));
                }
            }
            world.playEvent(1501, pos, 0); // sizzly bits
        }

        // edify non-source water to source water
        if (state.getFluidState().getFluid().isEquivalentTo(Fluids.WATER)) {
            if (world.isRemote()) {
                if (!state.getFluidState().isSource()) {
                    world.setBlockState(pos, Blocks.WATER.getDefaultState().with(BlockStateProperties.LEVEL_1_8, 8));
                }
            }
        }

        // extinguish fires
        if (state.getBlock().equals(Blocks.FIRE)) {
            world.playEvent(null, 1009, pos, 0);
            if (world.isRemote()) {
                world.removeBlock(pos, false);
            }
        }
    }

    private static void chromaSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP
        // dye things? TODO
    }

    private static void crystalSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP
        // not really sure what crystal should do
    }

    private static void decaySplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
        // decay:

        // bush to dead bush

        // leaves to air

        // grass to dirt

        // vines decay
    }

    private static void earthSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP
        // not really sure what crystal should do
    }

    private static void energySplat(int intensity, Entity sender, World world, BlockPos pos, Direction face, BlockState state)
    {
        pos = pos.offset(face);
        Explosion explosion = new Explosion(world, sender,
                pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                intensity, false, Explosion.Mode.BREAK);
        if (net.minecraftforge.event.ForgeEventFactory.onExplosionStart(world, explosion)) return;

        explosion.doExplosionA();
        explosion.doExplosionB(true);
    }

    private static void faunaSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP
        // not really sure what fauna should do
    }

    private static void floraSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO
        // dirt to grass

        // chance of bonemeal?

        // grow growables?
    }

    private static void fungiSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO

        // chance of brown/red mushrooms?
    }

    private static void honeySplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP

        // not sure what things honey can do without a bit more work, finite fluid, sticky patch or something.
    }

    private static void logicSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO

        // activate or toggle power states on buttons and switches.

        // maybe other redstone things I haven't considered, temporarily power things, et al.
    }

    private static void metalSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP

        // not sure what else metal should do
    }

    private static void moltenSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        if (world.isRemote()) {
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
    }

    private static void obsidianSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP

        // not sure what else obsidian should do
    }

    private static void regalSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP

        // not sure what else regal should do
    }

    private static void slimeSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO

        // chance at a slime spawn might be a little exploitable
        // not sure what else we can do here
    }

    private static void snowSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // cool lava
        if (state.getFluidState().getFluid().isEquivalentTo(Fluids.LAVA)) {
            // spawn some sizzly smoke and sounds
            if (world.isRemote()) {
                if (state.getFluidState().isSource()) {
                    world.setBlockState(pos, net.minecraftforge.event.ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, pos, Blocks.OBSIDIAN.getDefaultState()));
                } else {
                    world.setBlockState(pos, net.minecraftforge.event.ForgeEventFactory.fireFluidPlaceBlockEvent(world, pos, pos, Blocks.COBBLESTONE.getDefaultState()));
                }
            }
            world.playEvent(1501, pos, 0); // sizzly bits
        }

        // extinguish fires
        if (state.getBlock().equals(Blocks.FIRE)) {
            world.playEvent(null, 1009, pos, 0);
            if (world.isRemote()) {
                world.removeBlock(pos, false);
            }
        }

        // freeze water
        if (state.getFluidState().getFluid().isEquivalentTo(Fluids.WATER)) {
            if (world.isRemote()) {
                if (state.getFluidState().isSource()) {
                    world.setBlockState(pos, Blocks.ICE.getDefaultState());
                }
            }
        }
    }

    private static void vitalSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // NO OP

        // not sure what to do with vital here
    }

    private static void weirdSplat(int intensity, World world, BlockPos pos, Direction face, BlockState state)
    {
        // TODO

        // I had weird plans for weird but they're a little complicated. For now this is a NO OP.
    }
}
