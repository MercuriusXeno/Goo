package com.mercuriusxeno.goo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads per-goo-type RGB colors from a JSON config file with three
 * channels: wheel (radial menu), highlight (aim arc, ghost fill,
 * fade walls), and edge (wireframe contours). On first run, copies
 * the bundled default to the config directory.
 */
public final class GooColors {

    /**
     * Config file name.
     */
    private static final String FILE_NAME = "goo_colors.json";
    /**
     * Bundled default resource path.
     */
    private static final String BUNDLED_PATH = "/assets/goo/" + FILE_NAME;
    /**
     * Hex color radix.
     */
    private static final int HEX_RADIX = 16;
    /**
     * JSON key for the wheel (radial menu) color channel.
     */
    private static final String KEY_WHEEL = "wheel";
    /**
     * JSON key for the bright/hover color channel.
     */
    private static final String KEY_BRIGHT = "bright";
    /**
     * JSON key for the highlight color channel.
     */
    private static final String KEY_HIGHLIGHT = "highlight";
    /**
     * JSON key for the edge/contour color channel.
     */
    private static final String KEY_EDGE = "edge";
    /**
     * Log message when copying the default config fails.
     */
    private static final String LOG_COPY_FAILED = "Failed to copy default goo_colors.json";
    /**
     * Log message when reading the config fails.
     */
    private static final String LOG_READ_FAILED = "Failed to read goo_colors.json, using defaults";
    /**
     * Log message when a hex color string is invalid.
     */
    private static final String LOG_INVALID_COLOR = "Invalid color '{}' for goo type {}";

    private static final Map<GooType, ColorSet> COLORS = new EnumMap<>(GooType.class);

    private GooColors() {
    }

    /**
     * Loads colors from the config directory, copying the bundled default
     * if the file doesn't exist yet. Call once during mod init.
     *
     * @param configDir the mod config directory
     */
    public static void load(Path configDir) {
        Path configFile = configDir.resolve(FILE_NAME);
        copyDefaultIfMissing(configFile);
        Map<String, Map<String, String>> raw = readJson(configFile);
        applyColors(raw);
    }

    /**
     * Returns the wheel (radial menu) color for the given goo type.
     *
     * @param type the goo type
     * @return the RGB color int
     */
    public static int wheel(GooType type) {
        ColorSet set = COLORS.get(type);
        return set != null ? set.wheel : type.getDefaultColor();
    }

    /**
     * Returns the bright/hover color for the radial wheel.
     *
     * @param type the goo type
     * @return the RGB color int
     */
    public static int bright(GooType type) {
        ColorSet set = COLORS.get(type);
        return set != null ? set.bright : type.getDefaultColor();
    }

    /**
     * Returns the highlight color (aim arc, ghost fill, fade walls).
     *
     * @param type the goo type
     * @return the RGB color int
     */
    public static int highlight(GooType type) {
        ColorSet set = COLORS.get(type);
        return set != null ? set.highlight : type.getDefaultColor();
    }

    /**
     * Returns the edge/contour color (wireframes, outlines).
     *
     * @param type the goo type
     * @return the RGB color int
     */
    public static int edge(GooType type) {
        ColorSet set = COLORS.get(type);
        return set != null ? set.edge : type.getDefaultColor();
    }

    /**
     * Returns the default/legacy color. Falls back to the highlight
     * channel, which is the most common use.
     *
     * @param type the goo type
     * @return the RGB color int
     */
    public static int get(GooType type) {
        return highlight(type);
    }

    /**
     * Copies the bundled default to the config dir if the file is missing.
     *
     * @param configFile the target config file path
     */
    private static void copyDefaultIfMissing(Path configFile) {
        if (Files.exists(configFile)) {
            return;
        }
        try (InputStream in = GooColors.class.getResourceAsStream(BUNDLED_PATH)) {
            if (in == null) {
                return;
            }
            Files.createDirectories(configFile.getParent());
            Files.copy(in, configFile);
        } catch (IOException e) {
            Goo.LOGGER.warn(LOG_COPY_FAILED, e);
        }
    }

    /**
     * Reads the JSON file into a nested map.
     *
     * @param configFile the config file path
     * @return the parsed map, or empty on failure
     */
    private static Map<String, Map<String, String>> readJson(Path configFile) {
        Type mapType = new TypeToken<Map<String, Map<String, String>>>() {
        }.getType();
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            Map<String, Map<String, String>> result = new Gson().fromJson(reader, mapType);
            return result != null ? result : Map.of();
        } catch (IOException e) {
            Goo.LOGGER.warn(LOG_READ_FAILED, e);
            return Map.of();
        }
    }

    /**
     * Parses hex strings and populates the color map.
     *
     * @param raw the raw nested map from JSON
     */
    private static void applyColors(Map<String, Map<String, String>> raw) {
        for (GooType type : GooType.values()) {
            Map<String, String> entry = raw.get(type.getId());
            int fallback = type.getDefaultColor();
            if (entry != null) {
                int wheelColor = parseHex(entry.get(KEY_WHEEL), fallback, type);
                COLORS.put(type, new ColorSet(
                        wheelColor,
                        parseHex(entry.get(KEY_BRIGHT), wheelColor, type),
                        parseHex(entry.get(KEY_HIGHLIGHT), fallback, type),
                        parseHex(entry.get(KEY_EDGE), fallback, type)));
            } else {
                COLORS.put(type, new ColorSet(fallback, fallback, fallback, fallback));
            }
        }
    }

    /**
     * Parses a hex color string, falling back on failure.
     *
     * @param hex      the hex string, or null
     * @param fallback the fallback color
     * @param type     the goo type (for logging)
     * @return the parsed color, or fallback
     */
    private static int parseHex(String hex, int fallback, GooType type) {
        if (hex == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(hex, HEX_RADIX);
        } catch (NumberFormatException e) {
            if (Goo.LOGGER.isWarnEnabled()) {
                Goo.LOGGER.warn(LOG_INVALID_COLOR, hex, type.getId());
            }
            return fallback;
        }
    }

    /**
     * Four-channel color set for one goo type.
     */
    private record ColorSet(int wheel, int bright, int highlight, int edge) {
    }
}
