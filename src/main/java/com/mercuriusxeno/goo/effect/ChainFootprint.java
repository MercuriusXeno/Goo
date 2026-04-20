package com.mercuriusxeno.goo.effect;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared footprint math for rock and blaze chain effects. Both use
 * the same scaling: 1x1 at 1 stack, cross at 2, 3x3 at 3, then
 * 3x3 with increasing depth beyond 3. Flat mode redistributes the
 * same total block count into a taxicab-distance circle on one layer.
 */
public final class ChainFootprint {

    /** Maximum tunnel depth, reached at 27 stacks. */
    public static final int MAX_DEPTH = 25;

    /** Maximum meaningful stack count (3 + MAX_DEPTH). */
    public static final int MAX_STACKS = 3 + MAX_DEPTH;

    /** Stack count where footprint widens to a cross. */
    private static final int CROSS_THRESHOLD = 2;
    /** Stack count where footprint fills to 3x3. */
    private static final int FULL_THRESHOLD = 3;
    /** Block count for a cross footprint (center + 4 cardinal). */
    private static final int CROSS_BLOCKS = 5;
    /** Block count for a 3x3 footprint. */
    private static final int FULL_BLOCKS = 9;
    /** Depth offset: stacks minus this = tunnel depth for stacks > 3. */
    private static final int DEPTH_OFFSET = 2;
    /** Half-width of the 3x3 grid. */
    private static final int GRID_HALF = 1;
    /** Negative unit offset for cardinal directions. */
    private static final int NEG = -1;
    /** AABB expansion: blocks occupy full unit cubes. */
    private static final int BLOCK_SIZE = 1;
    /** Array index for Z component in offset triples. */
    private static final int Z_INDEX = 2;

    private ChainFootprint() {}


    /**
     * Total blocks affected at the given stack count.
     *
     * @param stacks blob stack count (1-based)
     * @return total block count
     */
    public static int totalBlocks(int stacks) {
        return switch (stacks) {
            case 1 -> 1;
            case CROSS_THRESHOLD -> CROSS_BLOCKS;
            default -> FULL_BLOCKS * tunnelDepth(stacks);
        };
    }

    /**
     * Tunnel depth (layers into the wall) at the given stack count.
     * 1-3 stacks produce depth 1; each stack beyond 3 adds one layer,
     * capped at {@link #MAX_DEPTH}.
     *
     * @param stacks blob stack count (1-based)
     * @return depth in layers
     */
    public static int tunnelDepth(int stacks) {
        if (stacks <= FULL_THRESHOLD) { return 1; }
        return Math.min(stacks - DEPTH_OFFSET, MAX_DEPTH);
    }


    /**
     * Returns the 2D offsets for one tunnel-mode layer at the given
     * stack count. Coordinates are (perpA, perpB) relative to the
     * layer center.
     *
     * @param stacks blob stack count (1-based)
     * @return list of [a, b] offset pairs
     */
    public static List<int[]> layerFootprint(int stacks) {
        return switch (stacks) {
            case 1 -> singleBlock();
            case CROSS_THRESHOLD -> crossShape();
            default -> threeByThree();
        };
    }

    /**
     * Returns the 2D offsets for flat mode at the given stack count.
     * For 1-3 stacks this is identical to {@link #layerFootprint}.
     * For 4+, distributes {@link #totalBlocks} into taxicab-distance
     * rings for a quasi-circular flat pattern.
     *
     * @param stacks blob stack count (1-based)
     * @return list of [a, b] offset pairs
     */
    public static List<int[]> flatFootprint(int stacks) {
        if (stacks <= FULL_THRESHOLD) { return layerFootprint(stacks); }
        return euclideanCircle(totalBlocks(stacks));
    }


    /**
     * Decomposes the flat footprint into concentric distance rings.
     * Each ring contains positions at the same squared distance from center.
     * Ring 0 is the origin, ring 1 is the first cardinal neighbors, etc.
     * The union of all rings equals {@link #flatFootprint(int)}.
     *
     * @param stacks blob stack count (1-based)
     * @return list of rings, each ring a list of [a, b] offset pairs
     */
    public static List<List<int[]>> flatRings(int stacks) {
        if (stacks <= FULL_THRESHOLD) { return List.of(layerFootprint(stacks)); }
        int budget = totalBlocks(stacks);
        int searchRadius = (int) Math.ceil(Math.sqrt(budget)) + 1;
        List<int[]> candidates = collectCandidates(searchRadius);
        candidates.sort((p, q) -> Integer.compare(sqDist(p), sqDist(q)));
        return splitIntoTiers(candidates, budget);
    }

    /** Splits sorted candidates into distance tiers, stopping at budget.
     *
     * @param sorted   positions sorted by squared distance
     * @param budget   maximum total block count
     * @return the tier-decomposed ring list
     */
    private static List<List<int[]>> splitIntoTiers(List<int[]> sorted, int budget) {
        List<List<int[]>> rings = new ArrayList<>();
        int total = 0;
        int i = 0;
        while (i < sorted.size()) {
            int tierEnd = findTierEnd(sorted, i);
            int tierSize = tierEnd - i;
            if (total + tierSize > budget) { break; }
            List<int[]> ring = new ArrayList<>(tierSize);
            addRange(ring, sorted, i, tierEnd);
            rings.add(ring);
            total += tierSize;
            i = tierEnd;
        }
        return rings;
    }

    /**
     * Builds the roundest possible flat region by filling positions
     * sorted by Euclidean distance from center. Positions at equal
     * distance form a tier; tiers are added in full to maintain
     * quarter symmetry. Stops before adding a tier that would exceed
     * the budget, so actual count may be slightly less than target.
     *
     * @param budget maximum number of blocks
     * @return list of [a, b] offset pairs
     */
    static List<int[]> euclideanCircle(int budget) {
        int searchRadius = (int) Math.ceil(Math.sqrt(budget)) + 1;
        List<int[]> candidates = collectCandidates(searchRadius);
        candidates.sort((p, q) -> {
            int da = p[0] * p[0] + p[1] * p[1];
            int db = q[0] * q[0] + q[1] * q[1];
            return Integer.compare(da, db);
        });
        return fillByTier(candidates, budget);
    }

    /**
     * Collects all grid positions within the search radius.
     *
     * @param radius the search radius
     * @return unsorted candidate positions
     */
    private static List<int[]> collectCandidates(int radius) {
        List<int[]> candidates = new ArrayList<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                candidates.add(new int[]{a, b});
            }
        }
        return candidates;
    }

    /**
     * Fills complete Euclidean distance tiers until the next full
     * tier would exceed the budget.
     *
     * @param sorted   positions sorted by squared distance
     * @param budget   maximum block count
     * @return the filled positions
     */
    private static List<int[]> fillByTier(List<int[]> sorted, int budget) {
        List<int[]> result = new ArrayList<>(budget);
        int i = 0;
        while (i < sorted.size()) {
            int tierEnd = findTierEnd(sorted, i);
            int tierSize = tierEnd - i;
            if (result.size() + tierSize > budget) { break; }
            addRange(result, sorted, i, tierEnd);
            i = tierEnd;
        }
        return result;
    }

    /** Returns the exclusive end index of the tier starting at {@code from}.
     *
     * @param sorted the sorted offset list
     * @param from   the start index
     * @return the exclusive end index of the tier
     */
    private static int findTierEnd(List<int[]> sorted, int from) {
        int dist = sqDist(sorted.get(from));
        int i = from;
        while (i < sorted.size() && sqDist(sorted.get(i)) == dist) { i++; }
        return i;
    }

    /** Adds elements from sorted[start..end) to the result list.
     *
     * @param result the destination list
     * @param sorted the source list
     * @param start  the inclusive start index
     * @param end    the exclusive end index
     */
    private static void addRange(List<int[]> result, List<int[]> sorted, int start, int end) {
        for (int j = start; j < end; j++) {
            result.add(sorted.get(j));
        }
    }

    private static int sqDist(int[] pos) {
        return pos[0] * pos[0] + pos[1] * pos[1];
    }


    private static List<int[]> singleBlock() {
        return List.of(new int[]{0, 0});
    }

    private static List<int[]> crossShape() {
        List<int[]> cross = new ArrayList<>(CROSS_BLOCKS);
        cross.add(new int[]{0, 0});
        cross.add(new int[]{1, 0});
        cross.add(new int[]{NEG, 0});
        cross.add(new int[]{0, 1});
        cross.add(new int[]{0, NEG});
        return cross;
    }

    private static List<int[]> threeByThree() {
        List<int[]> grid = new ArrayList<>(FULL_BLOCKS);
        for (int a = -GRID_HALF; a <= GRID_HALF; a++) {
            for (int b = -GRID_HALF; b <= GRID_HALF; b++) {
                grid.add(new int[]{a, b});
            }
        }
        return grid;
    }


    /**
     * Returns all 3D block offsets in the effect region, relative to
     * the marker position. Each offset is {dx, dy, dz} in world axes.
     * Layer 0 starts one step into the wall from the marker.
     *
     * @param stacks   blob stack count
     * @param flatMode true for flat mode
     * @param face     the placed face
     * @return list of {dx, dy, dz} offsets
     */
    public static List<int[]> computeRegionOffsets(int stacks, boolean flatMode, Direction face) {
        List<int[]> footprint = flatMode ? flatFootprint(stacks) : layerFootprint(stacks);
        int depth = flatMode ? 1 : tunnelDepth(stacks);
        Direction blastDir = face.getOpposite();
        return expandLayers(footprint, depth, blastDir);
    }

    /** Expands a 2D footprint into 3D offsets along the blast direction.
     *
     * @param footprint the 2D footprint offsets
     * @param depth     the number of layers
     * @param blastDir  the blast direction
     * @return list of 3D {dx, dy, dz} offsets
     */
    private static List<int[]> expandLayers(List<int[]> footprint, int depth, Direction blastDir) {
        Direction.Axis axis = blastDir.getAxis();
        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG;
        List<int[]> result = new ArrayList<>(footprint.size() * depth);
        for (int layer = 0; layer < depth; layer++) {
            int depthOffset = (layer + 1) * step;
            for (int[] fp : footprint) {
                result.add(mapToWorld(axis, fp[0], fp[1], depthOffset));
            }
        }
        return result;
    }

    /**
     * Maps a (perpA, perpB, depthAlong) triple to world (dx, dy, dz).
     *
     * @param axis the blast axis
     * @param a    first perpendicular offset
     * @param b    second perpendicular offset
     * @param d    depth offset along the blast axis
     * @return {dx, dy, dz} in world axes
     */
    private static int[] mapToWorld(Direction.Axis axis, int a, int b, int d) {
        return switch (axis) {
            case X -> new int[]{d, a, b};
            case Y -> new int[]{a, d, b};
            case Z -> new int[]{a, b, d};
        };
    }

    /**
     * Returns all 3D block offsets in a sphere centered one block into
     * the wall from the marker. Used by frost and other spheroid effects.
     *
     * @param radius the sphere radius in blocks
     * @param face   the placed face (determines center offset direction)
     * @return list of {dx, dy, dz} offsets relative to the marker
     */
    public static List<int[]> computeSphereOffsets(int radius, Direction face) {
        Direction blastDir = face.getOpposite();
        int cx = blastDir.getStepX();
        int cy = blastDir.getStepY();
        int cz = blastDir.getStepZ();
        int r2 = radius * radius;
        List<int[]> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= r2) {
                        result.add(new int[]{dx + cx, dy + cy, dz + cz});
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns 3D offsets for a single spherical shell at the given radius.
     * Shell r contains all integer positions where r-1 < distance <= r,
     * computed as floor(sqrt(d2)) == r. Shell 0 is the origin block.
     * Shells 0..R union to the full solid sphere of radius R.
     *
     * @param shellRadius the shell radius (0 = origin only)
     * @return list of {dx, dy, dz} offsets
     */
    public static List<int[]> sphereShell(int shellRadius) {
        if (shellRadius == 0) { return List.of(new int[]{0, 0, 0}); }
        int r2max = shellRadius * shellRadius;
        int r2min = (shellRadius - 1) * (shellRadius - 1);
        List<int[]> result = new ArrayList<>();
        for (int dx = -shellRadius; dx <= shellRadius; dx++) {
            collectShellSlice(result, dx, shellRadius, r2min, r2max);
        }
        return result;
    }

    /** Collects all positions in one x-slice of a spherical shell.
     *
     * @param result     the output list
     * @param dx         the x offset
     * @param shellRadius the shell radius
     * @param r2min      the squared inner radius (exclusive)
     * @param r2max      the squared outer radius (inclusive)
     */
    private static void collectShellSlice(List<int[]> result, int dx,
            int shellRadius, int r2min, int r2max) {
        for (int dy = -shellRadius; dy <= shellRadius; dy++) {
            for (int dz = -shellRadius; dz <= shellRadius; dz++) {
                int d2 = dx * dx + dy * dy + dz * dz;
                if (d2 <= r2max && d2 > r2min) {
                    result.add(new int[]{dx, dy, dz});
                }
            }
        }
    }

    /**
     * Returns 3D offsets for a single spherical shell, translated so the
     * sphere center is one block into the wall from the marker.
     *
     * @param shellRadius the shell radius (0 = center block)
     * @param face        the placed face (determines center offset)
     * @return list of {dx, dy, dz} offsets relative to the marker
     */
    public static List<int[]> sphereShellOffsets(int shellRadius, Direction face) {
        Direction blastDir = face.getOpposite();
        int cx = blastDir.getStepX();
        int cy = blastDir.getStepY();
        int cz = blastDir.getStepZ();
        List<int[]> shell = sphereShell(shellRadius);
        List<int[]> result = new ArrayList<>(shell.size());
        for (int[] p : shell) {
            result.add(new int[]{p[0] + cx, p[1] + cy, p[Z_INDEX] + cz});
        }
        return result;
    }


    /**
     * Computes the AABB of all affected blocks relative to the marker
     * position. The marker sits in the air block adjacent to the wall;
     * layer 0 is one step into the wall from the marker.
     *
     * @param stacks   blob stack count
     * @param flatMode true for flat mode, false for tunnel
     * @param face     the face the marker was placed on
     * @return AABB in marker-local coordinates (marker at origin)
     */
    public static AABB computeBounds(int stacks, boolean flatMode, Direction face) {
        List<int[]> footprint = flatMode ? flatFootprint(stacks) : layerFootprint(stacks);
        int depth = flatMode ? 1 : tunnelDepth(stacks);
        Direction blastDir = face.getOpposite();

        int minA = Integer.MAX_VALUE;
        int maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE;
        int maxB = Integer.MIN_VALUE;
        for (int[] offset : footprint) {
            minA = Math.min(minA, offset[0]);
            maxA = Math.max(maxA, offset[0]);
            minB = Math.min(minB, offset[1]);
            maxB = Math.max(maxB, offset[1]);
        }

        int step = blastDir.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : NEG;
        return mapToWorldBounds(blastDir.getAxis(), step, minA, maxA, minB, maxB, 1, depth);
    }

    /**
     * Maps 2D footprint bounds and depth range into a world-relative AABB
     * by routing the depth axis and perpendicular axes according to the
     * blast direction.
     *
     * @param axis       the blast axis (X, Y, or Z)
     * @param step       +1 or -1 along the axis
     * @param minA       minimum perpendicular-A offset
     * @param maxA       maximum perpendicular-A offset
     * @param minB       minimum perpendicular-B offset
     * @param maxB       maximum perpendicular-B offset
     * @param depthStart first layer offset along the blast direction
     * @param depthEnd   last layer offset along the blast direction
     * @return the world-relative AABB
     */
    private static AABB mapToWorldBounds(Direction.Axis axis, int step,
                                         int minA, int maxA, int minB, int maxB,
                                         int depthStart, int depthEnd) {
        int dMin = Math.min(depthStart * step, depthEnd * step);
        int dMax = Math.max(depthStart * step, depthEnd * step);
        return switch (axis) {
            case X -> new AABB(dMin, minA, minB,
                    dMax + BLOCK_SIZE, maxA + BLOCK_SIZE, maxB + BLOCK_SIZE);
            case Y -> new AABB(minA, dMin, minB,
                    maxA + BLOCK_SIZE, dMax + BLOCK_SIZE, maxB + BLOCK_SIZE);
            case Z -> new AABB(minA, minB, dMin,
                    maxA + BLOCK_SIZE, maxB + BLOCK_SIZE, dMax + BLOCK_SIZE);
        };
    }
}
