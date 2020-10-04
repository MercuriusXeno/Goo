package com.xeno.goo.interactions;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

public class Faunal
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.FAUNAL_GOO.get(), "twitterpate_animals", Faunal::makeAnimalsTwitterpated);
    }

    private static boolean makeAnimalsTwitterpated(SplatContext splatContext) {
        List<AnimalEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(AnimalEntity.class, splatContext.splat().getBoundingBox().grow(1d), null);
        for(AnimalEntity entity : nearbyEntities) {
            if (!entity.isInLove() && entity.getGrowingAge() == 0) {
                entity.setInLove(5);
                doEffects(entity);
                return true;
            }
        }
        return false;
    }

    private static void doEffects(AnimalEntity entity) {
        if (entity.world instanceof ServerWorld) {
            ((ServerWorld)entity.world).spawnParticle(ParticleTypes.HEART, entity.getPosXRandom(1.0D), entity.getPosYRandom() + 0.5D,
                    entity.getPosZRandom(1.0D), 4, 0d, 0d, 0d, 0d);
            AudioHelper.entityAudioEvent(entity, Registry.TWITTERPATE_ANIMAL_SOUND.get(), SoundCategory.AMBIENT, 1.0f,
                    AudioHelper.PitchFormulas.HalfToOne);
        }
    }
}
