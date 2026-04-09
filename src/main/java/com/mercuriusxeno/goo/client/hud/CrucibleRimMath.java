package com.mercuriusxeno.goo.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Selects the best basin rim anchor for the crucible HUD panel by scoring
 * 8 candidate points (4 corners + 4 side midpoints) based on camera position
 * and look direction.
 */
final class CrucibleRimMath {
    /** Basin top Y in block-local coords (top of the basin walls). */
    private static final double BASIN_TOP_Y = 1.0;

    /** How far above the basin top to lift the panel anchor (in blocks). */
    private static final double HUD_LIFT = 0.05;

    /**
     * Cosine threshold for side-preference bias. When the camera's forward
     * vector aligns with a cardinal axis within this angle (~25 degrees),
     * side midpoints get a score bonus over corners. cos(25) ~ 0.906.
     */
    private static final float SIDE_BIAS_COS_THRESHOLD = 0.906f;

    /** Score bonus given to side midpoints when look vector is near-cardinal. */
    private static final float SIDE_BIAS_BONUS = 0.5f;

    /** Below-rim sign for selecting nearest rim point. */
    private static final float BELOW_RIM_SIGN = -1f;

    /**
     * 8 rim anchor points on the basin top, in block-local XZ coords.
     * 4 corners + 4 side midpoints. The isSide flag distinguishes midpoints
     * from corners for bias scoring.
     */
    private static final RimPoint[] RIM_POINTS = {
        new RimPoint(1f / 16f,  1f / 16f,  false),  // NW corner
        new RimPoint(15f / 16f, 1f / 16f,  false),  // NE corner
        new RimPoint(1f / 16f,  15f / 16f, false),   // SW corner
        new RimPoint(15f / 16f, 15f / 16f, false),   // SE corner
        new RimPoint(8f / 16f,  1f / 16f,  true),    // N midpoint
        new RimPoint(8f / 16f,  15f / 16f, true),    // S midpoint
        new RimPoint(1f / 16f,  8f / 16f,  true),    // W midpoint
        new RimPoint(15f / 16f, 8f / 16f,  true),    // E midpoint
    };

    private CrucibleRimMath() {}

    /**
     * Translates the pose stack to the farthest rim point, lifted above the basin,
     * relative to camera position. The point is selected from 8 candidates
     * (4 corners + 4 side midpoints) with look-alignment bias.
     *
     * @param poseStack the pose stack for rendering
     * @param pos the block position
     * @param cam the camera world position
     * @param camera the render camera
     */
    static void translateToRimPoint(PoseStack poseStack, BlockPos pos,
            Vec3 cam, Camera camera) {
        RimPoint rp = selectBestRimPoint(pos, camera);
        double rx = pos.getX() + rp.x() - cam.x;
        double ry = pos.getY() + BASIN_TOP_Y + HUD_LIFT - cam.y;
        double rz = pos.getZ() + rp.z() - cam.z;
        poseStack.translate(rx, ry, rz);
    }

    /**
     * Selects the best rim point for the HUD panel. When the camera is above
     * the basin rim, picks the farthest point (behind the basin). When below,
     * picks the nearest point (in front) so the panel isn't hidden by the walls.
     * Side midpoints get a bias when the look vector is near-cardinal.
     *
     * @param pos the block position
     * @param camera the render camera
     * @return the selected result
     */
    private static RimPoint selectBestRimPoint(BlockPos pos, Camera camera) {
        float yawRad = (float) Math.toRadians(camera.yRot());
        float forwardX = (float) -Math.sin(yawRad);
        float forwardZ = (float) Math.cos(yawRad);
        Vec3 cam = camera.position();
        boolean nearCardinal = isNearCardinal(forwardX, forwardZ);
        float sign = cam.y < pos.getY() + BASIN_TOP_Y ? BELOW_RIM_SIGN : 1f;
        return pickHighestScoringPoint(pos, cam, forwardX, forwardZ, nearCardinal, sign);
    }

    /**
     * Iterates all rim point candidates and returns the one with the highest score.
     *
     * @param pos the block position
     * @param cam the camera position
     * @param forwardX camera forward X component
     * @param forwardZ camera forward Z component
     * @param nearCardinal whether the camera is near a cardinal direction
     * @param sign +1 for farthest-wins (above rim), -1 for nearest-wins (below rim)
     * @return the highest-scoring rim point
     */
    private static RimPoint pickHighestScoringPoint(BlockPos pos, Vec3 cam,
            float forwardX, float forwardZ, boolean nearCardinal, float sign) {
        RimPoint best = RIM_POINTS[0];
        float bestScore = Float.NEGATIVE_INFINITY;
        for (RimPoint rp : RIM_POINTS) {
            float score = scoreRimPoint(rp, pos, cam, forwardX, forwardZ, nearCardinal, sign);
            best = score > bestScore ? rp : best;
            bestScore = Math.max(score, bestScore);
        }
        return best;
    }

    /**
     * Returns true if the camera's forward XZ vector is within the bias threshold
     * of a cardinal axis (N/S/E/W), meaning the player is looking roughly straight
     * along one axis.
     *
     * @param forwardX the forwardX
     * @param forwardZ the forwardZ
     * @return true if nearCardinal
     */
    private static boolean isNearCardinal(float forwardX, float forwardZ) {
        float absX = Math.abs(forwardX);
        float absZ = Math.abs(forwardZ);
        float dominant = Math.max(absX, absZ);
        return dominant >= SIDE_BIAS_COS_THRESHOLD;
    }

    /**
     * Scores a rim point candidate. Base score is the dot product of the
     * camera-to-point vector with the camera forward direction, multiplied by
     * sign (+1 = farthest wins, -1 = nearest wins). Side midpoints receive a
     * bias bonus in the same direction.
     *
     * @param rp the rim point
     * @param pos the block position
     * @param cam the camera position
     * @param forwardX the forwardX
     * @param forwardZ the forwardZ
     * @param nearCardinal the nearCardinal
     * @param sign the sign
     * @return the computed score
     */
    private static float scoreRimPoint(RimPoint rp, BlockPos pos, Vec3 cam,
            float forwardX, float forwardZ, boolean nearCardinal, float sign) {
        float dx = pos.getX() + rp.x() - (float) cam.x;
        float dz = pos.getZ() + rp.z() - (float) cam.z;
        float score = (dx * forwardX + dz * forwardZ) * sign;
        if (nearCardinal && rp.isSide()) {
            score += SIDE_BIAS_BONUS;
        }
        return score;
    }

    /**
     * A candidate anchor point on the basin rim.
     * @param x block-local X coordinate
     * @param z block-local Z coordinate
     * @param isSide true for side midpoints, false for corners
     */
    record RimPoint(float x, float z, boolean isSide) {
    }
}
