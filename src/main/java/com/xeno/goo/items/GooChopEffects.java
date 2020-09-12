package com.xeno.goo.items;

import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.client.particle.HeartParticle;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class GooChopEffects
{
    public static void resolve(ItemStack stack, LivingEntity attacker, LivingEntity target)
    {
        if (target.world.isRemote()) {
            return;
        }
        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return;
        }

        FluidStack goo = cap.getFluidInTank(0);
        if (goo.isEmpty()) {
            return;
        }

        cap.drain(2, IFluidHandler.FluidAction.EXECUTE);
        
        doGooEffect(goo, attacker, target);
    }

    private static void doGooEffect(FluidStack goo, LivingEntity attacker, LivingEntity target)
    {
        if (goo.getFluid().equals(Registry.AQUATIC_GOO.get())) {
            aquaChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            chromaChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.CRYSTAL_GOO.get())) {
            crystalChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.DECAY_GOO.get())) {
            decayChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.EARTHEN_GOO.get())) {
            earthChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.ENERGETIC_GOO.get())) {
            energyChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.FAUNAL_GOO.get())) {
            faunaChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.FLORAL_GOO.get())) {
            floraChop(attacker, target);
            return;
        }
    }

    private static void aquaChop(LivingEntity attacker, LivingEntity target)
    {
        if (target.isImmuneToFire()) {
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity) attacker), 6.0f);
        } else {
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity) attacker), 3.0f);
        }
        Vector3d knock = attacker.getPositionVec().subtract(target.getPositionVec());
        target.applyKnockback(3.0f, knock.getX(), knock.getZ());
        target.getEntityWorld().addParticle(ParticleTypes.BUBBLE, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }

    private static void chromaChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof SheepEntity) {
            int dyeColor = attacker.getEntityWorld().getRandom().nextInt(DyeColor.values().length);
            DyeColor dye = DyeColor.values()[dyeColor];
            ((SheepEntity) target).setFleeceColor(dye);
            target.getEntityWorld().addParticle(ParticleTypes.HEART, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
        } else {
            target.addPotionEffect(new EffectInstance(Effects.BLINDNESS, 60));
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity) attacker), 3.0f);
        }
    }

    private static void crystalChop(LivingEntity attacker, LivingEntity target)
    {
        target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 7.0f);
        target.getEntityWorld().addParticle(ParticleTypes.CRIT, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }

    private static void decayChop(LivingEntity attacker, LivingEntity target)
    {
        if (target.isEntityUndead()) {
            target.heal(6.0f);
            target.getEntityWorld().addParticle(ParticleTypes.HEART, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
        } else {
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity) attacker), 6.0f);
            target.getEntityWorld().addParticle(ParticleTypes.SMOKE, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
        }
    }

    private static void earthChop(LivingEntity attacker, LivingEntity target)
    {
        target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 3.0f);
        target.getEntityWorld().addParticle(ParticleTypes.CRIT, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }

    private static void energyChop(LivingEntity attacker, LivingEntity target)
    {
        target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 7.0f);
        Vector3d knock = attacker.getPositionVec().subtract(target.getPositionVec());
        target.applyKnockback(3.0f, knock.getX(), knock.getZ());
        target.getEntityWorld().addParticle(ParticleTypes.EXPLOSION, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }

    private static void faunaChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            target.heal(2.0f);
            target.getEntityWorld().addParticle(ParticleTypes.HEART, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
        } else {
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 2.0f);
        }
    }

    private static void floraChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            target.heal(1.0f);
            target.addPotionEffect(new EffectInstance(Effects.REGENERATION, 60));
        } else {
            if (!target.isEntityUndead()) {
                target.addPotionEffect(new EffectInstance(Effects.POISON, 60));
            }
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 2.0f);
        }
        target.getEntityWorld().addParticle(ParticleTypes.COMPOSTER, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }

    private static void fungiChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            target.heal(1.0f);
            target.addPotionEffect(new EffectInstance(Effects.POISON, 60));
        } else {
            if (!target.isEntityUndead()) {
                target.addPotionEffect(new EffectInstance(Effects.REGENERATION, 60));
            }
            target.attackEntityFrom(DamageSource.causePlayerDamage((PlayerEntity)attacker), 2.0f);
        }
        target.getEntityWorld().addParticle(ParticleTypes.COMPOSTER, target.getPosX(), target.getPosY(), target.getPosZ(), 0d, 0.2d, 0d);
    }
}
