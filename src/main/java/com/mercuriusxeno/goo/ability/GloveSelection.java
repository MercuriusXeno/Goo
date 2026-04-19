package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The player's current goo type + ability selection on a glove.
 * Replaces the plain string SELECTED_GOO_TYPE with a richer model
 * that tracks both which type and which specific ability is chosen.
 *
 * @param gooTypeId the selected goo type's string id (empty = none)
 * @param abilityId the selected ability's resource id string (empty = type-only, no specific ability)
 */
public record GloveSelection(String gooTypeId, String abilityId) {

    /** Empty string sentinel for unset fields. */
    private static final String NONE = "";

    /** Empty selection - no type, no ability. */
    public static final GloveSelection EMPTY = new GloveSelection(NONE, NONE);

    /** Persistent codec for data component storage. */
    public static final Codec<GloveSelection> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("gooTypeId", "").forGetter(GloveSelection::gooTypeId),
            Codec.STRING.optionalFieldOf("abilityId", "").forGetter(GloveSelection::abilityId)
    ).apply(inst, GloveSelection::new));

    /** Network codec for client-server sync. */
    public static final StreamCodec<ByteBuf, GloveSelection> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, GloveSelection::gooTypeId,
                    ByteBufCodecs.STRING_UTF8, GloveSelection::abilityId,
                    GloveSelection::new);

    /**
     * Creates a type-only selection (no specific ability chosen).
     *
     * @param type the goo type
     * @return a selection with just the type set
     */
    public static GloveSelection ofType(GooType type) {
        return new GloveSelection(type.getId(), NONE);
    }

    /**
     * Creates a full selection with type and ability.
     *
     * @param type      the goo type
     * @param abilityId the ability resource identifier
     * @return a selection with both type and ability
     */
    public static GloveSelection ofAbility(GooType type, Identifier abilityId) {
        return new GloveSelection(type.getId(), abilityId.toString());
    }

    /**
     * Resolves the goo type from the stored id.
     *
     * @return the GooType, or null if empty or unknown
     */
    public @Nullable GooType getGooType() {
        if (gooTypeId.isEmpty()) { return null; }
        return GooType.fromId(gooTypeId);
    }

    /**
     * Resolves the ability identifier from the stored string.
     *
     * @return the Identifier, or null if empty
     */
    public @Nullable Identifier getAbilityIdentifier() {
        if (abilityId.isEmpty()) { return null; }
        return Identifier.tryParse(abilityId);
    }

    /**
     * Returns true if a goo type is selected.
     *
     * @return true if type is set
     */
    public boolean hasType() {
        return !gooTypeId.isEmpty();
    }

    /**
     * Returns true if a specific ability is selected.
     *
     * @return true if ability is set
     */
    public boolean hasAbility() {
        return !abilityId.isEmpty();
    }

    /**
     * Returns true if nothing is selected.
     *
     * @return true if both type and ability are empty
     */
    public boolean isEmpty() {
        return gooTypeId.isEmpty();
    }
}
