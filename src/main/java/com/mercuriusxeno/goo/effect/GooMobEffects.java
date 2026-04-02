package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

public class GooMobEffects {

    public static void apply(Level level, LivingEntity target, GooType type, @Nullable Entity thrower) {
        if (level.isClientSide()) return;

        switch (type) {
            case METAL -> metalJavelin(target);
            case CRYSTAL -> crystalFlechettes(level, target);
            case LEAF -> leafEntangle(target);
            case VITAL -> vitalClone(level, target);
            case SHROOM -> shroomDebuff(target);
            case ROCK -> rockPetrify(level, target);
            case BLAZE -> blazeIgnite(level, target);
            case FROST -> frostSnap(target);
            case TYPHOON -> typhoonLevitate(target);
            case GLOW -> glowSmite(target);
            case HEX -> hexCharm(target, thrower);
            case PULSE -> pulseShortCircuit(target);
            case NETHER -> netherWither(target);
            case ENDER -> enderTeleport(level, target);
            case AEON -> aeonTimeStop(target);
        }
    }

    // Metal: Javelin  - single-target damage
    private static void metalJavelin(LivingEntity target) {
        target.hurt(target.damageSources().magic(), 8.0f);
    }

    // Crystal: Flechettes  - AoE behind and around target
    private static void crystalFlechettes(Level level, LivingEntity target) {
        target.hurt(target.damageSources().magic(), 4.0f);
        // AoE to nearby entities
        AABB area = target.getBoundingBox().inflate(3.0);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (nearby != target) {
                nearby.hurt(nearby.damageSources().magic(), 2.0f);
            }
        }
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + 1.0, target.getZ(), 15, 1.5, 0.5, 1.5, 0.0);
        }
    }

    // Leaf: Entangle with vines  - slow + pull toward origin
    private static void leafEntangle(LivingEntity target) {
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 2));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 60, 0));
    }

    // Vital: Clone chance = 100/h^0.6
    private static void vitalClone(Level level, LivingEntity target) {
        if (!(target instanceof Mob mob)) return;
        float maxHealth = target.getMaxHealth();
        double cloneChance = 100.0 / Math.pow(maxHealth, 0.6);
        if (level.getRandom().nextFloat() * 100 < cloneChance) {
            // Spawn a copy
            Entity clone = mob.getType().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (clone != null) {
                clone.setPos(target.getX() + level.getRandom().nextGaussian(), target.getY(), target.getZ() + level.getRandom().nextGaussian());
                level.addFreshEntity(clone);
            }
        }
    }

    // Shroom: Blend of slow, weakness, poison. Bosses immune, players not.
    private static void shroomDebuff(LivingEntity target) {
        // Bosses are immune (check for wither/dragon by entity type)
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) return;
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 1));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 200, 1));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
    }

    // Rock: Petrify  - slow to immobility, then crush
    private static void rockPetrify(Level level, LivingEntity target) {
        // Apply heavy slowness (simulates petrification)
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 127));
        // If already "petrified" (max slowness), crush
        MobEffectInstance existing = target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        if (existing != null && existing.getAmplifier() >= 127 && level instanceof ServerLevel sl) {
            // Crush: kill and drop cobblestone
            target.hurt(target.damageSources().magic(), Float.MAX_VALUE);
            target.spawnAtLocation(sl, new ItemStack(Items.COBBLESTONE, 1 + level.getRandom().nextInt(3)));
        }
    }

    // Blaze: Ignite in radius, fire spreads
    private static void blazeIgnite(Level level, LivingEntity target) {
        target.igniteForSeconds(10);
        // Ignite nearby entities too
        AABB area = target.getBoundingBox().inflate(2.5);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!nearby.fireImmune()) {
                nearby.igniteForSeconds(5);
            }
        }
    }

    // Frost: Cold snap + freeze buildup
    private static void frostSnap(LivingEntity target) {
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) return;
        target.hurt(target.damageSources().freeze(), 4.0f);
        target.setTicksFrozen(target.getTicksFrozen() + 140); // add freeze buildup
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 3));
    }

    // Typhoon: Levitation
    private static void typhoonLevitate(LivingEntity target) {
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, 100, 1));
    }

    // Glow: Smite undead, harmless to others. Inflicts glow on survivors.
    private static void glowSmite(LivingEntity target) {
        if (target.isInvertedHealAndHarm()) {
            // Undead  - solar damage
            target.hurt(target.damageSources().magic(), 12.0f);
        }
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 200, 0));
        }
    }

    // Hex: Charm  - mob becomes passive to thrower, hostile to thrower's enemies
    private static void hexCharm(LivingEntity target, @Nullable Entity thrower) {
        if (!(target instanceof Mob mob)) return;
        float health = target.getHealth();
        double duration = 60.0 / Math.pow(health, 0.4);
        int ticks = (int)(duration * 20);
        // Simple charm: give weakness (reduces hostility) and glowing
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, ticks, 4));
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0));
        // TODO: Full charm AI override (make mob passive to thrower)
    }

    // Pulse: Short circuit  - mob stops all behavior
    private static void pulseShortCircuit(LivingEntity target) {
        // Simulate with stun: no AI + slowness
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            // Schedule AI re-enable (5 seconds)
            mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 100, 127));
        }
    }

    // Nether: Wither + halve current health
    private static void netherWither(LivingEntity target) {
        // Halve current health (if not wither-immune)
        if (target.getType() != EntityType.WITHER) {
            float halfHealth = target.getHealth() / 2.0f;
            target.setHealth(halfHealth);
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 200, 1));
        }
    }

    // Ender: Teleport far mobs near, near mobs far
    private static void enderTeleport(Level level, LivingEntity target) {
        Vec3 pos = target.position();
        double offsetX = (level.getRandom().nextDouble() - 0.5) * 32.0;
        double offsetZ = (level.getRandom().nextDouble() - 0.5) * 32.0;
        target.teleportTo(pos.x + offsetX, pos.y, pos.z + offsetZ);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
    }

    // Aeon: Time stop + time-reversal ritual progress
    private static void aeonTimeStop(LivingEntity target) {
        if (!(target instanceof Mob mob)) return;
        float maxHealth = target.getMaxHealth();
        double eggChance = 100.0 / Math.pow(maxHealth, 0.6);
        // Apply stasis
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0));
        // TODO: Accumulate ritual progress across multiple applications
        // TODO: At 100% → convert to egg item
    }
}
