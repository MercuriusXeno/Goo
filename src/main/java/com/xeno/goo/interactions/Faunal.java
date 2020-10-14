package com.xeno.goo.interactions;

import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;

import java.util.List;

public class Faunal
{
    public static void registerInteractions()
    {
        GooInteractions.registerSplat(Registry.FAUNAL_GOO.get(), "twitterpate_animals", Faunal::makeAnimalsTwitterpated, Faunal::isBreedingAnimalInRange);
    }

    private static boolean isBreedingAnimalInRange(SplatContext splatContext) {
        return splatContext.world().getEntitiesWithinAABB(AnimalEntity.class, splatContext.splat().getBoundingBox().grow(2d, 1d, 2d),
                (e) -> !e.isInLove() && e.getGrowingAge() == 0).size() > 1;
    }

    private static boolean makeAnimalsTwitterpated(SplatContext splatContext) {

        List<AnimalEntity> nearbyEntities = splatContext.world().getEntitiesWithinAABB(AnimalEntity.class, splatContext.splat().getBoundingBox().grow(2d, 1d, 2d),
                (e) -> !e.isInLove() && e.getGrowingAge() == 0);
        for(AnimalEntity entity : nearbyEntities) {
            if (!splatContext.isRemote()) {
                if (splatContext.splat().owner() instanceof ServerPlayerEntity) {
                    entity.setInLove(((ServerPlayerEntity) splatContext.splat().owner()));
                } else {
                    entity.setInLove(600);
                    splatContext.world().setEntityState(entity, (byte) 18);
                }
            }
            doEffects(entity);
            return true; // stop at the first entity
        }
        return true;
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
