package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.client.particle.OrientedBoomParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * Rock visuals: warden-style sonic-boom preview oriented along the
 * blast direction, then a dust-plume on struck. Migrated from the
 * legacy {@code RockBehavior} static helpers so the per-step pipeline
 * can dispatch by name rather than via {@code switch (particleStyle)}.
 */
final class RockDustVisuals implements LayerVisuals {

    static final RockDustVisuals INSTANCE = new RockDustVisuals();

    private static final double BLOCK_CENTER_OFFSET = 0.5;
    private static final int DUST_PARTICLES_PER_BLOCK = 4;
    private static final double DUST_PERP_SPREAD = 0.45;
    private static final double DUST_ALONG_SPREAD = 0.12;
    private static final double DUST_PARTICLE_SPEED = 0.02;

    private RockDustVisuals() {
    }

    @Override
    public void preview(ServerLevel level, BlockPos origin, Direction placedFace,
                        int stepIndex, int stackCount) {
        BlockPos layerCenter = LayerGeometry.layerCenter(origin, placedFace, stepIndex);
        BlockPos particlePos = layerCenter.relative(placedFace);
        double cx = particlePos.getX() + BLOCK_CENTER_OFFSET;
        double cy = particlePos.getY() + BLOCK_CENTER_OFFSET;
        double cz = particlePos.getZ() + BLOCK_CENTER_OFFSET;
        level.sendParticles(new OrientedBoomParticleOptions(placedFace.getOpposite()),
                cx, cy, cz, 1, 0.0, 0.0, 0.0, 0.0);
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
        int count = DUST_PARTICLES_PER_BLOCK * destroyed;
        double spreadX = blastAxis == Direction.Axis.X ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadY = blastAxis == Direction.Axis.Y ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        double spreadZ = blastAxis == Direction.Axis.Z ? DUST_ALONG_SPREAD : DUST_PERP_SPREAD;
        level.sendParticles(ParticleTypes.DUST_PLUME,
                cx, cy, cz, count, spreadX, spreadY, spreadZ, DUST_PARTICLE_SPEED);
    }
}
