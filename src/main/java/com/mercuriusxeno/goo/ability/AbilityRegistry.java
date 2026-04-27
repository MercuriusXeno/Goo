package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Static registry of loaded ability definitions. Populated by
 * {@link AbilityLoader} during datapack reload. Provides lookup
 * by identifier and by goo type.
 */
public final class AbilityRegistry {

    private static Map<Identifier, AbilityDefinition> byId = Map.of();
    private static Map<GooType, List<AbilityDefinition>> byType = new EnumMap<>(GooType.class);

    private AbilityRegistry() {
    }

    /**
     * Replaces the registry contents. Called by the loader after parsing.
     *
     * @param abilities the loaded ability map keyed by resource id
     */
    static void reload(Map<Identifier, AbilityDefinition> abilities) {
        byId = Map.copyOf(abilities);
        byType = abilities.values().stream()
                .sorted(Comparator.comparingInt(AbilityDefinition::order))
                .collect(Collectors.groupingBy(
                        AbilityDefinition::gooType,
                        () -> new EnumMap<>(GooType.class),
                        Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList)));
    }

    /**
     * Returns the ability definition for the given id, or null.
     *
     * @param id the resource identifier
     * @return the definition, or null if not found
     */
    public static @Nullable AbilityDefinition getAbility(Identifier id) {
        return byId.get(id);
    }

    /**
     * Returns all abilities for the given goo type, sorted by order.
     *
     * @param type the goo type
     * @return immutable list, empty if none registered
     */
    public static List<AbilityDefinition> getAbilitiesForType(GooType type) {
        return byType.getOrDefault(type, List.of());
    }

    /**
     * Returns true if the goo type has any registered abilities.
     *
     * @param type the goo type
     * @return true if at least one ability is registered
     */
    public static boolean hasAbilities(GooType type) {
        return !getAbilitiesForType(type).isEmpty();
    }

    /**
     * Returns true if the given ability id is valid for the given type.
     *
     * @param type      the goo type
     * @param abilityId the ability resource id
     * @return true if the ability exists and belongs to the type
     */
    public static boolean isValidAbility(GooType type, Identifier abilityId) {
        AbilityDefinition def = byId.get(abilityId);
        return def != null && def.gooType() == type;
    }

    /**
     * Returns the total number of loaded abilities.
     *
     * @return the count
     */
    public static int size() {
        return byId.size();
    }
}
