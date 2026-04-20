package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;

/**
 * A single ability within a goo type's repertoire. Loaded from datapack
 * JSON under {@code data/<ns>/goo_abilities/}. Defines the behavior
 * composition, cost formula, chain parameters, and display metadata.
 *
 * @param id          the datapack resource identifier (from filename)
 * @param gooType     the goo type this ability belongs to
 * @param displayName the translation key for the ability name
 * @param icon        the texture path for the radial menu icon
 * @param order       sort order within the type's ability list
 * @param cost        the cost formula for this ability
 * @param chain       chain marker parameters (nullable for non-chain abilities)
 * @param behaviors   the composed behavior building blocks
 * @param tags        categorical tags (explosive, instant, trap, field-effect, etc.)
 */
public record AbilityDefinition(
        Identifier id,
        GooType gooType,
        String displayName,
        String icon,
        int order,
        AbilityCost cost,
        ChainConfig chain,
        List<BehaviorEntry> behaviors,
        List<String> tags
) {

    /** Placeholder id used during codec parsing; replaced by filename in the loader. */
    private static final Identifier PLACEHOLDER_ID = Identifier.withDefaultNamespace("unknown");

    /** Codec for the ability JSON. The id comes from the filename, not the JSON body. */
    public static final Codec<AbilityDefinition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            GooType.CODEC.fieldOf("gooType").forGetter(AbilityDefinition::gooType),
            Codec.STRING.fieldOf("displayName").forGetter(AbilityDefinition::displayName),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(AbilityDefinition::icon),
            Codec.INT.optionalFieldOf("order", 0).forGetter(AbilityDefinition::order),
            AbilityCost.CODEC.fieldOf("cost").forGetter(AbilityDefinition::cost),
            ChainConfig.CODEC.optionalFieldOf("chain", ChainConfig.DEFAULT).forGetter(AbilityDefinition::chain),
            BehaviorEntry.CODEC.listOf().fieldOf("behaviors").forGetter(AbilityDefinition::behaviors),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(AbilityDefinition::tags)
    ).apply(inst, (gooType, displayName, icon, order, cost, chain, behaviors, tags) ->
            new AbilityDefinition(PLACEHOLDER_ID, gooType, displayName, icon, order,
                    cost, chain, behaviors, tags)));

    /**
     * Returns a copy with the datapack resource id set.
     *
     * @param resourceId the resource identifier from the filename
     * @return the definition with id applied
     */
    public AbilityDefinition withId(Identifier resourceId) {
        return new AbilityDefinition(resourceId, gooType, displayName, icon, order,
                cost, chain, behaviors, tags);
    }

    /**
     * Returns true if this ability has the given tag.
     *
     * @param tag the tag to check
     * @return true if present
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Chain marker parameters for abilities that use the chain system.
     *
     * @param fuseTicks    fuse countdown (-1 for trigger-based)
     * @param maxStacks    maximum blob stacks
     * @param blobShape    cosmetic blob shape: "blob" (default) or "flat" (squished)
     * @param rangeFormula range formula name (constant, tunnel_depth, freeze_radius, etc.)
     * @param rangeValue   base value for constant range formulas
     */
    public record ChainConfig(
            int fuseTicks,
            int maxStacks,
            String blobShape,
            String rangeFormula,
            int rangeValue
    ) {
        /** Default blob shape. */
        public static final String SHAPE_BLOB = "blob";
        /** Squished blob shape. */
        public static final String SHAPE_FLAT = "flat";

        /** Default chain config for abilities that don't specify one. */
        static final ChainConfig DEFAULT = new ChainConfig(30, 1, SHAPE_BLOB, "constant", 1);

        static final Codec<ChainConfig> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.optionalFieldOf("fuseTicks", 30).forGetter(ChainConfig::fuseTicks),
                Codec.INT.optionalFieldOf("maxStacks", 1).forGetter(ChainConfig::maxStacks),
                Codec.STRING.optionalFieldOf("blobShape", SHAPE_BLOB).forGetter(ChainConfig::blobShape),
                Codec.STRING.optionalFieldOf("rangeFormula", "constant").forGetter(ChainConfig::rangeFormula),
                Codec.INT.optionalFieldOf("rangeValue", 1).forGetter(ChainConfig::rangeValue)
        ).apply(inst, ChainConfig::new));
    }

    /**
     * A single behavior building block with its parameters.
     * Parameters are stored as string key-value pairs; behavior factories
     * parse them into typed values (float, boolean, etc.).
     *
     * @param type   the behavior type name (explosion, break_area, etc.)
     * @param params the parameter map for the behavior factory
     */
    public record BehaviorEntry(String type, Map<String, String> params) {

        static final Codec<BehaviorEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("type").forGetter(BehaviorEntry::type),
                Codec.unboundedMap(Codec.STRING, Codec.STRING)
                        .optionalFieldOf("params", Map.of())
                        .forGetter(BehaviorEntry::params)
        ).apply(inst, BehaviorEntry::new));

        /**
         * Gets a float parameter, returning the default if absent or unparseable.
         *
         * @param key          the parameter name
         * @param defaultValue the fallback value
         * @return the parsed float
         */
        public float getFloat(String key, float defaultValue) {
            String v = params.get(key);
            if (v == null) { return defaultValue; }
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        /**
         * Gets a boolean parameter, returning the default if absent.
         *
         * @param key          the parameter name
         * @param defaultValue the fallback value
         * @return the parsed boolean
         */
        public boolean getBool(String key, boolean defaultValue) {
            String v = params.get(key);
            return v != null ? Boolean.parseBoolean(v) : defaultValue;
        }
    }
}
