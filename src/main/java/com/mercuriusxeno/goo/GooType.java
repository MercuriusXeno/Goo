package com.mercuriusxeno.goo;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * The types of goo that make up everything in the world.
 */
public enum GooType implements StringRepresentable {
    AEON("aeon", 0xDAA520, 8, 0.5f),
    BLAZE("blaze", 0xFF6600, 15, 0.5f),
    CRYSTAL("crystal", 0x4FC1E9, 8, 0.5f),
    ENDER("ender", 0x2E8B8B, 8, 0.5f),
    FROST("frost", 0xADD8E6, 8, 0.5f),
    GLOW("glow", 0xFFD700, 15, 0.5f),
    HEX("hex", 0x5B4BA0, 8, 0.5f),
    LEAF("leaf", 0x7EC850, 4, 0.5f),
    METAL("metal", 0xC0C0C0, 4, 0.5f),
    NETHER("nether", 0x8B0000, 4, 0.5f),
    PULSE("pulse", 0xCC0000, 8, 0.6f),
    ROCK("rock", 0xC2A868, 4, 0.5f),
    SHROOM("shroom", 0x8E44AD, 4, 0.5f),
    TYPHOON("typhoon", 0xD5F5E3, 4, 0.5f),
    UNSTABLE("unstable", 0x39FF14, 15, 0.55f),
    VITAL("vital", 0xE74C3C, 4, 0.5f);

    /**
     * Codec that serializes a GooType as its string id.
     */
    public static final com.mojang.serialization.Codec<GooType> CODEC =
            com.mojang.serialization.Codec.STRING.xmap(
                    id -> {
                        GooType t = fromId(id);
                        if (t == null) {
                            throw new IllegalArgumentException("Unknown goo type: " + id);
                        }
                        return t;
                    },
                    GooType::getId);
    /**
     * Translation key prefix for goo type display names.
     */
    private static final String TRANSLATION_PREFIX = "goo.type.";
    private final String id;
    private final int color;
    /** Peak block-light emission this type contributes when fully present.
     * 0 means non-emissive. Capped at vanilla 15 ceiling. */
    private final int peakLight;
    /** Fill fraction (0..1) at which this type's contribution reaches
     * {@link #peakLight}. Lower values = ramps to peak with less goo.
     * Ignored when {@link #peakLight} is 0. */
    private final float saturationFill;

    GooType(String id, int color, int peakLight, float saturationFill) {
        this.id = id;
        this.color = color;
        this.peakLight = peakLight;
        this.saturationFill = saturationFill;
    }

    /** @return peak block-light emission for this type, capped at vanilla 15. */
    public int peakLight() {
        return peakLight;
    }

    /** @return fill fraction at which this type reaches {@link #peakLight()}. */
    public float saturationFill() {
        return saturationFill;
    }

    @org.jspecify.annotations.Nullable
    public static GooType fromId(String id) {
        for (GooType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
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

    /**
     * Looks up a GooType by its string id. Returns null if no match.
     *
     * @return the matching GooType, or null if not found
     */

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
}
