package com.mercuriusxeno.goo.effect;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Teleports the target to a random location within a 32-block range. */
public final class EnderMobEffect {

    private static final double ENDER_TELEPORT_RANGE = 32.0;
    private static final double ENDER_TELEPORT_CENTER = 0.5;
    private static final float ENDER_SOUND_VOLUME = 1.0f;
    private static final float ENDER_SOUND_PITCH = 1.0f;

    private EnderMobEffect() {}

    /**
     * Teleports the target to a random location within a 32-block range.
     *
     * @param level  the current level
     * @param target the entity to teleport
     */
    public static void apply(Level level, LivingEntity target) {
        Vec3 pos = target.position();
        double offsetX = (level.getRandom().nextDouble() - ENDER_TELEPORT_CENTER) * ENDER_TELEPORT_RANGE;
        double offsetZ = (level.getRandom().nextDouble() - ENDER_TELEPORT_CENTER) * ENDER_TELEPORT_RANGE;
        target.teleportTo(pos.x + offsetX, pos.y, pos.z + offsetZ);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, ENDER_SOUND_VOLUME, ENDER_SOUND_PITCH);
    }
}
