package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.material.Fluid;
import java.util.*;

/**
 * Datapack reload listener that loads reactor reactions from
 * {@code data/<ns>/goo_reactions/*.json}. Validates conflicts at
 * load time and sorts recipes by input count descending for
 * superset-first matching.
 */
public final class GooReactionLoader
        extends SimpleJsonResourceReloadListener<GooReaction> {

    /**
     * Datapack directory: data/<ns>/goo_reactions/
     */
    private static final String DIRECTORY = "goo_reactions";

    /**
     * Registration id for the reload listener.
     */
    public static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} goo reactions";
    private static final String LOG_CONFLICT_IDENTICAL =
            "Reaction conflict: {} and {} have identical input type sets";
    /**
     * Sorted reactions, most inputs first. Immutable after load.
     */
    private static List<GooReaction> reactions = List.of();

    /**
     * Creates the loader with the reaction codec.
     */
    public GooReactionLoader() {
        super(GooReaction.CODEC,
                FileToIdConverter.json(DIRECTORY));
    }

    /**
     * Returns all loaded reactions, sorted by input count descending.
     *
     * @return immutable reaction list
     */
    public static List<GooReaction> getReactions() {
        return reactions;
    }

    /**
     * Copies the prepared map into a list with resource ids assigned.
     *
     * @param prepared the parsed map from the JSON scanner
     * @return mutable list of reactions with ids
     */
    private static List<GooReaction> assignIds(
            Map<Identifier, GooReaction> prepared) {
        List<GooReaction> list = new ArrayList<>(prepared.size());
        for (Map.Entry<Identifier, GooReaction> entry : prepared.entrySet()) {
            list.add(entry.getValue().withId(entry.getKey()));
        }
        return list;
    }

    /**
     * Checks every pair of recipes for input-type-set conflicts.
     * Identical sets = error. Overlapping but neither subset = warning.
     *
     * @param loaded the loaded reactions
     */
    private static void validateConflicts(List<GooReaction> loaded) {
        for (int i = 0; i < loaded.size(); i++) {
            for (int j = i + 1; j < loaded.size(); j++) {
                checkPair(loaded.get(i), loaded.get(j));
            }
        }
    }

    /**
     * Checks a single pair for conflicts.
     *
     * @param a the first reaction
     * @param b the second reaction
     */
    private static void checkPair(GooReaction a, GooReaction b) {
        Set<Fluid> sa = a.inputTypeSet();
        Set<Fluid> sb = b.inputTypeSet();
        if (sa.equals(sb) && Goo.LOGGER.isErrorEnabled()) {
            Goo.LOGGER.error(LOG_CONFLICT_IDENTICAL, a.id(), b.id());
        }
    }

    /**
     * Returns true if a is a strict subset of b.
     *
     * @param a the candidate subset
     * @param b the candidate superset
     * @return true if every element of a is in b and b is larger
     */
    private static boolean isSubset(Set<Fluid> a, Set<Fluid> b) {
        return a.size() < b.size() && b.containsAll(a);
    }

    @Override
    protected void apply(Map<Identifier, GooReaction> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        List<GooReaction> loaded = assignIds(prepared);
        validateConflicts(loaded);
        loaded.sort(Comparator.comparingInt(
                (GooReaction r) -> r.inputs().size()).reversed());
        reactions = Collections.unmodifiableList(loaded);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED, reactions.size());
        }
    }
}
