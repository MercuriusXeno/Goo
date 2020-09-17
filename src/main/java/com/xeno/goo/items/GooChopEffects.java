package com.xeno.goo.items;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.setup.Registry;
import com.xeno.goo.tiles.FluidHandlerHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.player.PlayerEntity;
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
    private static final int GEOMANCY_DRAIN = 16;
    private static final int NORMAL_DRAIN = 9;

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

    public static boolean resolve(ItemStack stack, LivingEntity attacker, Entity target)
    {
        if (!(target instanceof LivingEntity)) {
            return false;
        }

        IFluidHandlerItem cap = FluidHandlerHelper.capability(stack);
        if (cap == null) {
            return false;
        }

        boolean isGeomancy = Gauntlet.geomancy(stack);

        FluidStack goo = cap.drain(isGeomancy ? GEOMANCY_DRAIN : NORMAL_DRAIN, IFluidHandler.FluidAction.EXECUTE);
        if (goo.isEmpty()) {
            return false;
        }

        doGooEffect(goo, isGeomancy, attacker, (LivingEntity)target);
        return true;
    }

    private static BasicParticleType particleTypeFromGoo(FluidStack fluidInTank)
    {
        return Registry.fallingParticleFromFluid(fluidInTank.getFluid());
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

    private static void doGooEffect(FluidStack goo, boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        tryGooParticles(goo, attacker, target);

        if (attacker instanceof PlayerEntity) {
            attacker.getEntityWorld().playSound((PlayerEntity)attacker,
                    target.getPosX(), target.getPosY(), target.getPosZ(), Registry.GOO_CHOP_SOUND.get(),
                    SoundCategory.PLAYERS, 1.0f, attacker.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f);
        } else {
            attacker.getEntityWorld().playSound(target.getPosX(), target.getPosY(), target.getPosZ(),
                    Registry.GOO_CHOP_SOUND.get(), SoundCategory.PLAYERS, 1.0f,
                    attacker.getEntityWorld().rand.nextFloat() * 0.5f + 0.5f, false);
        }

        if (attacker.getEntityWorld().isRemote()) {
            return;
        }

        // all hits have some knockback
        knockback(attacker, target, 0.3f);

        if (goo.getFluid().equals(Registry.AQUATIC_GOO.get())) {
            aquaChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.CHROMATIC_GOO.get())) {
            chromaChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.CRYSTAL_GOO.get())) {
            crystalChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.DECAY_GOO.get())) {
            decayChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.EARTHEN_GOO.get())) {
            earthChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.ENERGETIC_GOO.get())) {
            energyChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.FAUNAL_GOO.get())) {
            faunaChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.FLORAL_GOO.get())) {
            floraChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.FUNGAL_GOO.get())) {
            fungiChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.HONEY_GOO.get())) {
            honeyChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.LOGIC_GOO.get())) {
            logicChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.METAL_GOO.get())) {
            metalChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.MOLTEN_GOO.get())) {
            moltenChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.OBSIDIAN_GOO.get())) {
            obsidianChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.REGAL_GOO.get())) {
            regalChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.SLIME_GOO.get())) {
            slimeChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.SNOW_GOO.get())) {
            snowChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.VITAL_GOO.get())) {
            vitalChop(isGeomancy, attacker, target);
            return;
        }

        if (goo.getFluid().equals(Registry.WEIRD_GOO.get())) {
            weirdChop(isGeomancy, attacker, target);
            return;
        }
    }

    private static void aquaChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target.isImmuneToFire()) {
            attack(attacker, target, 5.0f, isGeomancy);
        } else {
            attack(attacker, target, 3.0f, isGeomancy);
        }
    }

    private static void chromaChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof SheepEntity) {
            int dyeColor = attacker.getEntityWorld().getRandom().nextInt(DyeColor.values().length);
            DyeColor dye = DyeColor.values()[dyeColor];
            ((SheepEntity) target).setFleeceColor(dye);
        } else {
            effect(target, Effects.BLINDNESS, 120, isGeomancy);
            attack(attacker, target, 3.0f, isGeomancy);
        }
    }

    private static void crystalChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 7.0f, isGeomancy);
    }

    private static void decayChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target.isEntityUndead()) {
            heal(attacker, target, 3.0f, isGeomancy);
        } else {
            attack(attacker, target, 5.0f, isGeomancy);
            effect(target, Effects.WITHER, 120, isGeomancy);
        }
    }

    private static void earthChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 3.0f, isGeomancy);
    }

    private static void energyChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 5.0f, isGeomancy);
        knockback(attacker, target, 1.0f);
    }

    private static void faunaChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            heal(attacker, target, 2.0f, isGeomancy);
        } else {
            attack(attacker, target, 2.0f, isGeomancy);
        }
    }

    private static void floraChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            heal(attacker, target, 2.0f, isGeomancy);
            effect(target, Effects.REGENERATION, 120, isGeomancy);
        } else {
            if (!target.isEntityUndead()) {
                effect(target, Effects.POISON, 120, isGeomancy);
            }
            attack(attacker, target, 2.0f, isGeomancy);
        }
    }

    private static void fungiChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof AnimalEntity) {
            attack(attacker, target, 2.0f, isGeomancy);
            effect(target, Effects.POISON, 120, isGeomancy);
        } else {
            if (!target.isEntityUndead()) {
                heal(attacker, target, 2.0f, isGeomancy);
                effect(target, Effects.REGENERATION, 120, isGeomancy);
            }
        }
    }

    private static void honeyChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target instanceof PlayerEntity) {
            FoodStats stats = ((PlayerEntity) target).getFoodStats();
            stats.setFoodLevel(stats.getFoodLevel() + 1);
            stats.setFoodSaturationLevel(stats.getSaturationLevel() + 1f);
        }
        if (target instanceof AnimalEntity) {
            heal(attacker, target, 2.0f, isGeomancy);
            effect(target, Effects.REGENERATION, 120, isGeomancy);
        } else {
            if (!target.isEntityUndead()) {
                attack(attacker, target, 2.0f, isGeomancy);
            }
            effect(target, Effects.SLOWNESS, 120, isGeomancy);
        }
    }

    private static void logicChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 2.0f, isGeomancy);
        effect(target, Effects.SLOWNESS, 120, isGeomancy);
        effect(target, Effects.WEAKNESS, 120, isGeomancy);
    }

    private static void metalChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 5.0f, isGeomancy);
    }

    private static void moltenChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 5.0f, true, isGeomancy);
    }

    private static void obsidianChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 7.0f, isGeomancy);
    }

    private static void regalChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 5.0f, isGeomancy);
        effect(target, Effects.WEAKNESS, 120, isGeomancy);
        effect(target, Effects.MINING_FATIGUE, 120, isGeomancy);
    }

    private static void slimeChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, 3.0f, isGeomancy);
        knockback(attacker, target, 1.0f);
        if (!target.isEntityUndead()) {
            effect(target, Effects.POISON, 120, isGeomancy);
        }
        effect(target, Effects.WEAKNESS, 120, isGeomancy);
        effect(target, Effects.MINING_FATIGUE, 120, isGeomancy);
    }

    private static void snowChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        attack(attacker, target, target.isImmuneToFire() ? 6.0f : 3.0f, isGeomancy);
        effect(target, Effects.SLOWNESS, 120, isGeomancy);
    }

    private static void vitalChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        if (target.isEntityUndead()) {
            attack(attacker, target, 5.0f, isGeomancy);
        } else {
            heal(attacker, target, 3.0f, isGeomancy);
        }
    }

    private static void weirdChop(boolean isGeomancy, LivingEntity attacker, LivingEntity target)
    {
        heal(target, attacker, 2.0f, isGeomancy);
        attack(attacker, target, 3.0f, isGeomancy);
    }
}
