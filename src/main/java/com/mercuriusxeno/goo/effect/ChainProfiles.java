package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.ChainMarkerEffect.ChainProfile;
import net.minecraft.world.level.Level;

/**
 * Registers all chain profiles during mod init. Each goo type with a chain
 * effect gets a profile that defines fuse, max stacks, range formula, and
 * executor. Blaze is the first; Frost, Rock, and Nether are added in
 * subsequent sessions.
 */
public final class ChainProfiles {

    private static final int BLAZE_FUSE_TICKS = 10;
    private static final int BLAZE_MAX_STACKS = 4;

    private ChainProfiles() {}

    /** Called once from {@link com.mercuriusxeno.goo.Goo#commonSetup}. */
    public static void registerAll() {
        ChainProfile.register(GooType.BLAZE, new ChainProfile(
                BLAZE_FUSE_TICKS,
                BLAZE_MAX_STACKS,
                stacks -> (int) EffectMath.computeExplosionRadius(stacks),
                (level, pos, range, stacks) -> level.explode(
                        null,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        (float) range,
                        Level.ExplosionInteraction.TNT)
        ));
    }
}
