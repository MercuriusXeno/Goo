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
    ENDER("ender", 0x00CED1),
    FROST("frost", 0xADD8E6),
    GLOW("glow", 0xFFD700),
    HEX("hex", 0x2C3E50),
    LEAF("leaf", 0x7EC850),
    METAL("metal", 0xC0C0C0),
    NETHER("nether", 0x8B0000),
    PULSE("pulse", 0xCC0000),
    ROCK("rock", 0x808080),
    SHROOM("shroom", 0x8E44AD),
    TYPHOON("typhoon", 0xD5F5E3),
    VITAL("vital", 0xE74C3C);

    private final String id;
    private final int color;

    GooType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    @Override
    public @NonNull String getSerializedName() {
        return id;
    }

    public String getTranslationKey() {
        return "goo.type." + id;
    }

    /**
     * Looks up a GooType by its string id. Returns null if no match.
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
