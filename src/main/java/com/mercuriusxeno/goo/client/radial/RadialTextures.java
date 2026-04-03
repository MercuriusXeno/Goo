package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Generates and caches anti-aliased mask textures for the radial menu.
 * One white-on-transparent DynamicTexture per wedge plus one for the
 * cancel circle. Edges are smoothed via 4x4 sub-pixel multi-sampling,
 * eliminating the aliased scanline artifacts of the old rasterizer.
 */
public final class RadialTextures {

    private RadialTextures() {}

    /** Texture resolution - matches OUTER_RADIUS * 2 so blit UV maps 1:1. */
    static final int TEX_SIZE = 200;

    /** Number of wedges (one per goo type). */
    private static final int WEDGE_COUNT = GooType.values().length;

    /** Sub-samples per axis for anti-aliasing (4x4 = 16 samples per pixel). */
    private static final int AA_SAMPLES = 4;

    /** Inner/outer radius in normalized [0..1] space (center = 0.5). */
    private static final double NORM_INNER = 30.0 / 100.0; // INNER_RADIUS / OUTER_RADIUS
    private static final double NORM_OUTER = 1.0;

    /** Registered texture identifiers, one per wedge. */
    private static final Identifier[] wedgeIds = new Identifier[WEDGE_COUNT];

    /** Registered cancel circle texture identifier. */
    private static Identifier cancelId;

    /** Whether textures have been generated and registered. */
    private static boolean initialized;

    /** Lazily generates and registers all mask textures on first use. */
    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        var texManager = Minecraft.getInstance().getTextureManager();

        for (int i = 0; i < WEDGE_COUNT; i++) {
            NativeImage image = generateWedgeMask(i);
            int idx = i; // effectively final for lambda capture
            DynamicTexture tex = new DynamicTexture(() -> "goo_radial_wedge_" + idx, image);
            Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, "dynamic/radial_wedge_" + i);
            texManager.register(id, tex);
            wedgeIds[i] = id;
        }

        NativeImage cancelImage = generateCancelMask();
        DynamicTexture cancelTex = new DynamicTexture(() -> "goo_radial_cancel", cancelImage);
        cancelId = Identifier.fromNamespaceAndPath(Goo.MODID, "dynamic/radial_cancel");
        texManager.register(cancelId, cancelTex);
    }

    /** Returns the texture identifier for the given wedge index. */
    public static Identifier getWedgeTexture(int wedgeIndex) {
        return wedgeIds[wedgeIndex];
    }

    /** Returns the texture identifier for the cancel circle. */
    public static Identifier getCancelTexture() {
        return cancelId;
    }

    /**
     * Generates a white-on-transparent mask for a single wedge of the annular ring.
     * Uses 4x4 multi-sampling at each pixel for anti-aliased edges.
     */
    private static NativeImage generateWedgeMask(int wedgeIndex) {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double half = TEX_SIZE / 2.0;

        double wedgeStartAngle = wedgeIndex * (2.0 * Math.PI / WEDGE_COUNT);
        double wedgeEndAngle = (wedgeIndex + 1) * (2.0 * Math.PI / WEDGE_COUNT);

        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                int hits = 0;
                for (int sy = 0; sy < AA_SAMPLES; sy++) {
                    for (int sx = 0; sx < AA_SAMPLES; sx++) {
                        double x = (px + (sx + 0.5) / AA_SAMPLES - half) / half;
                        double y = (py + (sy + 0.5) / AA_SAMPLES - half) / half;
                        double dist = Math.sqrt(x * x + y * y);

                        if (dist < NORM_INNER || dist > NORM_OUTER) continue;

                        // Angle from top, clockwise - matches updateHoveredIndex
                        double angle = Math.atan2(x, -y);
                        if (angle < 0) angle += 2.0 * Math.PI;

                        if (angle >= wedgeStartAngle && angle < wedgeEndAngle) {
                            hits++;
                        }
                    }
                }

                if (hits > 0) {
                    int alpha = hits * 255 / (AA_SAMPLES * AA_SAMPLES);
                    image.setPixel(px, py, ARGB.color(alpha, 255, 255, 255));
                }
            }
        }
        return image;
    }

    /**
     * Generates a white-on-transparent filled circle mask for the cancel zone.
     * Radius is slightly smaller than the inner ring to leave a gap.
     */
    private static NativeImage generateCancelMask() {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        double half = TEX_SIZE / 2.0;
        // Cancel radius = (INNER_RADIUS - 2) / OUTER_RADIUS in normalized space
        double cancelNorm = (30.0 - 2.0) / 100.0;

        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                int hits = 0;
                for (int sy = 0; sy < AA_SAMPLES; sy++) {
                    for (int sx = 0; sx < AA_SAMPLES; sx++) {
                        double x = (px + (sx + 0.5) / AA_SAMPLES - half) / half;
                        double y = (py + (sy + 0.5) / AA_SAMPLES - half) / half;
                        double dist = Math.sqrt(x * x + y * y);
                        if (dist <= cancelNorm) hits++;
                    }
                }

                if (hits > 0) {
                    int alpha = hits * 255 / (AA_SAMPLES * AA_SAMPLES);
                    image.setPixel(px, py, ARGB.color(alpha, 255, 255, 255));
                }
            }
        }
        return image;
    }
}
