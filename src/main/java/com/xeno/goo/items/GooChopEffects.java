package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.BasicParticleType;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.*;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class GooChopEffects
{
    private static final int GEOMANCY_DRAIN = 9;
    private static final int NORMAL_DRAIN = 4;

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

    public static boolean tryDoingChopEffect(ItemStack stack, LivingEntity attacker, Entity target)
    {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return false;
        }

        // geomancy lets you drain around twice as much goo to deal around 33% stronger effects (rounded up tho)
        FluidStack goo = cap.drain(NORMAL_DRAIN, IFluidHandler.FluidAction.EXECUTE);
        if (goo.isEmpty()) {
            return false;
        }

        doChopEffect(goo, getIntensity(goo.getAmount()), attacker, (LivingEntity)target);
        return true;
    }

    private static void doAudioAndVisuals(FluidStack goo, LivingEntity attacker, LivingEntity target)
    {
        tryGooParticles(goo, target);

        if (attacker instanceof PlayerEntity) {
            attacker.getEntityWorld().playSound((PlayerEntity)attacker,
                    target.getPosX(), target.getPosY(), target.getPosZ(), Registry.GOO_CHOP_SOUND.get(),
                    SoundCategory.PLAYERS, 1.0f, attacker.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f);
        } else {
            attacker.getEntityWorld().playSound(target.getPosX(), target.getPosY(), target.getPosZ(),
                    Registry.GOO_CHOP_SOUND.get(), SoundCategory.PLAYERS, 1.0f,
                    attacker.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f, false);
        }
    }

    private static int getIntensity(int amount)
    {
        return (int)Math.ceil(Math.sqrt(amount));
    }

    private static BasicParticleType particleTypeFromGoo(FluidStack fluidInTank)
    {
        return Registry.fallingParticleFromFluid(fluidInTank.getFluid());
    }

    private static void tryGooParticles(FluidStack goo, LivingEntity target)
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

    public static void doChopEffect(FluidStack gooStack, int intensity, LivingEntity attacker, LivingEntity target)
    {
        Fluid goo = gooStack.getFluid();
        doAudioAndVisuals(gooStack, attacker, target);

        if (attacker.getEntityWorld().isRemote()) {
            return;
        }

        // all hits have some knockback
        knockback(attacker, target, intensity * 0.1f);

        if (goo.getFluid().equals(Registry.AQUATIC_GOO.get())) {
            aquaChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            chromaChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.CRYSTAL_GOO.get())) {
            crystalChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.DECAY_GOO.get())) {
            decayChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.EARTHEN_GOO.get())) {
            earthChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.ENERGETIC_GOO.get())) {
            energyChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.FAUNAL_GOO.get())) {
            faunaChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.FLORAL_GOO.get())) {
            floraChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.FUNGAL_GOO.get())) {
            fungiChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.HONEY_GOO.get())) {
            honeyChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.LOGIC_GOO.get())) {
            logicChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.METAL_GOO.get())) {
            metalChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.MOLTEN_GOO.get())) {
            moltenChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.OBSIDIAN_GOO.get())) {
            obsidianChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.REGAL_GOO.get())) {
            regalChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.SLIME_GOO.get())) {
            slimeChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.SNOW_GOO.get())) {
            snowChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.VITAL_GOO.get())) {
            vitalChop(attacker, target, intensity);
            return;
        }

        if (goo.getFluid().equals(Registry.WEIRD_GOO.get())) {
            weirdChop(attacker, target, intensity);
            return;
        }
    }

    private static void aquaChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target.isImmuneToFire()) {
            attack(attacker, target, intensity * 2f - 1f);
        } else {
            attack(attacker, target, intensity);
        }
    }

    private static void chromaChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target instanceof SheepEntity) {
            int dyeColor = attacker.getEntityWorld().getRandom().nextInt(DyeColor.values().length);
            DyeColor dye = DyeColor.values()[dyeColor];
            ((SheepEntity) target).setFleeceColor(dye);
        } else {
            effect(target, Effects.BLINDNESS, intensity * 40);
            attack(attacker, target, intensity);
        }
    }

    private static void crystalChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 3f - 2f);
    }

    private static void decayChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target.isEntityUndead()) {
            heal(target, intensity);
        } else {
            attack(attacker, target, intensity + 1f);
            effect(target, Effects.WITHER, intensity * 40);
        }
    }

    private static void earthChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity);
    }

    private static void energyChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 2f);
        knockback(attacker, target, intensity);
    }

    private static void faunaChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target instanceof AnimalEntity) {
            heal(target, intensity - 1f);
        } else {
            attack(attacker, target, intensity - 1f);
        }
    }

    private static void floraChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target instanceof AnimalEntity) {
            heal(target, intensity - 1f);
            effect(target, Effects.REGENERATION, intensity * 20);
        } else {
            if (!target.isEntityUndead()) {
                effect(target, Effects.POISON, intensity * 20);
            }
            attack(attacker, target, intensity - 1f);
        }
    }

    private static void fungiChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity);
        if (!target.isEntityUndead()) {
            effect(target, Effects.POISON, intensity * 40);
        }
    }

    private static void honeyChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target instanceof PlayerEntity) {
            FoodStats stats = ((PlayerEntity) target).getFoodStats();
            stats.setFoodLevel(stats.getFoodLevel() + intensity - 1);
            stats.setFoodSaturationLevel(stats.getSaturationLevel() + intensity * 0.2f);
        }
        if (target instanceof AnimalEntity) {
            heal(target, intensity - 1f);
            effect(target, Effects.REGENERATION, intensity * 20);
        } else {
            if (!target.isEntityUndead()) {
                attack(attacker, target, intensity);
            }
            effect(target, Effects.SLOWNESS, intensity * 40);
        }
    }

    private static void logicChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity);
        effect(target, Effects.WEAKNESS, intensity * 40);
    }

    private static void metalChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 2f - 1f);
    }

    private static void moltenChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 2f - 1f, intensity * 3);
    }

    private static void obsidianChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 3f - 1f, intensity * 2);
    }

    private static void regalChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity * 2f - 1f);
        effect(target, Effects.WEAKNESS, intensity * 40);
        effect(target, Effects.MINING_FATIGUE, intensity * 40);
    }

    private static void slimeChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, intensity);
        knockback(attacker, target, intensity - 1);
        if (!target.isEntityUndead()) {
            effect(target, Effects.POISON, intensity * 40);
        }
        effect(target, Effects.WEAKNESS, intensity * 40);
        effect(target, Effects.MINING_FATIGUE, intensity * 40);
    }

    private static void snowChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        attack(attacker, target, target.isImmuneToFire() ? intensity * 3f - 2f : intensity);
        effect(target, Effects.SLOWNESS, intensity * 40);
    }

    private static void vitalChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        if (target.isEntityUndead()) {
            attack(attacker, target, intensity * 2f - 1);
        } else {
            heal(target, intensity);
        }
    }

    private static void weirdChop(LivingEntity attacker, LivingEntity target, int intensity)
    {
        heal(attacker, intensity - 1f);
        attack(attacker, target, intensity);
    }
}
