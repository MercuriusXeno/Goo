package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side handler for the ability sync payload. Stores the ability
 * list in a client-accessible cache so the ability radial menu can
 * read it without server-side registry access.
 */
public final class AbilitySyncHandler {

    private static final String LOG_SYNCED = "Synced {} abilities from server";

    private static Map<GooType, List<ClientAbility>> byType = new EnumMap<>(GooType.class);

    private AbilitySyncHandler() {}

    /**
     * Handles the sync payload on the client thread.
     *
     * @param payload the sync payload
     * @param context the network context
     */
    public static void handle(AbilitySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> applySync(payload));
    }

    private static void applySync(AbilitySyncPayload payload) {
        Map<GooType, List<ClientAbility>> map = new EnumMap<>(GooType.class);
        for (AbilitySyncPayload.Entry e : payload.entries()) {
            GooType type = GooType.fromId(e.gooTypeId());
            if (type == null) { continue; }
            map.computeIfAbsent(type, t -> new ArrayList<>())
                    .add(new ClientAbility(
                            Identifier.tryParse(e.abilityId()),
                            e.displayName(), e.order()));
        }
        for (List<ClientAbility> list : map.values()) {
            list.sort(Comparator.comparingInt(ClientAbility::order));
        }
        byType = map;
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_SYNCED, payload.entries().size());
        }
    }

    /**
     * Returns the abilities available for a goo type on the client.
     *
     * @param type the goo type
     * @return immutable list, empty if none synced
     */
    public static List<ClientAbility> getAbilitiesForType(GooType type) {
        List<ClientAbility> list = byType.get(type);
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    /**
     * Returns true if the goo type has any synced abilities.
     *
     * @param type the goo type
     * @return true if at least one ability is available
     */
    public static boolean hasAbilities(GooType type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * A lightweight client-side ability descriptor.
     *
     * @param id          the ability resource identifier
     * @param displayName the translation key
     * @param order       the sort order
     */
    public record ClientAbility(Identifier id, String displayName, int order) {}
}
