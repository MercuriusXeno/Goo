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
public final class FluidTextureGenerator {

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

    /** Seed offset for blob CA to decorrelate from fluid CA. */
    private static final long BLOB_SEED_OFFSET = 7919L;
    /** Extra warmup ticks for blob generation beyond base warmup. */
    private static final int BLOB_EXTRA_WARMUP = 20;

    /** Minimum heat range before fallback to 1.0 to avoid division by near-zero. */
    private static final float MIN_HEAT_RANGE = 0.001f;

    // ── ARGB bit-shift and mask constants ──
    /** Bit shift for alpha channel in ARGB int. */
    private static final int ALPHA_SHIFT = 24;
    /** Bit shift for red channel in ARGB int. */
    private static final int RED_SHIFT = 16;
    /** Bit shift for green channel in ARGB int. */
    private static final int GREEN_SHIFT = 8;
    /** Mask for extracting a single color channel byte. */
    private static final int CHANNEL_MASK = 0xFF;
    /** Mask for stripping alpha from an ARGB int. */
    private static final int RGB_MASK = 0x00FFFFFF;
    /** Fully transparent ARGB pixel. */
    private static final int TRANSPARENT = 0x00000000;
    /** Maximum channel value (white). */
    private static final int MAX_CHANNEL = 255;

    // ── Luminance extraction weights (Rec. 601) ──
    /** Red weight for luminance calculation. */
    private static final float LUMA_RED_WEIGHT = 0.299f;
    /** Green weight for luminance calculation. */
    private static final float LUMA_GREEN_WEIGHT = 0.587f;
    /** Blue weight for luminance calculation. */
    private static final float LUMA_BLUE_WEIGHT = 0.114f;
    /** Channel normalizer (divides 0-255 to 0.0-1.0). */
    private static final float CHANNEL_NORMALIZER = 255.0f;

    /** Maximum specular sheen blend factor. */
    private static final float MAX_SHEEN_BLEND = 0.5f;

    // ── Palette hex parsing constants ──
    /** Start index for red hex digits. */
    private static final int HEX_RED_START = 0;
    /** End index for red hex digits. */
    private static final int HEX_RED_END = 2;
    /** Start index for green hex digits. */
    private static final int HEX_GREEN_START = 2;
    /** End index for green hex digits. */
    private static final int HEX_GREEN_END = 4;
    /** Start index for blue hex digits. */
    private static final int HEX_BLUE_START = 4;
    /** End index for blue hex digits. */
    private static final int HEX_BLUE_END = 6;
    /** Start index for alpha hex digits. */
    private static final int HEX_ALPHA_START = 6;
    /** End index for alpha hex digits. */
    private static final int HEX_ALPHA_END = 8;
    /** Minimum hex string length that includes an alpha channel. */
    private static final int HEX_WITH_ALPHA_LENGTH = 8;
    /** Default alpha for hex colors without explicit alpha. */
    private static final int DEFAULT_ALPHA = 255;
    /** Hex radix for color parsing. */
    private static final int HEX_RADIX = 16;

    // ── Palette color array indices ──
    /** Index for red in RGBA color arrays. */
    private static final int IDX_R = 0;
    /** Index for green in RGBA color arrays. */
    private static final int IDX_G = 1;
    /** Index for blue in RGBA color arrays. */
    private static final int IDX_B = 2;
    /** Index for alpha in RGBA color arrays. */
    private static final int IDX_A = 3;
    /** Number of components in an RGBA color array. */
    private static final int RGBA_COMPONENTS = 4;
    /** Required pair size for palette varargs (position + hex). */
    private static final int PALETTE_PAIR_SIZE = 2;

    // ── CA engine constants ──
    /** Maximum viscosity blend factor for soup heat smoothing. */
    private static final float MAX_VISCOSITY_BLEND = 0.7f;
    /** Viscosity scaling factor for blend computation. */
    private static final float VISCOSITY_SCALE = 0.28f;
    /** Soup heat decay rate per tick. */
    private static final float SOUP_DECAY = 0.95f;
    /** Pot heat decay rate per tick. */
    private static final float POT_DECAY = 0.9f;
    /** Cardinal neighbor ignition falloff (adjacent on axis). */
    private static final float CARDINAL_FALLOFF = 0.6f;
    /** Diagonal neighbor ignition falloff. */
    private static final float DIAGONAL_FALLOFF = 0.35f;
    /** Offset from end of frame list to start the reverse pass (skip last frame already included). */
    private static final int PING_PONG_REVERSE_OFFSET = 2;

    /** Console message prefix for generation progress. */
    private static final String MSG_GENERATING = "Generating: ";
    /** Console message prefix for completion summary. */
    private static final String MSG_DONE_PREFIX = "Done. Generated ";
    /** Console message suffix for completion summary. */
    private static final String MSG_DONE_SUFFIX = " fluid textures and blob bases.";
    /** File suffix for fluid PNG textures. */
    private static final String SUFFIX_FLUID_PNG = "_fluid.png";
    /** Image format for PNG output. */
    private static final String FORMAT_PNG = "PNG";
    /** File suffix for fluid mcmeta sidecar. */
    private static final String SUFFIX_FLUID_MCMETA = "_fluid.png.mcmeta";
    /** Console format for heat range debug output. */
    private static final String FMT_HEAT_RANGE = "  %s: heat range %.4f-%.4f (spread %.4f)%n";
    /** File suffix for blob variant PNG textures. */
    private static final String SUFFIX_BLOB_TINY = "_blob_tiny";
    /** File suffix for small blob variant. */
    private static final String SUFFIX_BLOB_SMALL = "_blob_small";
    /** File suffix for base blob variant. */
    private static final String SUFFIX_BLOB_BASE = "_blob_base";
    /** File suffix for large blob variant. */
    private static final String SUFFIX_BLOB_LARGE = "_blob_large";
    /** File extension for PNG files. */
    private static final String EXT_PNG = ".png";
    /** File extension suffix for mcmeta sidecar files. */
    private static final String EXT_PNG_MCMETA = ".png.mcmeta";
    /** Separator between ping-pong frame indices. */
    private static final String FRAME_SEPARATOR = ", ";
    /** Hex color prefix character. */
    private static final String HEX_PREFIX = "#";
    /** Negative direction for neighbor iteration. */
    private static final int NEIGHBOR_NEG = -1;
    /** Error message for invalid palette varargs. */
    private static final String ERR_PALETTE_PAIRS = "Args must be pairs of (float position, String hex)";

    private FluidTextureGenerator() {}

    /**
     * Generates all goo fluid and blob textures from the JSON type definitions.
     *
     * @param args unused
     * @throws IOException if texture files cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Files.createDirectories(FLUID_DIR);
        Files.createDirectories(ITEM_DIR);
        List<GooFluidType> types = loadFluidTypes();
        for (GooFluidType type : types) {
            System.out.println(MSG_GENERATING + type.id());
            generateFluidTexture(type);
            generateBlobBase(type);
        }
        System.out.println(MSG_DONE_PREFIX + types.size() + MSG_DONE_SUFFIX);
    }

    /**
     * Loads goo fluid type definitions from the JSON data file.
     *
     * @return the parsed list of fluid type definitions
     * @throws IOException if the file cannot be read
     */
    static List<GooFluidType> loadFluidTypes() throws IOException {
        String json = Files.readString(FLUID_TYPES_JSON);
        return parseFluidTypes(json);
    }

    /**
     * Parses goo fluid type definitions from a JSON string.
     *
     * @param json the JSON string to parse
     * @return the parsed list of fluid type definitions
     */
    static List<GooFluidType> parseFluidTypes(String json) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<GooFluidTypeJson>>() {}.getType();
        List<GooFluidTypeJson> rawTypes = gson.fromJson(json, listType);
        return rawTypes.stream().map(GooFluidTypeJson::toFluidType).toList();
    }

    /**
     * Generates an animated fluid sprite strip for a goo type.
     *
     * @param type the goo fluid type definition
     * @throws IOException if texture files cannot be written
     */
    private static void generateFluidTexture(GooFluidType type) throws IOException {
        FluidCA ca = new FluidCA(type.genParams(), type.seed());
        warmup(ca, WARMUP);
        float[][] heatFrames = captureHeatFrames(ca);
        float[] heatRange = findHeatRange(heatFrames);
        writeFluidStrip(type, heatFrames, heatRange);
    }

    /** Advances the CA the specified number of ticks to reach a stable state.
     *
     * @param ca    the cellular automata engine
     * @param ticks the number of warmup ticks
     */
    private static void warmup(FluidCA ca, int ticks) {
        for (int i = 0; i < ticks; i++) { ca.tick(); }
    }

    /** Renders, writes, and logs the fluid sprite strip.
     *
     * @param type       the goo fluid type definition
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @throws IOException if files cannot be written
     */
    private static void writeFluidStrip(GooFluidType type, float[][] heatFrames,
                                         float[] heatRange) throws IOException {
        BufferedImage strip = renderFluidStrip(heatFrames, heatRange, type.palette());
        ImageIO.write(strip, FORMAT_PNG, FLUID_DIR.resolve(type.id() + SUFFIX_FLUID_PNG).toFile());
        writeFluidMcmeta(type, type.id() + SUFFIX_FLUID_MCMETA);
        System.out.printf(FMT_HEAT_RANGE, type.id(), heatRange[0], heatRange[0] + heatRange[1], heatRange[1]);
    }

    /**
     * Renders a fluid sprite strip from normalized heat frames.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param palette    the color palette to sample
     * @return the rendered sprite strip image
     */
    private static BufferedImage renderFluidStrip(float[][] heatFrames, float[] heatRange, Palette palette) {
        BufferedImage strip = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAMES; frame++) {
            renderFluidFrame(strip, heatFrames[frame], frame, heatRange[0], heatRange[1], palette);
        }
        return strip;
    }

    /** Renders a single frame of the fluid strip into the composite image.
     *
     * @param strip   the composite sprite strip image
     * @param heat    the raw heat values for this frame
     * @param frame   the frame index
     * @param minHeat the minimum heat for normalization
     * @param range   the heat range for normalization
     * @param palette the color palette to sample
     */
    private static void renderFluidFrame(BufferedImage strip, float[] heat, int frame,
                                          float minHeat, float range, Palette palette) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float normalized = normalizeHeat(heat[y * SIZE + x], minHeat, range);
                strip.setRGB(x, frame * SIZE + y, palette.sample(normalized));
            }
        }
    }

    /**
     * Writes a looping mcmeta for a fluid sprite strip.
     *
     * @param type     the goo fluid type definition
     * @param filename the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    private static void writeFluidMcmeta(GooFluidType type, String filename) throws IOException {
        String mcmeta = """
                {"animation": {"frametime": %d, "interpolate": true}}
                """.formatted(type.frametime());
        Files.writeString(FLUID_DIR.resolve(filename), mcmeta);
    }

    /**
     * Generates tiny, small, base, and large blob sprites for a goo type using luminance masks.
     *
     * @param type the goo fluid type definition
     * @throws IOException if texture or mask files cannot be read or written
     */
    private static void generateBlobBase(GooFluidType type) throws IOException {
        FluidCA ca = new FluidCA(type.genParams(), type.seed() + BLOB_SEED_OFFSET);
        warmup(ca, WARMUP + BLOB_EXTRA_WARMUP);
        float[][] heatFrames = captureHeatFrames(ca);
        float[] heatRange = findHeatRange(heatFrames);
        writeBlobVariants(type, heatFrames, heatRange);
    }

    /** Loads all size masks and writes masked blob sprite variants.
     *
     * @param type       the goo fluid type definition
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @throws IOException if texture or mask files cannot be read or written
     */
    private static void writeBlobVariants(GooFluidType type, float[][] heatFrames,
                                           float[] heatRange) throws IOException {
        float[][] tinyMask = loadLuminanceMask(BLOB_MASK_TINY_PATH);
        float[][] smallMask = loadLuminanceMask(BLOB_MASK_SMALL_PATH);
        float[][] baseMask = loadLuminanceMask(BLOB_MASK_PATH);
        float[][] largeMask = loadLuminanceMask(BLOB_MASK_LARGE_PATH);
        writeMaskedBlobVariant(heatFrames, heatRange, type, tinyMask, type.id() + SUFFIX_BLOB_TINY);
        writeMaskedBlobVariant(heatFrames, heatRange, type, smallMask, type.id() + SUFFIX_BLOB_SMALL);
        writeMaskedBlobVariant(heatFrames, heatRange, type, baseMask, type.id() + SUFFIX_BLOB_BASE);
        writeMaskedBlobVariant(heatFrames, heatRange, type, largeMask, type.id() + SUFFIX_BLOB_LARGE);
    }

    /**
     * Writes a masked blob variant (luminance-shaded, single layer) with sprite strip + mcmeta.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param type       the goo fluid type definition
     * @param mask       the luminance mask for shading
     * @param baseName   the output filename without extension
     * @throws IOException if texture files cannot be written
     */
    private static void writeMaskedBlobVariant(float[][] heatFrames, float[] heatRange,
            GooFluidType type, float[][] mask, String baseName) throws IOException {
        BufferedImage strip = renderBlobStripMasked(heatFrames, heatRange, type, mask);
        ImageIO.write(strip, FORMAT_PNG, ITEM_DIR.resolve(baseName + EXT_PNG).toFile());
        writeBlobMcmeta(type, baseName + EXT_PNG_MCMETA);
    }


    /**
     * Captures FRAMES of raw heat values from the CA.
     *
     * @param ca the cellular automata engine
     * @return array of heat values indexed by [frame][pixel]
     */
    private static float[][] captureHeatFrames(FluidCA ca) {
        float[][] heatFrames = new float[FRAMES][SIZE * SIZE];
        for (int frame = 0; frame < FRAMES; frame++) {
            ca.tick();
            captureOneFrame(ca, heatFrames[frame]);
        }
        return heatFrames;
    }

    /** Captures the CA's current heat state into a flat pixel array.
     *
     * @param ca    the cellular automata engine
     * @param dest  the destination array (SIZE*SIZE elements)
     */
    private static void captureOneFrame(FluidCA ca, float[] dest) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) { dest[y * SIZE + x] = ca.getRawHeat(x, y); }
        }
    }

    /**
     * Returns {min, range} across all frames.
     *
     * @param heatFrames the raw heat values per frame
     * @return two-element array: [minHeat, range]
     */
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
        if (range < MIN_HEAT_RANGE) { range = 1.0f; }
        return new float[]{minHeat, range};
    }

    /**
     * Loads a PNG mask and extracts per-pixel luminance (0.0-1.0), incorporating alpha.
     *
     * @param maskPath the path to the PNG mask file
     * @return 2D luminance array indexed by [y][x]
     * @throws IOException if the mask file cannot be read
     */
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

    /**
     * Extracts luminance from an ARGB pixel, scaled by alpha. Transparent pixels return 0.
     *
     * @param argb the ARGB pixel value
     * @return luminance in the range 0.0 to 1.0
     */
    private static float extractLuminance(int argb) {
        int a = (argb >> ALPHA_SHIFT) & CHANNEL_MASK;
        if (a == 0) { return 0.0f; }
        int r = (argb >> RED_SHIFT) & CHANNEL_MASK;
        int g = (argb >> GREEN_SHIFT) & CHANNEL_MASK;
        int b = argb & CHANNEL_MASK;
        float lum = (LUMA_RED_WEIGHT * r + LUMA_GREEN_WEIGHT * g + LUMA_BLUE_WEIGHT * b) / CHANNEL_NORMALIZER;
        return lum * (a / CHANNEL_NORMALIZER);
    }

    /**
     * Renders a blob sprite strip with luminance mask modulating the palette colors.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param type       the goo fluid type definition
     * @param mask       the luminance mask for shading
     * @return the rendered blob sprite strip image
     */
    private static BufferedImage renderBlobStripMasked(
            float[][] heatFrames, float[] heatRange, GooFluidType type, float[][] mask) {
        BufferedImage strip = new BufferedImage(SIZE, SIZE * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FRAMES; frame++) {
            renderMaskedFrame(strip, heatFrames[frame], frame, heatRange[0], heatRange[1], type, mask);
        }
        return strip;
    }

    /** Renders a single masked blob frame into the composite strip.
     *
     * @param strip   the composite sprite strip image
     * @param heat    the raw heat values for this frame
     * @param frame   the frame index
     * @param minHeat the minimum heat for normalization
     * @param range   the heat range for normalization
     * @param type    the goo fluid type definition
     * @param mask    the luminance mask
     */
    private static void renderMaskedFrame(BufferedImage strip, float[] heat, int frame,
            float minHeat, float range, GooFluidType type, float[][] mask) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int py = frame * SIZE + y;
                float lum = mask[y][x];
                if (lum <= 0) {
                    strip.setRGB(x, py, TRANSPARENT);
                    continue;
                }
                float normalized = normalizeHeat(heat[y * SIZE + x], minHeat, range);
                strip.setRGB(x, py, modulateColorShaded(type.palette().sample(normalized), lum, type.shadowHue()));
            }
        }
    }

    /**
     * Two-zone luminance modulation: hue-shifts shadows toward a per-type shadow hue,
     * and blends highlights toward white for specular sheen.
     *
     * @param argb      the source ARGB color
     * @param luminance the mask luminance value (0.0 to 1.0)
     * @param shadowHue the per-type shadow hue for dark regions
     * @return the modulated ARGB color
     */
    private static int modulateColorShaded(int argb, float luminance, float shadowHue) {
        if (luminance >= HIGHLIGHT_THRESHOLD) {
            float sheenFactor = (luminance - HIGHLIGHT_THRESHOLD) / (1.0f - HIGHLIGHT_THRESHOLD);
            return applyHighlight(argb, sheenFactor);
        }
        float normalizedLum = luminance / HIGHLIGHT_THRESHOLD;
        return applyShadow(argb, normalizedLum, shadowHue);
    }

    /**
     * Darkens the color via HSB. Negative shadowHue disables hue shift and saturation boost.
     *
     * @param argb          the source ARGB color
     * @param normalizedLum the luminance normalized to highlight threshold
     * @param shadowHue     the target shadow hue, or negative to disable
     * @return the darkened ARGB color
     */
    private static int applyShadow(int argb, float normalizedLum, float shadowHue) {
        int a = (argb >> ALPHA_SHIFT) & CHANNEL_MASK;
        float[] hsb = Color.RGBtoHSB(
                (argb >> RED_SHIFT) & CHANNEL_MASK, (argb >> GREEN_SHIFT) & CHANNEL_MASK,
                argb & CHANNEL_MASK, null);
        shiftShadowHsb(hsb, normalizedLum, shadowHue);
        int rgb = Color.HSBtoRGB(hsb[IDX_R], hsb[IDX_G], hsb[IDX_B]);
        return (a << ALPHA_SHIFT) | (rgb & RGB_MASK);
    }

    /** Applies shadow hue shift, saturation boost, and brightness reduction in HSB space.
     *
     * @param hsb           the HSB array to modify in place
     * @param normalizedLum the luminance normalized to highlight threshold
     * @param shadowHue     the target shadow hue, or negative to disable
     */
    private static void shiftShadowHsb(float[] hsb, float normalizedLum, float shadowHue) {
        float darkness = 1.0f - normalizedLum;
        if (shadowHue >= 0) {
            hsb[IDX_R] = lerpFloat(hsb[IDX_R], shadowHue, darkness * HUE_SHIFT_STRENGTH);
            hsb[IDX_G] = Math.min(1.0f, hsb[IDX_G] + darkness * SAT_BOOST);
        }
        hsb[IDX_B] = hsb[IDX_B] * normalizedLum;
    }

    /**
     * Lerps the palette color toward white by a sheen factor (capped at 0.5 blend).
     *
     * @param argb        the source ARGB color
     * @param sheenFactor the specular sheen intensity (0.0 to 1.0)
     * @return the highlighted ARGB color
     */
    private static int applyHighlight(int argb, float sheenFactor) {
        int a = (argb >> ALPHA_SHIFT) & CHANNEL_MASK;
        float t = sheenFactor * MAX_SHEEN_BLEND;
        int r = (int) lerpFloat((argb >> RED_SHIFT) & CHANNEL_MASK, MAX_CHANNEL, t);
        int g = (int) lerpFloat((argb >> GREEN_SHIFT) & CHANNEL_MASK, MAX_CHANNEL, t);
        int b = (int) lerpFloat(argb & CHANNEL_MASK, MAX_CHANNEL, t);
        return (a << ALPHA_SHIFT) | (r << RED_SHIFT) | (g << GREEN_SHIFT) | b;
    }

    /**
     * Linear interpolation between two floats.
     *
     * @param from the start value
     * @param to   the end value
     * @param t    the interpolation factor (0.0 to 1.0)
     * @return the interpolated value
     */
    private static float lerpFloat(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * Clamps and normalizes a heat value to 0.0-1.0.
     *
     * @param heat    the raw heat value
     * @param minHeat the minimum heat across all frames
     * @param range   the heat range across all frames
     * @return the normalized heat value
     */
    private static float normalizeHeat(float heat, float minHeat, float range) {
        return Math.min(1.0f, Math.max(0, (heat - minHeat) / range));
    }


    /**
     * Writes a ping-pong mcmeta for a blob sprite strip.
     *
     * @param type     the goo fluid type definition
     * @param filename the mcmeta filename to write
     * @throws IOException if the file cannot be written
     */
    private static void writeBlobMcmeta(GooFluidType type, String filename) throws IOException {
        String mcmeta = """
                {"animation": {"frametime": %d, "interpolate": true, "frames": [%s]}}
                """.formatted(type.frametime(), buildPingPongFrames());
        Files.writeString(ITEM_DIR.resolve(filename), mcmeta);
    }

    /**
     * Builds a ping-pong frame list: 0,1,...,FRAMES-1,FRAMES-2,...,1
     *
     * @return the comma-separated frame index list
     */
    private static StringBuilder buildPingPongFrames() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < FRAMES; i++) {
            if (i > 0) { sb.append(FRAME_SEPARATOR); }
            sb.append(i);
        }
        for (int i = FRAMES - PING_PONG_REVERSE_OFFSET; i >= 1; i--) {
            sb.append(FRAME_SEPARATOR).append(i);
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

        /**
         * Creates a CA engine with the given parameters and RNG seed.
         *
         * @param params the simulation tuning parameters
         * @param seed   the RNG seed for deterministic output
         */
        FluidCA(GooFluidGenParams params, long seed) {
            this.params = params;
            this.random = new Random(seed);
        }

        /** Advances the simulation by one tick, updating all three heat layers. */
        void tick() {
            float[][] newSoup = new float[SIZE][SIZE];
            float[][] newPot = new float[SIZE][SIZE];
            float[][] newFlame = new float[SIZE][SIZE];
            computeNextState(newSoup, newPot, newFlame);
            System.arraycopy(newSoup, 0, soupHeat, 0, SIZE);
            System.arraycopy(newPot, 0, potHeat, 0, SIZE);
            System.arraycopy(newFlame, 0, flameHeat, 0, SIZE);
        }

        /** Computes the next state for all three layers across the full grid.
         *
         * @param newSoup  destination for soup heat values
         * @param newPot   destination for pot heat values
         * @param newFlame accumulator for flame heat values
         */
        private void computeNextState(float[][] newSoup, float[][] newPot, float[][] newFlame) {
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    newSoup[y][x] = computeSoupHeat(x, y);
                    newPot[y][x] = computePotHeat(x, y);
                    computeFlameHeat(x, y, newFlame);
                }
            }
        }

        /**
         * Computes new soup heat for a cell by blending with neighbors and injecting pot heat.
         *
         * @param x the cell x coordinate
         * @param y the cell y coordinate
         * @return the new soup heat value
         */
        private float computeSoupHeat(int x, int y) {
            float neighborAvg = computeNeighborAverage(x, y);
            float blend = Math.min(MAX_VISCOSITY_BLEND, params.viscosity() * VISCOSITY_SCALE);
            float smoothed = soupHeat[y][x] * blend + neighborAvg * (1.0f - blend);
            float soupDecay = SOUP_DECAY;
            return smoothed * soupDecay + potHeat[y][x] * params.potInfluence();
        }

        /**
         * Computes the average soup heat of neighboring cells within the reach radius.
         *
         * @param x the cell x coordinate
         * @param y the cell y coordinate
         * @return the neighbor-averaged heat value
         */
        private float computeNeighborAverage(int x, int y) {
            float neighborSum = 0;
            int neighborCount = 0;
            int reach = params.neighborhoodReach();
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (dx == 0 && dy == 0) { continue; }
                    neighborSum += soupHeat[(y + dy + SIZE) % SIZE][(x + dx + SIZE) % SIZE];
                    neighborCount++;
                }
            }
            return neighborSum / neighborCount;
        }

        /**
         * Computes new pot heat for a cell: accumulates from flame with decay.
         *
         * @param x the cell x coordinate
         * @param y the cell y coordinate
         * @return the new pot heat value
         */
        private float computePotHeat(int x, int y) {
            float potDecay = POT_DECAY;
            return Math.max(0, potHeat[y][x] * potDecay + flameHeat[y][x] * params.potHeatRate());
        }

        /**
         * Computes flame heat with random ignition and cluster spread. Writes into newFlame accumulator.
         *
         * @param x        the cell x coordinate
         * @param y        the cell y coordinate
         * @param newFlame the accumulator for next-tick flame values
         */
        private void computeFlameHeat(int x, int y, float[][] newFlame) {
            float flame = flameHeat[y][x] - params.decayRate();
            if (random.nextFloat() < params.ignitionChance()) {
                flame = params.ignitionStrength();
                spreadIgnitionToNeighbors(x, y, newFlame);
            }
            newFlame[y][x] = Math.max(newFlame[y][x], Math.max(0, flame));
        }

        /**
         * Spreads ignition to the 3x3 neighborhood with cardinal/diagonal falloff.
         *
         * @param x        the ignition source x coordinate
         * @param y        the ignition source y coordinate
         * @param newFlame the accumulator for next-tick flame values
         */
        private void spreadIgnitionToNeighbors(int x, int y, float[][] newFlame) {
            for (int dy = NEIGHBOR_NEG; dy <= 1; dy++) {
                for (int dx = NEIGHBOR_NEG; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) { continue; }
                    int nx = (x + dx + SIZE) % SIZE;
                    int ny = (y + dy + SIZE) % SIZE;
                    float falloff = (dx == 0 || dy == 0) ? CARDINAL_FALLOFF : DIAGONAL_FALLOFF;
                    newFlame[ny][nx] = Math.max(newFlame[ny][nx], params.ignitionStrength() * falloff);
                }
            }
        }

        /**
         * Returns the raw soup heat at the given cell.
         *
         * @param x the cell x coordinate
         * @param y the cell y coordinate
         * @return the raw soup heat value
         */
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

        /**
         * Creates a palette from (position, hexColor) pairs. Hex format: "#RRGGBB" or "#RRGGBBAA".
         *
         * @param args alternating (float position, String hex) pairs
         * @return the constructed palette
         */
        static Palette of(Object... args) {
            if (args.length % PALETTE_PAIR_SIZE != 0) { throw new IllegalArgumentException(ERR_PALETTE_PAIRS); }
            int stops = args.length / PALETTE_PAIR_SIZE;
            float[] positions = new float[stops];
            int[][] colors = new int[stops][RGBA_COMPONENTS];
            for (int i = 0; i < stops; i++) {
                positions[i] = ((Number) args[i * PALETTE_PAIR_SIZE]).floatValue();
                colors[i] = parseHex((String) args[i * PALETTE_PAIR_SIZE + 1]);
            }
            return new Palette(positions, colors);
        }

        /**
         * Creates a palette from a list of deserialized palette stops.
         *
         * @param stops the palette stop list
         * @return the constructed palette
         */
        static Palette fromStops(List<PaletteStop> stops) {
            float[] positions = new float[stops.size()];
            int[][] colors = new int[stops.size()][RGBA_COMPONENTS];
            for (int i = 0; i < stops.size(); i++) {
                positions[i] = stops.get(i).position();
                colors[i] = parseHex(stops.get(i).color());
            }
            return new Palette(positions, colors);
        }

        /**
         * Parses "#RRGGBB" or "#RRGGBBAA" into {r, g, b, a}.
         *
         * @param hex the hex color string
         * @return four-element RGBA array
         */
        private static int[] parseHex(String hex) {
            String h = hex.startsWith(HEX_PREFIX) ? hex.substring(1) : hex;
            int r = Integer.parseInt(h.substring(HEX_RED_START, HEX_RED_END), HEX_RADIX);
            int g = Integer.parseInt(h.substring(HEX_GREEN_START, HEX_GREEN_END), HEX_RADIX);
            int b = Integer.parseInt(h.substring(HEX_BLUE_START, HEX_BLUE_END), HEX_RADIX);
            int a = h.length() >= HEX_WITH_ALPHA_LENGTH ? Integer.parseInt(h.substring(HEX_ALPHA_START, HEX_ALPHA_END), HEX_RADIX) : DEFAULT_ALPHA;
            return new int[]{r, g, b, a};
        }

        /**
         * Samples the palette at position t (0.0-1.0), interpolating between surrounding stops.
         *
         * @param t the normalized position to sample
         * @return the interpolated ARGB color
         */
        int sample(float t) {
            float clamped = Math.min(1.0f, Math.max(0, t));
            if (clamped <= positions[0]) { return packColor(colors[0]); }
            if (clamped >= positions[positions.length - 1]) { return packColor(colors[colors.length - 1]); }
            return interpolateStop(clamped);
        }

        /** Finds the surrounding stops and interpolates between them.
         *
         * @param t the clamped position to sample
         * @return the interpolated ARGB color
         */
        private int interpolateStop(float t) {
            for (int i = 0; i < positions.length - 1; i++) {
                if (t >= positions[i] && t <= positions[i + 1]) {
                    float local = (t - positions[i]) / (positions[i + 1] - positions[i]);
                    return lerpColor(colors[i], colors[i + 1], local);
                }
            }
            return packColor(colors[colors.length - 1]);
        }

        /**
         * Linearly interpolates between two RGBA color arrays.
         *
         * @param a the start color
         * @param b the end color
         * @param t the interpolation factor (0.0 to 1.0)
         * @return the interpolated ARGB int
         */
        private static int lerpColor(int[] a, int[] b, float t) {
            int r = (int) (a[IDX_R] + (b[IDX_R] - a[IDX_R]) * t);
            int g = (int) (a[IDX_G] + (b[IDX_G] - a[IDX_G]) * t);
            int blue = (int) (a[IDX_B] + (b[IDX_B] - a[IDX_B]) * t);
            int alpha = (int) (a[IDX_A] + (b[IDX_A] - a[IDX_A]) * t);
            return (alpha << ALPHA_SHIFT) | (r << RED_SHIFT) | (g << GREEN_SHIFT) | blue;
        }

        /**
         * Packs an {r, g, b, a} array into an ARGB int.
         *
         * @param c the RGBA color array
         * @return the packed ARGB int
         */
        private static int packColor(int[] c) {
            return (c[IDX_A] << ALPHA_SHIFT) | (c[IDX_R] << RED_SHIFT) | (c[IDX_G] << GREEN_SHIFT) | c[IDX_B];
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
        /**
         * Converts the deserialized JSON form into a full GooFluidType with a built Palette.
         *
         * @return the complete fluid type definition
         */
        GooFluidType toFluidType() {
            return new GooFluidType(id, seed, genParams, frametime, shadowHue, Palette.fromStops(palette));
        }
    }
}
