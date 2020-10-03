package com.xeno.goo.library;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;
import java.util.function.Supplier;

public class AudioHelper
{
    public static Random rand = new Random();

    public static class PitchFormulas {
        public static Supplier<Float> HalfToOne = () -> rand.nextFloat() * 0.5f + 0.5f;
        public static Supplier<Float> HalfToOneAndHalf = () -> rand.nextFloat() + 1f;
        public static Supplier<Float> FlatOne = () -> 1f;
    }

    // "default" player sound method, meant to shorten things up a bit.
    public static void playerAudioEvent(PlayerEntity player, SoundEvent soundEvent, float v)
    {
        entityAudioEvent(player, soundEvent, SoundCategory.PLAYERS, v, PitchFormulas.HalfToOne);
    }

    public static void entityAudioEvent(Entity e, SoundEvent sound, SoundCategory category, float volume, Supplier<Float> pitchFormula) {
        headlessAudioEvent(e.getEntityWorld(), e.getPosition(), sound, category, volume, pitchFormula);
    }

    public static void tileAudioEvent(World world, BlockPos pos, SoundEvent sound, SoundCategory blocks, float v, Supplier<Float> pitchFormula)
    {
        headlessAudioEvent(world, pos, sound, blocks, v, pitchFormula);
    }

    public static void headlessAudioEvent(World world, BlockPos pos, SoundEvent sound, SoundCategory category, float volume,
            Supplier<Float> pitchFormula) {
        world.playSound(null, pos, sound, category, volume, pitchFormula.get());
    }

}
