package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and caches anti-aliased mask textures for the radial menu.
 * One white-on-transparent DynamicTexture per wedge plus one for the
 * cancel circle. Edges are smoothed via 4x4 sub-pixel multi-sampling,
 * eliminating the aliased scanline artifacts of the old rasterizer.
 */
public final class RadialTextures {
    /**
     * Texture resolution - matches OUTER_RADIUS * 2 so blit UV maps 1:1.
     */
    static final int TEX_SIZE = 200;

    /**
     * Number of wedges (one per goo type).
     */
    private static final int WEDGE_COUNT = GooType.values().length;

    /**
     * Sub-samples per axis for anti-aliasing (4x4 = 16 samples per pixel).
     */
    private static final int AA_SAMPLES = 4;

    /**
     * Inner/outer radius in normalized [0..1] space (center = 0.5).
     */
    private static final double NORM_INNER = 30.0 / 100.0; // INNER_RADIUS / OUTER_RADIUS
    private static final double NORM_OUTER = 1.0;

    /**
     * Full circle in radians.
     */
    private static final double TWO_PI = 2.0 * Math.PI;

    /**
     * Divisor for computing center of texture.
     */
    private static final double HALF_DIVISOR = 2.0;

    /**
     * Sub-sample center offset.
     */
    private static final double SAMPLE_CENTER = 0.5;

    /**
     * Maximum alpha value for fully opaque white.
     */
    private static final int MAX_ALPHA = 255;

    /**
     * Cancel radius gap from inner ring (in pixels).
     */
    private static final double CANCEL_GAP = 2.0;

    /**
     * Inner radius in pixels for cancel mask computation.
     */
    private static final double CANCEL_INNER_PX = 30.0;

    /**
     * Outer radius in pixels for cancel mask computation.
     */
    private static final double CANCEL_OUTER_PX = 100.0;

    /**
     * Texture label prefix for wedge textures.
     */
    private static final String WEDGE_LABEL_PREFIX = "goo_radial_wedge_";

    /**
     * Resource path prefix for wedge textures.
     */
    private static final String WEDGE_PATH_PREFIX = "dynamic/radial_wedge_";

    /**
     * Texture label for the cancel mask.
     */
    private static final String CANCEL_LABEL = "goo_radial_cancel";

    /**
     * Resource path for the cancel mask.
     */
    private static final String CANCEL_PATH = "dynamic/radial_cancel";

    /**
     * Registered texture identifiers, one per wedge.
     */
    private static final Identifier[] WEDGE_IDS = new Identifier[WEDGE_COUNT];
    /**
     * Cache of dynamically generated wedge masks for variable-count radials.
     * Key: (wedgeCount << 16) | wedgeIndex.
     */
    private static final Map<Integer, Identifier> DYNAMIC_WEDGES = new HashMap<>();
    /**
     * Dynamic texture label prefix for variable-count wedges.
     */
    private static final String DYN_WEDGE_LABEL = "goo_dyn_wedge_";
    /**
     * Dynamic texture path prefix for variable-count wedges.
     */
    private static final String DYN_WEDGE_PATH = "dynamic/dyn_wedge_";
    /**
     * Separator between count and index in dynamic wedge names.
     */
    private static final String DYN_SEP = "_";
    /**
     * Bit shift for packing wedge count into cache key.
     */
    private static final int COUNT_SHIFT = 16;
    /**
     * Registered cancel circle texture identifier.
     */
    private static Identifier cancelId;
    /**
     * Whether textures have been generated and registered.
     */
    private static boolean initialized;

    private RadialTextures() {
    }

    /**
     * Lazily generates and registers all mask textures on first use.
     */
    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        var texManager = Minecraft.getInstance().getTextureManager();
        registerWedgeTextures(texManager);
        registerCancelTexture(texManager);
    }

    /**
     * Generates and registers one AA mask texture per wedge.
     *
     * @param texManager the texture manager for registration
     */
    private static void registerWedgeTextures(TextureManager texManager) {
        for (int i = 0; i < WEDGE_COUNT; i++) {
            NativeImage image = generateWedgeMask(i);
            int idx = i;
            DynamicTexture tex = new DynamicTexture(() -> WEDGE_LABEL_PREFIX + idx, image);
            Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, WEDGE_PATH_PREFIX + i);
            texManager.register(id, tex);
            WEDGE_IDS[i] = id;
        }
    }

    /**
     * Generates and registers the cancel circle mask texture.
     *
     * @param texManager the texture manager for registration
     */
    private static void registerCancelTexture(TextureManager texManager) {
        NativeImage cancelImage = generateCancelMask();
        DynamicTexture cancelTex = new DynamicTexture(() -> CANCEL_LABEL, cancelImage);
        cancelId = Identifier.fromNamespaceAndPath(Goo.MODID, CANCEL_PATH);
        texManager.register(cancelId, cancelTex);
    }

    /**
     * Returns the texture identifier for the given wedge index.
     *
     * @param wedgeIndex the zero-based wedge index
     * @return the registered texture identifier for this wedge
     */
    public static Identifier getWedgeTexture(int wedgeIndex) {
        return WEDGE_IDS[wedgeIndex];
    }

    /**
     * Returns the texture identifier for the cancel circle.
     *
     * @return the registered texture identifier for the cancel mask
     */
    public static Identifier getCancelTexture() {
        return cancelId;
    }

    /**
     * Returns a wedge mask texture for a variable-count radial (e.g., ability radial).
     * Generates and caches on first use per (wedgeCount, wedgeIndex) pair.
     *
     * @param wedgeCount the total number of wedges
     * @param wedgeIndex the index of this wedge
     * @return the registered texture identifier
     */
    public static Identifier getWedgeTexture(int wedgeCount, int wedgeIndex) {
        ensureInitialized();
        int key = (wedgeCount << COUNT_SHIFT) | wedgeIndex;
        return DYNAMIC_WEDGES.computeIfAbsent(key, k -> registerDynamicWedge(wedgeCount, wedgeIndex));
    }

    private static Identifier registerDynamicWedge(int wedgeCount, int wedgeIndex) {
        double startAngle = wedgeIndex * (TWO_PI / wedgeCount);
        double endAngle = (wedgeIndex + 1) * (TWO_PI / wedgeCount);
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        rasterizeWedge(image, TEX_SIZE / HALF_DIVISOR, startAngle, endAngle);
        int count = wedgeCount;
        int idx = wedgeIndex;
        DynamicTexture tex = new DynamicTexture(() -> DYN_WEDGE_LABEL + count + DYN_SEP + idx, image);
        Identifier id = Identifier.fromNamespaceAndPath(
                Goo.MODID, DYN_WEDGE_PATH + count + DYN_SEP + wedgeIndex);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return id;
    }

    /**
     * Generates a white-on-transparent mask for a single wedge of the annular ring.
     * Uses 4x4 multi-sampling at each pixel for anti-aliased edges.
     *
     * @param wedgeIndex the zero-based wedge index to generate
     * @return the generated native image mask for this wedge
     */
    private static NativeImage generateWedgeMask(int wedgeIndex) {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double half = TEX_SIZE / HALF_DIVISOR;
        double startAngle = wedgeIndex * (TWO_PI / WEDGE_COUNT);
        double endAngle = (wedgeIndex + 1) * (TWO_PI / WEDGE_COUNT);
        rasterizeWedge(image, half, startAngle, endAngle);
        return image;
    }

    /**
     * Fills pixel samples for one wedge arc into the image.
     *
     * @param image      the target image
     * @param half       half the texture size for normalization
     * @param startAngle wedge start angle in radians
     * @param endAngle   wedge end angle in radians
     */
    private static void rasterizeWedge(NativeImage image, double half, double startAngle, double endAngle) {
        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                int hits = countWedgeHits(px, py, half, startAngle, endAngle);
                writePixelIfHit(image, px, py, hits);
            }
        }
    }

    /**
     * Counts how many sub-samples at (px, py) fall inside the wedge arc.
     *
     * @param px         pixel x coordinate
     * @param py         pixel y coordinate
     * @param half       half the texture size, used to normalize coordinates
     * @param startAngle wedge start angle in radians
     * @param endAngle   wedge end angle in radians
     * @return number of sub-samples inside the wedge (0..AA_SAMPLES^2)
     */
    private static int countWedgeHits(int px, int py, double half, double startAngle, double endAngle) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                boolean inside = isInsideWedge(toNormalized(px, sx, half), toNormalized(py, sy, half), startAngle, endAngle);
                if (inside) {
                    hits++;
                }
            }
        }
        return hits;
    }

    /**
     * Tests whether a normalized coordinate lies inside the annular wedge.
     *
     * @param x          normalized x (-1..1, center = 0)
     * @param y          normalized y (-1..1, center = 0)
     * @param startAngle wedge start angle in radians
     * @param endAngle   wedge end angle in radians
     * @return true if the point is within the ring and within the angular bounds
     */
    private static boolean isInsideWedge(double x, double y,
                                         double startAngle, double endAngle) {
        double dist = Math.sqrt(x * x + y * y);
        if (dist < NORM_INNER || dist > NORM_OUTER) {
            return false;
        }

        // Angle from top, clockwise - matches GooRadialRenderer.computeHoveredIndex
        double angle = Math.atan2(x, -y);
        if (angle < 0) {
            angle += TWO_PI;
        }
        return angle >= startAngle && angle < endAngle;
    }

    /**
     * Generates a white-on-transparent filled circle mask for the cancel zone.
     * Radius is slightly smaller than the inner ring to leave a gap.
     *
     * @return the generated native image mask for the cancel circle
     */
    private static NativeImage generateCancelMask() {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double half = TEX_SIZE / HALF_DIVISOR;
        double cancelNorm = (CANCEL_INNER_PX - CANCEL_GAP) / CANCEL_OUTER_PX;
        rasterizeCircle(image, half, cancelNorm);
        return image;
    }

    /**
     * Fills pixel samples for a filled circle into the image.
     *
     * @param image     the target image
     * @param half      half the texture size for normalization
     * @param maxRadius normalized radius threshold
     */
    private static void rasterizeCircle(NativeImage image, double half, double maxRadius) {
        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                int hits = countCircleHits(px, py, half, maxRadius);
                writePixelIfHit(image, px, py, hits);
            }
        }
    }

    /**
     * Counts how many sub-samples at (px, py) fall inside the cancel circle.
     *
     * @param px        pixel x coordinate
     * @param py        pixel y coordinate
     * @param half      half the texture size, used to normalize coordinates
     * @param maxRadius normalized radius threshold
     * @return number of sub-samples inside the circle (0..AA_SAMPLES^2)
     */
    private static int countCircleHits(int px, int py, double half, double maxRadius) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                if (isInsideCircle(toNormalized(px, sx, half), toNormalized(py, sy, half), maxRadius)) {
                    hits++;
                }
            }
        }
        return hits;
    }

    /**
     * Tests whether a normalized coordinate lies inside the cancel circle.
     *
     * @param x         normalized x
     * @param y         normalized y
     * @param maxRadius normalized radius threshold
     * @return true if the point is within the circle
     */
    private static boolean isInsideCircle(double x, double y, double maxRadius) {
        return Math.sqrt(x * x + y * y) <= maxRadius;
    }

    /**
     * Converts a pixel coordinate and sub-sample index to normalized [-1..1] space.
     *
     * @param pixel  the pixel coordinate
     * @param sample the sub-sample index within that pixel
     * @param half   half the texture size
     * @return the normalized coordinate centered at 0
     */
    private static double toNormalized(int pixel, int sample, double half) {
        return (pixel + (sample + SAMPLE_CENTER) / AA_SAMPLES - half) / half;
    }

    /**
     * Writes a white pixel with proportional alpha if any sub-samples hit.
     *
     * @param image the target image
     * @param px    pixel x coordinate
     * @param py    pixel y coordinate
     * @param hits  number of sub-sample hits (0 means no write)
     */
    private static void writePixelIfHit(NativeImage image, int px, int py, int hits) {
        if (hits > 0) {
            int alpha = hits * MAX_ALPHA / (AA_SAMPLES * AA_SAMPLES);
            image.setPixel(px, py, ARGB.color(alpha, MAX_ALPHA, MAX_ALPHA, MAX_ALPHA));
        }
    }
}
