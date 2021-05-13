package com.xeno.goo.interactions;

import com.xeno.goo.fluids.GooFluid;
import com.xeno.goo.library.AudioHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.function.Supplier;

public class Faunal
{
    private static final Supplier<GooFluid> fluidSupplier = Registry.FAUNAL_GOO;

    public static void registerInteractions()
    {
        GooInteractions.registerSplat(fluidSupplier.get(), "breed_animals", Faunal::makeAnimalsBreed, Faunal::isBreedingAnimalInRange);

        GooInteractions.registerBlobHit(fluidSupplier.get(), "faunal_hit", Faunal::hitEntity);
    }

    private static boolean hitEntity(BlobHitContext c) {
        if (c.victim() instanceof AnimalEntity && breedingAnimalPredicate((AnimalEntity)c.victim())) {
            doEffects((AnimalEntity)c.victim());
            return true;
        }
        return false;
    }

    private static boolean isBreedingAnimalInRange(SplatContext splatContext) {
        return splatContext.world().getEntitiesWithinAABB(AnimalEntity.class, splatContext.splat().getBoundingBox().grow(2d, 1d, 2d),
                Faunal::breedingAnimalPredicate).size() > 1;
    }

    private static boolean breedingAnimalPredicate(AnimalEntity e) {
        return !e.isInLove() && e.getGrowingAge() == 0;
    }

    private static boolean makeAnimalsBreed(SplatContext splatContext) {

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
