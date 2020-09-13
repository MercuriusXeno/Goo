package com.xeno.goo.items;

import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.client.particle.HeartParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.particles.RedstoneParticleData;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class GooChopEffects
{
    private static void particles(LivingEntity target, IParticleData type)
    {
        float width = target.getWidth();
        Vector3d centerTop = target.getPositionVec().add(width / 2d, target.getHeight(), width / 2d);
        for (int i = 0; i < 4; i++) {
            float offsetX = target.world.rand.nextFloat() - 0.5f;
            float offsetZ = target.world.rand.nextFloat() - 0.5f;

            target.getEntityWorld()
                    .addParticle(type, centerTop.x + offsetX, centerTop.y, centerTop.z + offsetZ, 0d, 0.0d, 0d);
        }
    }

    private static void attack(LivingEntity attacker, LivingEntity target, float v)
    {
        attack(attacker, target, v, false);
    }

    private static void attack(LivingEntity attacker, LivingEntity target, float v, boolean isFire)
    {
        if (attacker instanceof PlayerEntity) {
            DamageSource source = DamageSource.causePlayerDamage((PlayerEntity) attacker);
            if (isFire) {
                source.setFireDamage();
            }
            target.attackEntityFrom(source, v);
        }
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

    public static boolean resolve(ItemStack stack, LivingEntity attacker, Entity target)
    {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return false;
        }

        FluidStack goo = cap.getFluidInTank(0);
        if (goo.isEmpty()) {
            return false;
        }

        cap.drain(2, IFluidHandler.FluidAction.EXECUTE);
        
        doGooEffect(goo, attacker, (LivingEntity)target);
        return true;
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

        if (goo.getFluid().equals(Registry.FUNGAL_GOO.get())) {
            fungiChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.HONEY_GOO.get())) {
            honeyChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.LOGIC_GOO.get())) {
            logicChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.METAL_GOO.get())) {
            metalChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.MOLTEN_GOO.get())) {
            moltenChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.OBSIDIAN_GOO.get())) {
            obsidianChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.REGAL_GOO.get())) {
            regalChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.SLIME_GOO.get())) {
            slimeChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.SNOW_GOO.get())) {
            snowChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.VITAL_GOO.get())) {
            vitalChop(attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.WEIRD_GOO.get())) {
            weirdChop(attacker, target);
            return;
        }
    }

    private static void aquaChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            if (target.isImmuneToFire()) {
                attack(attacker, target, 5.0f);
            } else {
                attack(attacker, target, 3.0f);
            }
            knockback(attacker, target, 1.0f);
        }
        particles(target, ParticleTypes.BUBBLE);
    }

    private static void chromaChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof SheepEntity) {
            if (!target.world.isRemote) {
                int dyeColor = attacker.getEntityWorld().getRandom().nextInt(DyeColor.values().length);
                DyeColor dye = DyeColor.values()[dyeColor];
                ((SheepEntity) target).setFleeceColor(dye);
            }
            particles(target, ParticleTypes.COMPOSTER);
        } else {
            if (!target.world.isRemote) {
                effect(target, Effects.BLINDNESS, 240);
                knockback(attacker, target, 1.0f);
                attack(attacker, target, 3.0f);
            }
            particles(target, ParticleTypes.COMPOSTER);
        }
    }

    private static void crystalChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 7.0f);
        }
        particles(target, ParticleTypes.CRIT);
    }

    private static void decayChop(LivingEntity attacker, LivingEntity target)
    {
        if (target.isEntityUndead()) {
            if (!target.world.isRemote) {
                target.heal(3.0f);
            }
            particles(target, ParticleTypes.HEART);
        } else {
            if (!target.world.isRemote) {
                attack(attacker, target, 5.0f);
                knockback(attacker, target, 1.0f);
                effect(target, Effects.WITHER, 240);
            }
            particles(target, ParticleTypes.SMOKE);
        }
    }

    private static void earthChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 3.0f);
            knockback(attacker, target, 1.0f);
        }
        particles(target, ParticleTypes.CRIT);
    }

    private static void energyChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 5.0f);
            knockback(attacker, target, 2.0f);
        }
        particles(target, ParticleTypes.EXPLOSION);
    }

    private static void faunaChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            if (!target.world.isRemote) {
                target.heal(2.0f);
            }
            particles(target, ParticleTypes.HEART);
        } else {
            if (!target.world.isRemote) {
                attack(attacker, target, 2.0f);
                knockback(attacker, target, 1.0f);
                particles(target, ParticleTypes.WITCH);
            }
        }
    }

    private static void floraChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            if (!target.world.isRemote) {
                target.heal(2.0f);
                effect(target, Effects.REGENERATION, 240);
            }
            particles(target, ParticleTypes.COMPOSTER);
        } else {
            if (!target.isEntityUndead()) {
                if (!target.world.isRemote) {
                    effect(target, Effects.POISON, 240);
                }
            }
            if (!target.world.isRemote) {
                attack(attacker, target, 2.0f);
                knockback(attacker, target, 1.0f);
            }
            particles(target, ParticleTypes.WITCH);
        }
    }

    private static void fungiChop(LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            if (!target.world.isRemote) {
                attack(attacker, target, 2.0f);
                knockback(attacker, target, 1.0f);
                effect(target, Effects.POISON, 240);
            }
            particles(target, ParticleTypes.CRIMSON_SPORE);
        } else {
            if (!target.isEntityUndead()) {
                if (!target.world.isRemote) {
                    target.heal(2.0f);
                    effect(target, Effects.REGENERATION, 240);
                }
                particles(target, ParticleTypes.CRIMSON_SPORE);
            }
        }
    }

    private static void honeyChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            if (target instanceof PlayerEntity) {
                FoodStats stats = ((PlayerEntity) target).getFoodStats();
                stats.setFoodLevel(stats.getFoodLevel() + 1);
                stats.setFoodSaturationLevel(stats.getSaturationLevel() + 1f);
            }
            if (target instanceof AnimalEntity) {
                target.heal(2.0f);
                effect(target, Effects.REGENERATION, 240);
            } else {
                if (!target.isEntityUndead()) {
                    attack(attacker, target, 2.0f);
                    knockback(attacker, target, 1.0f);
                }
                effect(target, Effects.SLOWNESS, 240);
            }
        }
    }

    private static void logicChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 2.0f);
            knockback(attacker, target, 1.0f);
            effect(target, Effects.SLOWNESS, 240);
            effect(target, Effects.WEAKNESS, 240);
        }
        particles(target, RedstoneParticleData.REDSTONE_DUST);
    }

    private static void metalChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 5.0f);
            knockback(attacker, target, 1.0f);
        }
        particles(target, ParticleTypes.CRIT);
    }

    private static void moltenChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 5.0f, true);
            knockback(attacker, target, 1.0f);
        }
        particles(target, ParticleTypes.FLAME);
    }

    private static void obsidianChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 7.0f, true);
            knockback(attacker, target, 1.0f);
        }
    }

    private static void regalChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 5.0f);
            knockback(attacker, target, 1.0f);
            effect(target, Effects.WEAKNESS, 240);
            effect(target, Effects.MINING_FATIGUE, 240);
        }
    }

    private static void slimeChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, 3.0f);
            knockback(attacker, target, 2.0f);
            if (!target.isEntityUndead()) {
                effect(target, Effects.POISON, 240);
            }
            effect(target, Effects.WEAKNESS, 240);
            effect(target, Effects.MINING_FATIGUE, 240);
        }
        particles(target, ParticleTypes.ITEM_SLIME);
    }

    private static void snowChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attack(attacker, target, target.isImmuneToFire() ? 6.0f : 3.0f);
            knockback(attacker, target, 1.0f);
            effect(target, Effects.SLOWNESS, 240);
        }
        particles(target, ParticleTypes.ITEM_SNOWBALL);
    }

    private static void vitalChop(LivingEntity attacker, LivingEntity target)
    {

        if (target.isEntityUndead()) {
            if (!target.world.isRemote) {
                attack(attacker, target, 5.0f);
                knockback(attacker, target, 1.0f);
            }
            particles(target, ParticleTypes.CRIT);
        } else {
            if (!target.world.isRemote) {
                target.heal(3.0f);
            }
            particles(target, ParticleTypes.HEART);
        }
    }

    private static void weirdChop(LivingEntity attacker, LivingEntity target)
    {
        if (!target.world.isRemote) {
            attacker.heal(3.0f);
            attack(attacker, target, 3.0f);
            knockback(attacker, target, 1.0f);
        }
        particles(target, ParticleTypes.CRIT);
        particles(target, ParticleTypes.WITCH);
        particles(attacker, ParticleTypes.HEART);
    }
}
