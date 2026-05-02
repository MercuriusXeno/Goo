package com.mercuriusxeno.goo.tools;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.ARGB;
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
 * <p>
 * Three heat layers (soupHeat, potHeat, flameHeat) simulate fluid dynamics.
 * Per-type parameters control viscosity, turbulence, color palettes, and frame speed.
 * <p>
 * Run via main() - outputs to src/main/resources/assets/goo/textures/
 */
@SuppressWarnings("PMD.SystemPrintln") // standalone CLI tool; no logger needed
public final class FluidTextureGenerator {

    static final int SIZE = 16;
    static final int FRAMES = 32;
    static final int WARMUP = 60;
    /**
     * Fully transparent ARGB pixel.
     */
    static final int TRANSPARENT = 0x00000000;
    private static final Path OUTPUT_ROOT = Path.of("src/main/resources/assets/goo/textures");
    static final Path ITEM_DIR = OUTPUT_ROOT.resolve("item");

    static final Path BLOB_MASK_TINY_PATH = ITEM_DIR.resolve("goo_blob_mask_tiny.png");
    static final Path BLOB_MASK_SMALL_PATH = ITEM_DIR.resolve("goo_blob_mask_small.png");
    static final Path BLOB_MASK_PATH = ITEM_DIR.resolve("goo_blob_mask.png");
    static final Path BLOB_MASK_LARGE_PATH = ITEM_DIR.resolve("goo_blob_mask_large.png");
    private static final Path FLUID_DIR = OUTPUT_ROOT.resolve("fluid");
    private static final Path FLUID_TYPES_JSON = Path.of("src/main/resources/data/goo/goo_fluid_types.json");

    // ── ARGB bit-shift and mask constants ──
    /**
     * Minimum heat range before fallback to 1.0 to avoid division by near-zero.
     */
    private static final float MIN_HEAT_RANGE = 0.001f;
    // ── Palette hex parsing constants ──
    /**
     * Start index for red hex digits.
     */
    private static final int HEX_RED_START = 0;
    /**
     * End index for red hex digits.
     */
    private static final int HEX_RED_END = 2;
    /**
     * Start index for green hex digits.
     */
    private static final int HEX_GREEN_START = 2;
    /**
     * End index for green hex digits.
     */
    private static final int HEX_GREEN_END = 4;
    /**
     * Start index for blue hex digits.
     */
    private static final int HEX_BLUE_START = 4;
    /**
     * End index for blue hex digits.
     */
    private static final int HEX_BLUE_END = 6;
    /**
     * Start index for alpha hex digits.
     */
    private static final int HEX_ALPHA_START = 6;
    /**
     * End index for alpha hex digits.
     */
    private static final int HEX_ALPHA_END = 8;
    /**
     * Minimum hex string length that includes an alpha channel.
     */
    private static final int HEX_WITH_ALPHA_LENGTH = 8;
    /**
     * Default alpha for hex colors without explicit alpha.
     */
    private static final int DEFAULT_ALPHA = 255;
    /**
     * Hex radix for color parsing.
     */
    private static final int HEX_RADIX = 16;

    // ── Palette color array indices ──
    /**
     * Index for red in RGBA color arrays.
     */
    private static final int IDX_R = 0;
    /**
     * Index for green in RGBA color arrays.
     */
    private static final int IDX_G = 1;
    /**
     * Index for blue in RGBA color arrays.
     */
    private static final int IDX_B = 2;
    /**
     * Index for alpha in RGBA color arrays.
     */
    private static final int IDX_A = 3;
    /**
     * Number of components in an RGBA color array.
     */
    private static final int RGBA_COMPONENTS = 4;
    /**
     * Required pair size for palette varargs (position + hex).
     */
    private static final int PALETTE_PAIR_SIZE = 2;

    // ── CA engine constants ──
    /**
     * Maximum viscosity blend factor for soup heat smoothing.
     */
    private static final float MAX_VISCOSITY_BLEND = 0.7f;
    /**
     * Viscosity scaling factor for blend computation.
     */
    private static final float VISCOSITY_SCALE = 0.28f;
    /**
     * Soup heat decay rate per tick.
     */
    private static final float SOUP_DECAY = 0.95f;
    /**
     * Pot heat decay rate per tick.
     */
    private static final float POT_DECAY = 0.9f;
    /**
     * Cardinal neighbor ignition falloff (adjacent on axis).
     */
    private static final float CARDINAL_FALLOFF = 0.6f;
    /**
     * Diagonal neighbor ignition falloff.
     */
    private static final float DIAGONAL_FALLOFF = 0.35f;
    /**
     * Console message prefix for generation progress.
     */
    private static final String MSG_GENERATING = "Generating: ";
    /**
     * Console message prefix for completion summary.
     */
    private static final String MSG_DONE_PREFIX = "Done. Generated ";
    /**
     * Console message suffix for completion summary.
     */
    private static final String MSG_DONE_SUFFIX = " fluid textures and blob bases.";
    /**
     * File suffix for fluid PNG textures.
     */
    private static final String SUFFIX_FLUID_PNG = "_fluid.png";
    /**
     * Image format for PNG output.
     */
    private static final String FORMAT_PNG = "PNG";
    /**
     * File suffix for fluid mcmeta sidecar.
     */
    private static final String SUFFIX_FLUID_MCMETA = "_fluid.png.mcmeta";
    /**
     * Console format for heat range debug output.
     */
    private static final String FMT_HEAT_RANGE = "  %s: heat range %.4f-%.4f (spread %.4f)%n";
    /**
     * Hex color prefix character.
     */
    private static final String HEX_PREFIX = "#";
    /**
     * Negative direction for neighbor iteration.
     */
    private static final int NEIGHBOR_NEG = -1;
    /**
     * Error message for invalid palette varargs.
     */
    private static final String ERR_PALETTE_PAIRS = "Args must be pairs of (float position, String hex)";

    private FluidTextureGenerator() {
    }

    /**
     * Generates all goo fluid and blob textures from the JSON type definitions.
     *
     * @param args unused
     * @throws IOException if texture files cannot be read or written
     */
    static void main(String[] args) throws IOException {
        Files.createDirectories(FLUID_DIR);
        Files.createDirectories(ITEM_DIR);
        List<GooFluidType> types = loadFluidTypes();
        generateAll(types);
        System.out.println(MSG_DONE_PREFIX + types.size() + MSG_DONE_SUFFIX);
    }

    /**
     * Generates fluid textures and blob bases for all types with progress output.
     *
     * @param types the list of goo fluid type definitions
     * @throws IOException if texture files cannot be written
     */
    private static void generateAll(List<GooFluidType> types) throws IOException {
        for (GooFluidType type : types) {
            System.out.println(MSG_GENERATING + type.id());
            generateFluidTexture(type);
            BlobTextureRenderer.generateBlobBase(type);
        }
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
        Type listType = new TypeToken<List<GooFluidTypeJson>>() {
        }.getType();
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

    /**
     * Advances the CA the specified number of ticks to reach a stable state.
     *
     * @param ca    the cellular automata engine
     * @param ticks the number of warmup ticks
     */
    static void warmup(FluidCA ca, int ticks) {
        for (int i = 0; i < ticks; i++) {
            ca.tick();
        }
    }

    /**
     * Renders, writes, and logs the fluid sprite strip.
     *
     * @param type       the goo fluid type definition
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @throws IOException if files cannot be written
     */
    private static void writeFluidStrip(GooFluidType type, float[][] heatFrames,
                                        float... heatRange) throws IOException {
        BufferedImage strip = renderFluidStrip(heatFrames, heatRange, type.palette());
        ImageIO.write(strip, FORMAT_PNG, FLUID_DIR.resolve(type.id() + SUFFIX_FLUID_PNG).toFile());
        TextureMcmetaWriter.writeFluidMcmeta(type.frametime(), FLUID_DIR, type.id() + SUFFIX_FLUID_MCMETA);
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

    /**
     * Renders a single frame of the fluid strip into the composite image.
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
     * Captures FRAMES of raw heat values from the CA.
     *
     * @param ca the cellular automata engine
     * @return array of heat values indexed by [frame][pixel]
     */
    static float[][] captureHeatFrames(FluidCA ca) {
        float[][] heatFrames = new float[FRAMES][SIZE * SIZE];
        for (int frame = 0; frame < FRAMES; frame++) {
            ca.tick();
            captureOneFrame(ca, heatFrames[frame]);
        }
        return heatFrames;
    }

    /**
     * Captures the CA's current heat state into a flat pixel array.
     *
     * @param ca   the cellular automata engine
     * @param dest the destination array (SIZE*SIZE elements)
     */
    private static void captureOneFrame(FluidCA ca, float... dest) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                dest[y * SIZE + x] = ca.getRawHeat(x, y);
            }
        }
    }

    /**
     * Returns {min, range} across all frames.
     *
     * @param heatFrames the raw heat values per frame
     * @return two-element array: [minHeat, range]
     */
    static float[] findHeatRange(float[]... heatFrames) {
        float minHeat = Float.MAX_VALUE;
        float maxHeat = Float.MIN_VALUE;
        for (float[] frame : heatFrames) {
            minHeat = Math.min(minHeat, frameMin(frame));
            maxHeat = Math.max(maxHeat, frameMax(frame));
        }
        float range = maxHeat - minHeat;
        if (range < MIN_HEAT_RANGE) {
            range = 1.0f;
        }
        return new float[]{minHeat, range};
    }

    /**
     * Returns the minimum value in a single heat frame.
     *
     * @param frame the heat values for one frame
     * @return the minimum heat value
     */
    private static float frameMin(float... frame) {
        float min = Float.MAX_VALUE;
        for (float h : frame) {
            min = Math.min(min, h);
        }
        return min;
    }

    /**
     * Returns the maximum value in a single heat frame.
     *
     * @param frame the heat values for one frame
     * @return the maximum heat value
     */
    private static float frameMax(float... frame) {
        float max = Float.MIN_VALUE;
        for (float h : frame) {
            max = Math.max(max, h);
        }
        return max;
    }

    /**
     * Clamps and normalizes a heat value to 0.0-1.0.
     *
     * @param heat    the raw heat value
     * @param minHeat the minimum heat across all frames
     * @param range   the heat range across all frames
     * @return the normalized heat value
     */
    static float normalizeHeat(float heat, float minHeat, float range) {
        return Math.min(1.0f, Math.max(0, (heat - minHeat) / range));
    }


    // ---- Cellular Automata Engine ----

    /**
     * Cellular automata simulation for fluid dynamics.
     * Three heat layers (soup, pot, flame) interact to produce organic fluid motion.
     */
    static class FluidCA {
        /**
         * Multiplier to convert radius to diameter (radius * 2 + 1 = side length).
         */
        private static final int DIAMETER_FACTOR = 2;
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

        /**
         * Advances the simulation by one tick, updating all three heat layers.
         */
        void tick() {
            float[][] newSoup = new float[SIZE][SIZE];
            float[][] newPot = new float[SIZE][SIZE];
            float[][] newFlame = new float[SIZE][SIZE];
            computeNextState(newSoup, newPot, newFlame);
            System.arraycopy(newSoup, 0, soupHeat, 0, SIZE);
            System.arraycopy(newPot, 0, potHeat, 0, SIZE);
            System.arraycopy(newFlame, 0, flameHeat, 0, SIZE);
        }

        /**
         * Computes the next state for all three layers across the full grid.
         *
         * @param newSoup  destination for soup heat values
         * @param newPot   destination for pot heat values
         * @param newFlame accumulator for flame heat values
         */
        private void computeNextState(float[][] newSoup, float[][] newPot, float[]... newFlame) {
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
            int reach = params.neighborhoodReach();
            float neighborSum = sumNeighborHeat(x, y, reach);
            int side = DIAMETER_FACTOR * reach + 1;
            int neighborCount = side * side - 1;
            return neighborSum / neighborCount;
        }

        /**
         * Sums soup heat of all neighbors within the reach radius, excluding the center cell.
         *
         * @param x     the cell x coordinate
         * @param y     the cell y coordinate
         * @param reach the neighborhood reach radius
         * @return the total neighbor heat
         */
        private float sumNeighborHeat(int x, int y, int reach) {
            float sum = 0;
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    sum += soupHeat[(y + dy + SIZE) % SIZE][(x + dx + SIZE) % SIZE];
                }
            }
            return sum;
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
        private void computeFlameHeat(int x, int y, float[]... newFlame) {
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
        private void spreadIgnitionToNeighbors(int x, int y, float[]... newFlame) {
            for (int dy = NEIGHBOR_NEG; dy <= 1; dy++) {
                for (int dx = NEIGHBOR_NEG; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    applyIgnitionFalloff(x + dx, y + dy, dx, dy, newFlame);
                }
            }
        }

        /**
         * Applies ignition falloff to a single neighbor cell, wrapping coordinates toroidally.
         *
         * @param rawX     the unwrapped neighbor x coordinate
         * @param rawY     the unwrapped neighbor y coordinate
         * @param dx       the x offset from the source (for cardinal/diagonal detection)
         * @param dy       the y offset from the source (for cardinal/diagonal detection)
         * @param newFlame the accumulator for next-tick flame values
         */
        private void applyIgnitionFalloff(int rawX, int rawY, int dx, int dy, float[]... newFlame) {
            int nx = (rawX + SIZE) % SIZE;
            int ny = (rawY + SIZE) % SIZE;
            float falloff = (dx == 0 || dy == 0) ? CARDINAL_FALLOFF : DIAGONAL_FALLOFF;
            newFlame[ny][nx] = Math.max(newFlame[ny][nx], params.ignitionStrength() * falloff);
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
            if (args.length % PALETTE_PAIR_SIZE != 0) {
                throw new IllegalArgumentException(ERR_PALETTE_PAIRS);
            }
            int stops = args.length / PALETTE_PAIR_SIZE;
            float[] positions = new float[stops];
            int[][] colors = new int[stops][RGBA_COMPONENTS];
            populateStops(args, stops, positions, colors);
            return new Palette(positions, colors);
        }

        /**
         * Parses position/hex pairs from the varargs into parallel arrays.
         *
         * @param args      the alternating (float, String) pairs
         * @param stops     the number of stops
         * @param positions the destination positions array
         * @param colors    the destination colors array
         */
        private static void populateStops(Object[] args, int stops,
                                          float[] positions, int[]... colors) {
            for (int i = 0; i < stops; i++) {
                positions[i] = ((Number) args[i * PALETTE_PAIR_SIZE]).floatValue();
                colors[i] = parseHex((String) args[i * PALETTE_PAIR_SIZE + 1]);
            }
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
            return ARGB.color(alpha, r, g, blue);
        }

        /**
         * Packs an {r, g, b, a} array into an ARGB int.
         *
         * @param c the RGBA color array
         * @return the packed ARGB int
         */
        private static int packColor(int... c) {
            return ARGB.color(c[IDX_A], c[IDX_R], c[IDX_G], c[IDX_B]);
        }

        /**
         * Samples the palette at position t (0.0-1.0), interpolating between surrounding stops.
         *
         * @param t the normalized position to sample
         * @return the interpolated ARGB color
         */
        int sample(float t) {
            float clamped = Math.min(1.0f, Math.max(0, t));
            if (clamped <= positions[0]) {
                return packColor(colors[0]);
            }
            if (clamped >= positions[positions.length - 1]) {
                return packColor(colors[colors.length - 1]);
            }
            return interpolateStop(clamped);
        }

        /**
         * Finds the surrounding stops and interpolates between them.
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
    }

    // ---- Per-Type Data Records ----

    /**
     * Cellular automata tuning parameters that control fluid simulation behavior.
     */
    record GooFluidGenParams(
            float viscosity,
            float decayRate,
            float ignitionChance,
            float ignitionStrength,
            float potHeatRate,
            float potInfluence,
            int neighborhoodReach
    ) {
    }

    /**
     * A single color stop in a gradient palette, used for JSON deserialization.
     */
    record PaletteStop(float position, String color) {
    }

    /**
     * Complete definition of a goo fluid type, including CA params, palette, and rendering hints.
     */
    record GooFluidType(String id, long seed, GooFluidGenParams genParams, int frametime, float shadowHue,
                        Palette palette) {
    }

    /**
     * JSON-shaped intermediate for Gson deserialization, converted to GooFluidType via toFluidType().
     */
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
