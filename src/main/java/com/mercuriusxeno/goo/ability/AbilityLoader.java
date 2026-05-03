package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.HashMap;
import java.util.Map;

/**
 * Datapack reload listener that loads ability definitions from
 * {@code data/<ns>/goo_abilities/*.json}. Populates the
 * {@link AbilityRegistry} on each reload.
 */
public final class AbilityLoader
        extends SimpleJsonResourceReloadListener<AbilityDefinition> {

    /**
     * Datapack directory: data/<ns>/goo_abilities/
     */
    private static final String DIRECTORY = "goo_abilities";

    /**
     * Registration id for the reload listener.
     */
    public static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} goo abilities";

    /**
     * Creates the loader with the ability definition codec.
     */
    public AbilityLoader() {
        super(AbilityDefinition.CODEC,
                FileToIdConverter.json(DIRECTORY));
    }

    /**
     * Copies the prepared map with resource ids assigned from filenames.
     *
     * @param prepared the parsed map from the JSON scanner
     * @return new map with ids applied to each definition
     */
    private static Map<Identifier, AbilityDefinition> assignIds(
            Map<Identifier, AbilityDefinition> prepared) {
        Map<Identifier, AbilityDefinition> result = HashMap.newHashMap(prepared.size());
        for (Map.Entry<Identifier, AbilityDefinition> entry : prepared.entrySet()) {
            result.put(entry.getKey(), entry.getValue().withId(entry.getKey()));
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, AbilityDefinition> prepared,
                         ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, AbilityDefinition> withIds = assignIds(prepared);
        AbilityRegistry.reload(withIds);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_LOADED, withIds.size());
        }
    }
}
