package com.mercuriusxeno.goo;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The 15 types of goo that make up everything in the world.
 */
public enum GooType implements StringRepresentable {
    AEON("aeon", 0xDAA520),
    BLAZE("blaze", 0xFF6600),
    CRYSTAL("crystal", 0x4FC1E9),
    ENDER("ender", 0x2E8B8B),
    FROST("frost", 0xADD8E6),
    GLOW("glow", 0xFFD700),
    HEX("hex", 0x5B4BA0),
    LEAF("leaf", 0x7EC850),
    METAL("metal", 0xC0C0C0),
    NETHER("nether", 0x8B0000),
    PULSE("pulse", 0xCC0000),
    ROCK("rock", 0xC2A868),
    SHROOM("shroom", 0x8E44AD),
    TYPHOON("typhoon", 0xD5F5E3),
    VITAL("vital", 0xE74C3C);

    /** Translation key prefix for goo type display names. */
    private static final String TRANSLATION_PREFIX = "goo.type.";

    private final String id;
    private final int color;

    GooType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    /**
     * Returns the lowercase string identifier for this goo type.
     *
     * @return the goo type ID string
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the RGB color used for rendering this goo type.
     * Reads from the JSON color config, falling back to the hardcoded default.
     *
     * @return the RGB color int
     */
    public int getColor() {
        return GooColors.get(this);
    }

    /**
     * Returns the hardcoded default RGB color for this goo type.
     *
     * @return the default RGB color int
     */
    public int getDefaultColor() {
        return color;
    }

    @Override
    public @NonNull String getSerializedName() {
        return id;
    }

    /**
     * Returns the translation key for this goo type's display name.
     *
     * @return the translation key string
     */
    public String getTranslationKey() {
        return TRANSLATION_PREFIX + id;
    }

    /**
     * Looks up a GooType by its string id. Returns null if no match.
     *
     * @param id the string identifier to look up
     * @return the matching GooType, or null if not found
     */
    @org.jspecify.annotations.Nullable
    public static GooType fromId(String id) {
        for (GooType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
