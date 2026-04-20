package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: syncs the loaded ability definitions so the
 * client radial menu knows what abilities exist per goo type. Sends only
 * the metadata needed for display (id, type, name, order), not the full
 * behavior configuration.
 *
 * @param entries the list of ability descriptors
 */
public record AbilitySyncPayload(List<Entry> entries) implements CustomPacketPayload {

    /** Payload type ID for registration. */
    public static final Type<AbilitySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ability_sync"));

    /** Stream codec for encoding/decoding. */
    public static final StreamCodec<FriendlyByteBuf, AbilitySyncPayload> STREAM_CODEC =
            StreamCodec.of(AbilitySyncPayload::encode, AbilitySyncPayload::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Builds the sync payload from the current server ability registry.
     *
     * @return the payload with all loaded abilities
     */
    public static AbilitySyncPayload fromRegistry() {
        List<Entry> entries = new ArrayList<>();
        for (GooType type : GooType.values()) {
            for (AbilityDefinition def : AbilityRegistry.getAbilitiesForType(type)) {
                entries.add(new Entry(def.id().toString(), type.getId(),
                        def.displayName(), def.icon(), def.order(), def.tags()));
            }
        }
        return new AbilitySyncPayload(entries);
    }

    private static void encode(FriendlyByteBuf buf, AbilitySyncPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeUtf(e.abilityId);
            buf.writeUtf(e.gooTypeId);
            buf.writeUtf(e.displayName);
            buf.writeUtf(e.icon);
            buf.writeVarInt(e.order);
            encodeTags(buf, e.tags);
        }
    }

    private static void encodeTags(FriendlyByteBuf buf, List<String> tags) {
        buf.writeVarInt(tags.size());
        for (String tag : tags) { buf.writeUtf(tag); }
    }

    private static AbilitySyncPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), decodeTags(buf)));
        }
        return new AbilitySyncPayload(entries);
    }

    private static List<String> decodeTags(FriendlyByteBuf buf) {
        int tagCount = buf.readVarInt();
        List<String> tags = new ArrayList<>(tagCount);
        for (int j = 0; j < tagCount; j++) { tags.add(buf.readUtf()); }
        return List.copyOf(tags);
    }

    /**
     * A lightweight ability descriptor for client display.
     *
     * @param abilityId   the ability resource id string
     * @param gooTypeId   the goo type id string
     * @param displayName the translation key
     * @param icon        the icon texture path override (empty for convention path)
     * @param order       the sort order within the type
     * @param tags        categorical tags for targeting and display
     */
    public record Entry(String abilityId, String gooTypeId, String displayName,
            String icon, int order, List<String> tags) {}
}
