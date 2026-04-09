package com.mercuriusxeno.goo.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Vital goo: spawns a small slime as a living blob placeholder. */
final class VitalEffect implements WorldEffect {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Vertical offset for spawning above the target block. */
    private static final double ABOVE_BLOCK_OFFSET = 1.0;

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) { return; }
        Slime slime = new Slime(EntityType.SLIME, level);
        slime.setPos(pos.getX() + BLOCK_CENTER_OFFSET, pos.getY() + ABOVE_BLOCK_OFFSET, pos.getZ() + BLOCK_CENTER_OFFSET);
        level.addFreshEntity(slime);
    }
}
