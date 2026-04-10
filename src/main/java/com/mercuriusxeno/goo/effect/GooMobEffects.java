package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies goo-type-specific mob effects when blobs hit living entities.
 * Each goo type has a unique effect dispatched through a type-indexed map.
 */
public final class GooMobEffects {

    // ── Damage values ──
    /** Metal javelin direct damage. */
    private static final float METAL_JAVELIN_DAMAGE = 8.0f;
    /** Crystal primary target damage. */
    private static final float CRYSTAL_PRIMARY_DAMAGE = 4.0f;
    /** Crystal AoE splash damage to nearby entities. */
    private static final float CRYSTAL_SPLASH_DAMAGE = 2.0f;
    /** Crystal flechette AoE radius in blocks. */
    private static final double CRYSTAL_AOE_RADIUS = 3.0;
    /** Frost cold-snap damage. */
    private static final float FROST_SNAP_DAMAGE = 4.0f;
    /** Glow smite damage to undead. */
    private static final float GLOW_SMITE_DAMAGE = 12.0f;

    // ── Particle parameters ──
    /** Vertical offset for crystal damage indicator particles. */
    private static final double CRYSTAL_PARTICLE_Y_OFFSET = 1.0;
    /** Crystal damage indicator particle count. */
    private static final int CRYSTAL_PARTICLE_COUNT = 15;
    /** Crystal damage indicator horizontal spread. */
    private static final double CRYSTAL_PARTICLE_H_SPREAD = 1.5;
    /** Crystal damage indicator vertical spread. */
    private static final double CRYSTAL_PARTICLE_V_SPREAD = 0.5;

    // ── Effect durations (ticks) ──
    /** Leaf entangle slowness duration. */
    private static final int LEAF_SLOW_DURATION = 100;
    /** Leaf entangle slowness amplifier. */
    private static final int LEAF_SLOW_AMPLIFIER = 2;
    /** Leaf poison duration. */
    private static final int LEAF_POISON_DURATION = 60;
    /** Shroom debuff duration (shared by all three effects). */
    private static final int SHROOM_DEBUFF_DURATION = 200;
    /** Rock petrify slowness duration. */
    private static final int ROCK_PETRIFY_DURATION = 100;
    /** Rock petrify slowness amplifier (max = immobility). */
    private static final int ROCK_PETRIFY_AMPLIFIER = 127;
    /** Maximum cobblestone drop from rock crush (exclusive upper bound for nextInt). */
    private static final int ROCK_CRUSH_DROP_BOUND = 3;
    /** Blaze primary target ignite duration in seconds. */
    private static final int BLAZE_IGNITE_SECONDS = 10;
    /** Blaze AoE ignite radius. */
    private static final double BLAZE_AOE_RADIUS = 2.5;
    /** Blaze AoE ignite duration for nearby entities in seconds. */
    private static final int BLAZE_NEARBY_IGNITE_SECONDS = 5;
    /** Frost freeze buildup ticks added on hit. */
    private static final int FROST_FREEZE_BUILDUP = 140;
    /** Frost slowness duration. */
    private static final int FROST_SLOW_DURATION = 60;
    /** Frost slowness amplifier. */
    private static final int FROST_SLOW_AMPLIFIER = 3;
    /** Typhoon levitation duration. */
    private static final int TYPHOON_LEVITATE_DURATION = 100;
    /** Glow glowing effect duration. */
    private static final int GLOW_EFFECT_DURATION = 200;
    /** Pulse stun slowness duration. */
    private static final int PULSE_STUN_DURATION = 100;
    /** Pulse stun slowness amplifier (max = immobility). */
    private static final int PULSE_STUN_AMPLIFIER = 127;
    /** Nether wither effect duration. */
    private static final int NETHER_WITHER_DURATION = 200;
    /** Nether health divisor (halves current health). */
    private static final float NETHER_HEALTH_DIVISOR = 2.0f;
    /** Aeon stasis glowing duration. */
    private static final int AEON_GLOW_DURATION = 60;

    // ── Hex charm parameters ──
    /** Hex charm base duration numerator. */
    private static final double HEX_CHARM_BASE_DURATION = 60.0;
    /** Hex charm health-scaling exponent. */
    private static final double HEX_CHARM_HEALTH_EXPONENT = 0.4;
    /** Ticks per second for charm duration conversion. */
    private static final int HEX_TICKS_PER_SECOND = 20;
    /** Hex weakness amplifier. */
    private static final int HEX_WEAKNESS_AMPLIFIER = 4;

    // ── Vital clone parameters ──
    /** Vital clone base chance numerator. */
    private static final double VITAL_CLONE_BASE_CHANCE = 100.0;
    /** Vital clone health-scaling exponent. */
    private static final double VITAL_CLONE_HEALTH_EXPONENT = 0.6;
    /** Vital clone percentage scale (random check against 100). */
    private static final int VITAL_CLONE_PERCENT_SCALE = 100;

    // ── Ender teleport parameters ──
    /** Ender teleport max offset (half of full 32-block range). */
    private static final double ENDER_TELEPORT_RANGE = 32.0;
    /** Ender teleport centering offset. */
    private static final double ENDER_TELEPORT_CENTER = 0.5;
    /** Ender teleport sound volume. */
    private static final float ENDER_SOUND_VOLUME = 1.0f;
    /** Ender teleport sound pitch. */
    private static final float ENDER_SOUND_PITCH = 1.0f;

    /** Per-type effect handler map. */
    private static final Map<GooType, Consumer<EffectContext>> EFFECTS =
            new EnumMap<>(Map.ofEntries(
        Map.entry(GooType.METAL, ctx -> metalJavelin(ctx.target())),
        Map.entry(GooType.CRYSTAL, ctx -> crystalFlechettes(ctx.level(), ctx.target())),
        Map.entry(GooType.LEAF, ctx -> leafEntangle(ctx.target())),
        Map.entry(GooType.VITAL, ctx -> vitalClone(ctx.level(), ctx.target())),
        Map.entry(GooType.SHROOM, ctx -> shroomDebuff(ctx.target())),
        Map.entry(GooType.ROCK, ctx -> rockPetrify(ctx.level(), ctx.target())),
        Map.entry(GooType.BLAZE, ctx -> blazeIgnite(ctx.level(), ctx.target())),
        Map.entry(GooType.FROST, ctx -> frostSnap(ctx.target())),
        Map.entry(GooType.TYPHOON, ctx -> typhoonLevitate(ctx.target())),
        Map.entry(GooType.GLOW, ctx -> glowSmite(ctx.target())),
        Map.entry(GooType.HEX, ctx -> hexCharm(ctx.target(), ctx.thrower())),
        Map.entry(GooType.PULSE, ctx -> pulseShortCircuit(ctx.target())),
        Map.entry(GooType.NETHER, ctx -> netherWither(ctx.target())),
        Map.entry(GooType.ENDER, ctx -> enderTeleport(ctx.level(), ctx.target())),
        Map.entry(GooType.AEON, ctx -> aeonTimeStop(ctx.target()))));

    /** Dispatch context: all parameters an effect handler might need. */
    private record EffectContext(Level level, LivingEntity target, @Nullable Entity thrower) {}

    private GooMobEffects() {}

    /**
     * Applies the mob effect for the given goo type to a living entity.
     *
     * @param level   the world
     * @param target  the entity to affect
     * @param type    the goo type whose effect to apply
     * @param thrower the entity that threw the blob, or null if unknown
     */
    public static void apply(Level level, LivingEntity target, GooType type, @Nullable Entity thrower) {
        if (level.isClientSide()) { return; }
        var handler = EFFECTS.get(type);
        if (handler != null) { handler.accept(new EffectContext(level, target, thrower)); }
    }

    /**
     * Deals direct magic damage to the target (metal javelin).
     *
     * @param target the entity to damage
     */
    private static void metalJavelin(LivingEntity target) {
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), METAL_JAVELIN_DAMAGE);
    }

    /**
     * Deals damage to the target and splash damage to nearby entities (crystal flechettes).
     *
     * @param level  the current level
     * @param target the primary target entity
     */
    private static void crystalFlechettes(Level level, LivingEntity target) {
        target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), CRYSTAL_PRIMARY_DAMAGE);
        splashDamageNearby(level, target);
        sendDamageParticles(level, target);
    }

    /** Deals splash magic damage to all living entities near the target.
     *
     * @param level  the current level
     * @param target the primary target (excluded from splash)
     */
    private static void splashDamageNearby(Level level, LivingEntity target) {
        AABB area = target.getBoundingBox().inflate(CRYSTAL_AOE_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (nearby != target) {
                nearby.hurtServer((ServerLevel) nearby.level(), nearby.damageSources().magic(), CRYSTAL_SPLASH_DAMAGE);
            }
        }
    }

    /** Sends damage indicator particles at the target's position.
     *
     * @param level  the current level
     * @param target the target entity
     */
    private static void sendDamageParticles(Level level, LivingEntity target) {
        if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + CRYSTAL_PARTICLE_Y_OFFSET, target.getZ(), CRYSTAL_PARTICLE_COUNT, CRYSTAL_PARTICLE_H_SPREAD, CRYSTAL_PARTICLE_V_SPREAD, CRYSTAL_PARTICLE_H_SPREAD, 0.0);
        }
    }

    /**
     * Applies slowness and poison to simulate vine entanglement.
     *
     * @param target the entity to entangle
     */
    private static void leafEntangle(LivingEntity target) {
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, LEAF_SLOW_DURATION, LEAF_SLOW_AMPLIFIER));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, LEAF_POISON_DURATION, 0));
    }

    /**
     * Attempts to clone a mob with probability inversely scaled by max health.
     *
     * @param level  the current level
     * @param target the entity to clone
     */
    private static void vitalClone(Level level, LivingEntity target) {
        if (!(target instanceof Mob mob)) { return; }
        if (!shouldClone(level, target)) { return; }
        spawnClone(level, mob);
    }

    /** Rolls a health-scaled probability check for vital cloning.
     *
     * @param level  the current level
     * @param target the potential clone source
     * @return true if cloning should proceed
     */
    private static boolean shouldClone(Level level, LivingEntity target) {
        double chance = VITAL_CLONE_BASE_CHANCE / Math.pow(target.getMaxHealth(), VITAL_CLONE_HEALTH_EXPONENT);
        return level.getRandom().nextFloat() * VITAL_CLONE_PERCENT_SCALE < chance;
    }

    /** Spawns a copy of the mob near its current position.
     *
     * @param level the current level
     * @param mob   the mob to clone
     */
    private static void spawnClone(Level level, Mob mob) {
        Entity clone = mob.getType().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (clone != null) {
            clone.setPos(mob.getX() + level.getRandom().nextGaussian(), mob.getY(), mob.getZ() + level.getRandom().nextGaussian());
            level.addFreshEntity(clone);
        }
    }

    /**
     * Applies slowness, weakness, and poison. Bosses (wither/dragon) are immune.
     *
     * @param target the entity to debuff
     */
    private static void shroomDebuff(LivingEntity target) {
        // Bosses are immune (check for wither/dragon by entity type)
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) { return; }
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, SHROOM_DEBUFF_DURATION, 1));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, SHROOM_DEBUFF_DURATION, 1));
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, SHROOM_DEBUFF_DURATION, 0));
    }

    /**
     * Applies max slowness; if already petrified, kills the target and drops cobblestone.
     *
     * @param level  the current level
     * @param target the entity to petrify
     */
    private static void rockPetrify(Level level, LivingEntity target) {
        // Apply heavy slowness (simulates petrification)
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ROCK_PETRIFY_DURATION, ROCK_PETRIFY_AMPLIFIER));
        // If already "petrified" (max slowness), crush
        MobEffectInstance existing = target.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
        if (existing != null && existing.getAmplifier() >= ROCK_PETRIFY_AMPLIFIER && level instanceof ServerLevel sl) {
            // Crush: kill and drop cobblestone
            target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), Float.MAX_VALUE);
            target.spawnAtLocation(sl, new ItemStack(Items.COBBLESTONE, 1 + level.getRandom().nextInt(ROCK_CRUSH_DROP_BOUND)));
        }
    }

    /**
     * Sets the target and nearby non-fireproof entities on fire.
     *
     * @param level  the current level
     * @param target the primary target entity
     */
    private static void blazeIgnite(Level level, LivingEntity target) {
        target.igniteForSeconds(BLAZE_IGNITE_SECONDS);
        // Ignite nearby entities too
        AABB area = target.getBoundingBox().inflate(BLAZE_AOE_RADIUS);
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (!nearby.fireImmune()) {
                nearby.igniteForSeconds(BLAZE_NEARBY_IGNITE_SECONDS);
            }
        }
    }

    /**
     * Deals freeze damage, adds freeze buildup ticks, and applies heavy slowness.
     *
     * @param target the entity to freeze
     */
    private static void frostSnap(LivingEntity target) {
        if (target.getType() == EntityType.WITHER || target.getType() == EntityType.ENDER_DRAGON) { return; }
        target.hurtServer((ServerLevel) target.level(), target.damageSources().freeze(), FROST_SNAP_DAMAGE);
        target.setTicksFrozen(target.getTicksFrozen() + FROST_FREEZE_BUILDUP); // add freeze buildup
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, FROST_SLOW_DURATION, FROST_SLOW_AMPLIFIER));
    }

    /**
     * Applies levitation to launch the target skyward.
     *
     * @param target the entity to levitate
     */
    private static void typhoonLevitate(LivingEntity target) {
        target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, TYPHOON_LEVITATE_DURATION, 1));
    }

    /**
     * Deals heavy damage to undead and applies glowing to survivors.
     *
     * @param target the entity to smite
     */
    private static void glowSmite(LivingEntity target) {
        if (target.isInvertedHealAndHarm()) {
            // Undead  - solar damage
            target.hurtServer((ServerLevel) target.level(), target.damageSources().magic(), GLOW_SMITE_DAMAGE);
        }
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, GLOW_EFFECT_DURATION, 0));
        }
    }

    /**
     * Applies weakness and glowing with duration inversely scaled by current health.
     *
     * @param target  the entity to charm
     * @param thrower the entity that threw the blob, or null
     */
    private static void hexCharm(LivingEntity target, @Nullable Entity thrower) {
        if (!(target instanceof Mob mob)) { return; }
        float health = target.getHealth();
        double duration = HEX_CHARM_BASE_DURATION / Math.pow(health, HEX_CHARM_HEALTH_EXPONENT);
        int ticks = (int)(duration * HEX_TICKS_PER_SECOND);
        // Simple charm: give weakness (reduces hostility) and glowing
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, ticks, HEX_WEAKNESS_AMPLIFIER));
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0));
        // TODO: Full charm AI override (make mob passive to thrower)
    }

    /**
     * Disables AI and applies max slowness to simulate a stun.
     *
     * @param target the entity to stun
     */
    private static void pulseShortCircuit(LivingEntity target) {
        // Simulate with stun: no AI + slowness
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            // Schedule AI re-enable (5 seconds)
            mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, PULSE_STUN_DURATION, PULSE_STUN_AMPLIFIER));
        }
    }

    /**
     * Halves the target's current health and applies wither. Withers are immune.
     *
     * @param target the entity to wither
     */
    private static void netherWither(LivingEntity target) {
        // Halve current health (if not wither-immune)
        if (target.getType() != EntityType.WITHER) {
            float halfHealth = target.getHealth() / NETHER_HEALTH_DIVISOR;
            target.setHealth(halfHealth);
            target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, NETHER_WITHER_DURATION, 1));
        }
    }

    /**
     * Teleports the target to a random location within a 32-block range.
     *
     * @param level  the current level
     * @param target the entity to teleport
     */
    private static void enderTeleport(Level level, LivingEntity target) {
        Vec3 pos = target.position();
        double offsetX = (level.getRandom().nextDouble() - ENDER_TELEPORT_CENTER) * ENDER_TELEPORT_RANGE;
        double offsetZ = (level.getRandom().nextDouble() - ENDER_TELEPORT_CENTER) * ENDER_TELEPORT_RANGE;
        target.teleportTo(pos.x + offsetX, pos.y, pos.z + offsetZ);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, ENDER_SOUND_VOLUME, ENDER_SOUND_PITCH);
    }

    /**
     * Freezes a mob in place: disables AI, makes invulnerable, and applies glowing.
     *
     * @param target the entity to freeze in time
     */
    private static void aeonTimeStop(LivingEntity target) {
        if (!(target instanceof Mob mob)) { return; }
        // Apply stasis
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, AEON_GLOW_DURATION, 0));
        // TODO: Accumulate ritual progress across multiple applications
        // TODO: At 100% → convert to egg item
    }
}
