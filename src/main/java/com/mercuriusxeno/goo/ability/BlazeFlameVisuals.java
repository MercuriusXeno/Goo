package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/**
 * Blaze visuals: per-block flame burst preview, then flame + lava +
 * ember on struck. Migrated from the legacy {@code BlazeBehavior}
 * static helpers so the per-step pipeline can dispatch by name rather
 * than via {@code switch (particleStyle)}.
 */
final class BlazeFlameVisuals implements LayerVisuals {

    static final BlazeFlameVisuals INSTANCE = new BlazeFlameVisuals();

    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int PREVIEW_FLAMES_PER_BLOCK = 3;
    private static final double PREVIEW_SPREAD = 0.3;
    private static final double FLAME_PARTICLE_SPEED = 0.05;
    private static final double EMBER_PARTICLE_SPEED = 0.03;
    private static final int FLAME_PARTICLES_PER_BLOCK = 6;
    private static final int LAVA_PARTICLES_PER_BLOCK = 2;
    private static final int EMBER_PARTICLES_PER_BLOCK = 4;
    private static final double FLAME_PERP_SPREAD = 0.45;
    private static final double FLAME_ALONG_SPREAD = 0.12;

    private BlazeFlameVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount) {
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        List<int[]> footprint = ChainFootprint.layerFootprint(stackCount);
        for (int[] offset : footprint) {
            BlockPos cell = LayerGeometry.offsetPerpendicular(layerCenter, blastAxis, offset[0], offset[1]);
            double bx = cell.getX() + BLOCK_CENTER_OFFSET;
            double by = cell.getY() + BLOCK_CENTER_OFFSET;
            double bz = cell.getZ() + BLOCK_CENTER_OFFSET;
            level.sendParticles(ParticleTypes.FLAME,
                    bx, by, bz, PREVIEW_FLAMES_PER_BLOCK,
                    PREVIEW_SPREAD, PREVIEW_SPREAD, PREVIEW_SPREAD, FLAME_PARTICLE_SPEED);
        }
    }

    @Override
    public void onLayerStruck(ServerLevel level, BlockPos origin, Direction placedFace,
                              int stepIndex, int destroyed) {
        if (destroyed <= 0) {
            return;
        }
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        Direction.Axis blastAxis = placedFace.getOpposite().getAxis();
        double cx = layerCenter.getX() + BLOCK_CENTER_OFFSET;
        double cy = layerCenter.getY() + BLOCK_CENTER_OFFSET;
        double cz = layerCenter.getZ() + BLOCK_CENTER_OFFSET;
        double spreadX = blastAxis == Direction.Axis.X ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? FLAME_ALONG_SPREAD : FLAME_PERP_SPREAD;
        level.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, FLAME_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, FLAME_PARTICLE_SPEED);
        level.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, LAVA_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, 0.0);
        level.sendParticles(ParticleTypes.SMALL_FLAME,
                cx, cy, cz, EMBER_PARTICLES_PER_BLOCK * destroyed,
                spreadX, spreadY, spreadZ, EMBER_PARTICLE_SPEED);
    }
}
