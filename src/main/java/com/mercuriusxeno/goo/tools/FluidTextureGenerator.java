package com.mercuriusxeno.goo.tools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates animated fluid textures and blob base sprites using a cellular automata
 * algorithm inspired by Minecraft Classic's lava/water animation.
 *
 * Three heat layers (soupHeat, potHeat, flameHeat) simulate fluid dynamics.
 * Per-type parameters control viscosity, turbulence, color palettes, and frame speed.
 *
 * Run via main() - outputs to src/main/resources/assets/goo/textures/
 */
public class FluidTextureGenerator {

    private static final int SIZE = 16;
    private static final int FRAMES = 32;
    private static final int WARMUP = 60;

    private static final Path OUTPUT_ROOT = Path.of("src/main/resources/assets/goo/textures");
    private static final Path FLUID_DIR = OUTPUT_ROOT.resolve("fluid");
    private static final Path ITEM_DIR = OUTPUT_ROOT.resolve("item");

    private static final Path BLOB_MASK_TINY_PATH = ITEM_DIR.resolve("goo_blob_mask_tiny.png");
    private static final Path BLOB_MASK_SMALL_PATH = ITEM_DIR.resolve("goo_blob_mask_small.png");
    private static final Path BLOB_MASK_PATH = ITEM_DIR.resolve("goo_blob_mask.png");
    private static final Path BLOB_MASK_LARGE_PATH = ITEM_DIR.resolve("goo_blob_mask_large.png");

    private static final Path FLUID_TYPES_JSON = Path.of("src/main/resources/data/goo/goo_fluid_types.json");

    // Hue-shifting luminance modulation constants
    /** Luminance above this threshold produces specular sheen. */
    private static final float HIGHLIGHT_THRESHOLD = 0.8f;
    /** How aggressively shadow hue rotates toward the per-type shadow hue. */
    private static final float HUE_SHIFT_STRENGTH = 0.3f;
    /** Saturation boost applied in shadow regions to counteract desaturation. */
    private static final float SAT_BOOST = 0.2f;


    public static void main(String[] args) throws IOException {
        Files.createDirectories(FLUID_DIR);
        Files.createDirectories(ITEM_DIR);

        List<GooFluidType> types = loadFluidTypes();

        for (GooFluidType type : types) {
            System.out.println("Generating: " + type.id());
            generateFluidTexture(type);
            generateBlobBase(type);
        }

        System.out.println("Done. Generated " + types.size() + " fluid textures and blob bases.");
    }

    /** Loads goo fluid type definitions from the JSON data file. */
    static List<GooFluidType> loadFluidTypes() throws IOException {
        String json = Files.readString(FLUID_TYPES_JSON);
        return parseFluidTypes(json);
    }

    /** Parses goo fluid type definitions from a JSON string. */
    static List<GooFluidType> parseFluidTypes(String json) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<GooFluidTypeJson>>() {}.getType();
        List<GooFluidTypeJson> rawTypes = gson.fromJson(json, listType);
        return rawTypes.stream().map(GooFluidTypeJson::toFluidType).toList();
    }

    /** Generates an animated fluid sprite strip for a goo type. */
    private static void generateFluidTexture(GooFluidType type) throws IOException {
        FluidCA ca = new FluidCA(type.genParams(), type.seed());

        for (int i = 0; i < WARMUP; i++) {
            ca.tick();
        }

        float[][] heatFrames = captureHeatFrames(ca);
        float[] heatRange = findHeatRange(heatFrames);
        BufferedImage strip = renderFluidStrip(heatFrames, heatRange, type.palette());

        Path pngPath = FLUID_DIR.resolve(type.id() + "_fluid.png");
        ImageIO.write(strip, "PNG", pngPath.toFile());
        writeFluidMcmeta(type, type.id() + "_fluid.png.mcmeta");

        System.out.printf("  %s: heat range %.4f-%.4f (spread %.4f)%n",
                type.id(), heatRange[0], heatRange[0] + heatRange[1], heatRange[1]);
    }

    /** Renders a fluid sprite strip from normalized heat frames. */
    private static BufferedImage renderFluidStrip(float[][] heatFrames, float[] heatRange, Palette palette) {
        float minHeat = heatRange[0];
        float range = heatRange[1];

        BufferedImage strip = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    float normalized = normalizeHeat(heatFrames[frame][y * SIZE + x], minHeat, range);
                    strip.setRGB(x, frame * SIZE + y, palette.sample(normalized));
                }
            }
        }
        return strip;
    }

    /** Writes a looping mcmeta for a fluid sprite strip. */
    private static void writeFluidMcmeta(GooFluidType type, String filename) throws IOException {
        String mcmeta = """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true
                  }
                }
                """.formatted(type.frametime());
        Files.writeString(FLUID_DIR.resolve(filename), mcmeta);
    }

    /** Generates tiny, small, base, and large blob sprites for a goo type using luminance masks. */
    private static void generateBlobBase(GooFluidType type) throws IOException {
        FluidCA ca = new FluidCA(type.genParams(), type.seed() + 7919L);

        for (int i = 0; i < WARMUP + 20; i++) {
            ca.tick();
        }

        float[][] heatFrames = captureHeatFrames(ca);
        float[] heatRange = findHeatRange(heatFrames);

        float[][] tinyMask = loadLuminanceMask(BLOB_MASK_TINY_PATH);
        float[][] smallMask = loadLuminanceMask(BLOB_MASK_SMALL_PATH);
        float[][] baseMask = loadLuminanceMask(BLOB_MASK_PATH);
        float[][] largeMask = loadLuminanceMask(BLOB_MASK_LARGE_PATH);

        writeMaskedBlobVariant(heatFrames, heatRange, type, tinyMask, type.id() + "_blob_tiny");
        writeMaskedBlobVariant(heatFrames, heatRange, type, smallMask, type.id() + "_blob_small");
        writeMaskedBlobVariant(heatFrames, heatRange, type, baseMask, type.id() + "_blob_base");
        writeMaskedBlobVariant(heatFrames, heatRange, type, largeMask, type.id() + "_blob_large");
    }

    /** Writes a masked blob variant (luminance-shaded, single layer) with sprite strip + mcmeta. */
    private static void writeMaskedBlobVariant(float[][] heatFrames, float[] heatRange,
            GooFluidType type, float[][] mask, String baseName) throws IOException {
        BufferedImage strip = renderBlobStripMasked(heatFrames, heatRange, type, mask);
        ImageIO.write(strip, "PNG", ITEM_DIR.resolve(baseName + ".png").toFile());
        writeBlobMcmeta(type, baseName + ".png.mcmeta");
    }


    /** Captures FRAMES of raw heat values from the CA. */
    private static float[][] captureHeatFrames(FluidCA ca) {
        float[][] heatFrames = new float[FRAMES][SIZE * SIZE];
        for (int frame = 0; frame < FRAMES; frame++) {
            ca.tick();
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    heatFrames[frame][y * SIZE + x] = ca.getRawHeat(x, y);
                }
            }
        }
        return heatFrames;
    }

    /** Returns {min, range} across all frames. */
    private static float[] findHeatRange(float[][] heatFrames) {
        float minHeat = Float.MAX_VALUE;
        float maxHeat = Float.MIN_VALUE;
        for (float[] frame : heatFrames) {
            for (float h : frame) {
                minHeat = Math.min(minHeat, h);
                maxHeat = Math.max(maxHeat, h);
            }
        }
        float range = maxHeat - minHeat;
        if (range < 0.001f) range = 1.0f;
        return new float[]{minHeat, range};
    }

    /** Loads a PNG mask and extracts per-pixel luminance (0.0-1.0), incorporating alpha. */
    private static float[][] loadLuminanceMask(Path maskPath) throws IOException {
        BufferedImage mask = ImageIO.read(maskPath.toFile());
        float[][] luminance = new float[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                luminance[y][x] = extractLuminance(mask.getRGB(x, y));
            }
        }
        return luminance;
    }

    /** Extracts luminance from an ARGB pixel, scaled by alpha. Transparent pixels return 0. */
    private static float extractLuminance(int argb) {
        int a = (argb >> 24) & 0xFF;
        if (a == 0) return 0.0f;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
        return lum * (a / 255.0f);
    }

    /** Renders a blob sprite strip with luminance mask modulating the palette colors. */
    private static BufferedImage renderBlobStripMasked(
            float[][] heatFrames, float[] heatRange, GooFluidType type, float[][] mask) {
        float minHeat = heatRange[0];
        float range = heatRange[1];

        BufferedImage strip = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int py = frame * SIZE + y;
                    float lum = mask[y][x];
                    if (lum <= 0) {
                        strip.setRGB(x, py, 0x00000000);
                        continue;
                    }
                    float normalized = normalizeHeat(heatFrames[frame][y * SIZE + x], minHeat, range);
                    strip.setRGB(x, py, modulateColorShaded(type.palette().sample(normalized), lum, type.shadowHue()));
                }
            }
        }
        return strip;
    }

    /**
     * Two-zone luminance modulation: hue-shifts shadows toward a per-type shadow hue,
     * and blends highlights toward white for specular sheen.
     */
    private static int modulateColorShaded(int argb, float luminance, float shadowHue) {
        if (luminance >= HIGHLIGHT_THRESHOLD) {
            float sheenFactor = (luminance - HIGHLIGHT_THRESHOLD) / (1.0f - HIGHLIGHT_THRESHOLD);
            return applyHighlight(argb, sheenFactor);
        }
        float normalizedLum = luminance / HIGHLIGHT_THRESHOLD;
        return applyShadow(argb, normalizedLum, shadowHue);
    }

    /** Darkens the color via HSB. Negative shadowHue disables hue shift and saturation boost. */
    private static int applyShadow(int argb, float normalizedLum, float shadowHue) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float darkness = 1.0f - normalizedLum;
        if (shadowHue >= 0) {
            hsb[0] = lerpFloat(hsb[0], shadowHue, darkness * HUE_SHIFT_STRENGTH);
            hsb[1] = Math.min(1.0f, hsb[1] + darkness * SAT_BOOST);
        }
        hsb[2] = hsb[2] * normalizedLum;

        int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    /** Lerps the palette color toward white by a sheen factor (capped at 0.5 blend). */
    private static int applyHighlight(int argb, float sheenFactor) {
        int a = (argb >> 24) & 0xFF;
        float t = sheenFactor * 0.5f;
        int r = (int) lerpFloat((argb >> 16) & 0xFF, 255, t);
        int g = (int) lerpFloat((argb >> 8) & 0xFF, 255, t);
        int b = (int) lerpFloat(argb & 0xFF, 255, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Linear interpolation between two floats. */
    private static float lerpFloat(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Clamps and normalizes a heat value to 0.0-1.0. */
    private static float normalizeHeat(float heat, float minHeat, float range) {
        return Math.min(1.0f, Math.max(0, (heat - minHeat) / range));
    }


    /** Writes a ping-pong mcmeta for a blob sprite strip. */
    private static void writeBlobMcmeta(GooFluidType type, String filename) throws IOException {
        StringBuilder frames = buildPingPongFrames();

        String mcmeta = """
                {
                  "animation": {
                    "frametime": %d,
                    "interpolate": true,
                    "frames": [%s]
                  }
                }
                """.formatted(type.frametime(), frames);

        Path mcmetaPath = ITEM_DIR.resolve(filename);
        Files.writeString(mcmetaPath, mcmeta);
    }

    /** Builds a ping-pong frame list: 0,1,...,FRAMES-1,FRAMES-2,...,1 */
    private static StringBuilder buildPingPongFrames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) {
            if (i > 0) sb.append(", ");
            sb.append(i);
        }
        for (int i = FRAMES - 2; i >= 1; i--) {
            sb.append(", ").append(i);
        }
        return sb;
    }


    // ---- Cellular Automata Engine ----

    /**
     * Cellular automata simulation for fluid dynamics.
     * Three heat layers (soup, pot, flame) interact to produce organic fluid motion.
     */
    static class FluidCA {
        private final GooFluidGenParams params;
        private final float[][] soupHeat = new float[SIZE][SIZE];
        private final float[][] potHeat = new float[SIZE][SIZE];
        private final float[][] flameHeat = new float[SIZE][SIZE];
        private final Random random;

        /** Creates a CA engine with the given parameters and RNG seed. */
        FluidCA(GooFluidGenParams params, long seed) {
            this.params = params;
            this.random = new Random(seed);
        }

        /** Advances the simulation by one tick, updating all three heat layers. */
        void tick() {
            float[][] newSoup = new float[SIZE][SIZE];
            float[][] newPot = new float[SIZE][SIZE];
            float[][] newFlame = new float[SIZE][SIZE];

            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    newSoup[y][x] = computeSoupHeat(x, y);
                    newPot[y][x] = computePotHeat(x, y);
                    computeFlameHeat(x, y, newFlame);
                }
            }

            System.arraycopy(newSoup, 0, soupHeat, 0, SIZE);
            System.arraycopy(newPot, 0, potHeat, 0, SIZE);
            System.arraycopy(newFlame, 0, flameHeat, 0, SIZE);
        }

        /** Computes new soup heat for a cell by blending with neighbors and injecting pot heat. */
        private float computeSoupHeat(int x, int y) {
            float neighborAvg = computeNeighborAverage(x, y);
            float blend = Math.min(0.7f, params.viscosity() * 0.28f);
            float smoothed = soupHeat[y][x] * blend + neighborAvg * (1.0f - blend);
            float soupDecay = 0.95f;
            return smoothed * soupDecay + potHeat[y][x] * params.potInfluence();
        }

        /** Computes the average soup heat of neighboring cells within the reach radius. */
        private float computeNeighborAverage(int x, int y) {
            float neighborSum = 0;
            int neighborCount = 0;
            int reach = params.neighborhoodReach();
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = (x + dx + SIZE) % SIZE;
                    int ny = (y + dy + SIZE) % SIZE;
                    neighborSum += soupHeat[ny][nx];
                    neighborCount++;
                }
            }
            return neighborSum / neighborCount;
        }

        /** Computes new pot heat for a cell: accumulates from flame with decay. */
        private float computePotHeat(int x, int y) {
            float potDecay = 0.9f;
            return Math.max(0, potHeat[y][x] * potDecay + flameHeat[y][x] * params.potHeatRate());
        }

        /** Computes flame heat with random ignition and cluster spread. Writes into newFlame accumulator. */
        private void computeFlameHeat(int x, int y, float[][] newFlame) {
            float flame = flameHeat[y][x] - params.decayRate();
            if (random.nextFloat() < params.ignitionChance()) {
                flame = params.ignitionStrength();
                spreadIgnitionToNeighbors(x, y, newFlame);
            }
            newFlame[y][x] = Math.max(newFlame[y][x], Math.max(0, flame));
        }

        /** Spreads ignition to the 3x3 neighborhood with cardinal/diagonal falloff. */
        private void spreadIgnitionToNeighbors(int x, int y, float[][] newFlame) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = (x + dx + SIZE) % SIZE;
                    int ny = (y + dy + SIZE) % SIZE;
                    float falloff = (dx == 0 || dy == 0) ? 0.6f : 0.35f;
                    newFlame[ny][nx] = Math.max(newFlame[ny][nx], params.ignitionStrength() * falloff);
                }
            }
        }

        /** Returns the raw soup heat at the given cell. */
        float getRawHeat(int x, int y) {
            return soupHeat[y][x];
        }
    }

    // ---- Color Palette with Positioned Stops ----

    /**
     * A gradient palette with arbitrarily positioned color stops.
     * Stops are defined as (position, r, g, b, a) where position is 0.0-1.0.
     * Heat values between stops are linearly interpolated.
     */
    record Palette(float[] positions, int[][] colors) {

        /** Creates a palette from (position, hexColor) pairs. Hex format: "#RRGGBB" or "#RRGGBBAA". */
        static Palette of(Object... args) {
            if (args.length % 2 != 0) {
                throw new IllegalArgumentException("Args must be pairs of (float position, String hex)");
            }
            int stops = args.length / 2;
            float[] positions = new float[stops];
            int[][] colors = new int[stops][4];
            for (int i = 0; i < stops; i++) {
                positions[i] = ((Number) args[i * 2]).floatValue();
                colors[i] = parseHex((String) args[i * 2 + 1]);
            }
            return new Palette(positions, colors);
        }

        /** Creates a palette from a list of deserialized palette stops. */
        static Palette fromStops(List<PaletteStop> stops) {
            float[] positions = new float[stops.size()];
            int[][] colors = new int[stops.size()][4];
            for (int i = 0; i < stops.size(); i++) {
                positions[i] = stops.get(i).position();
                colors[i] = parseHex(stops.get(i).color());
            }
            return new Palette(positions, colors);
        }

        /** Parses "#RRGGBB" or "#RRGGBBAA" into {r, g, b, a}. */
        private static int[] parseHex(String hex) {
            String h = hex.startsWith("#") ? hex.substring(1) : hex;
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            int a = h.length() >= 8 ? Integer.parseInt(h.substring(6, 8), 16) : 255;
            return new int[]{r, g, b, a};
        }

        /** Samples the palette at position t (0.0-1.0), interpolating between surrounding stops. */
        int sample(float t) {
            t = Math.min(1.0f, Math.max(0, t));

            if (t <= positions[0]) return packColor(colors[0]);
            if (t >= positions[positions.length - 1]) return packColor(colors[colors.length - 1]);

            for (int i = 0; i < positions.length - 1; i++) {
                if (t >= positions[i] && t <= positions[i + 1]) {
                    float local = (t - positions[i]) / (positions[i + 1] - positions[i]);
                    return lerpColor(colors[i], colors[i + 1], local);
                }
            }
            return packColor(colors[colors.length - 1]);
        }

        /** Linearly interpolates between two RGBA color arrays. */
        private static int lerpColor(int[] a, int[] b, float t) {
            int r = (int) (a[0] + (b[0] - a[0]) * t);
            int g = (int) (a[1] + (b[1] - a[1]) * t);
            int blue = (int) (a[2] + (b[2] - a[2]) * t);
            int alpha = (int) (a[3] + (b[3] - a[3]) * t);
            return (alpha << 24) | (r << 16) | (g << 8) | blue;
        }

        /** Packs an {r, g, b, a} array into an ARGB int. */
        private static int packColor(int[] c) {
            return (c[3] << 24) | (c[0] << 16) | (c[1] << 8) | c[2];
        }
    }

    // ---- Per-Type Data Records ----

    /** Cellular automata tuning parameters that control fluid simulation behavior. */
    record GooFluidGenParams(
            float viscosity,
            float decayRate,
            float ignitionChance,
            float ignitionStrength,
            float potHeatRate,
            float potInfluence,
            int neighborhoodReach
    ) {}

    /** A single color stop in a gradient palette, used for JSON deserialization. */
    record PaletteStop(float position, String color) {}

    /** Complete definition of a goo fluid type, including CA params, palette, and rendering hints. */
    record GooFluidType(String id, long seed, GooFluidGenParams genParams, int frametime, float shadowHue, Palette palette) {}

    /** JSON-shaped intermediate for Gson deserialization, converted to GooFluidType via toFluidType(). */
    private record GooFluidTypeJson(
            String id,
            long seed,
            GooFluidGenParams genParams,
            int frametime,
            float shadowHue,
            List<PaletteStop> palette
    ) {
        /** Converts the deserialized JSON form into a full GooFluidType with a built Palette. */
        GooFluidType toFluidType() {
            return new GooFluidType(id, seed, genParams, frametime, shadowHue, Palette.fromStops(palette));
        }
    }
}
