package com.mercuriusxeno.goo.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-level SavedData storing the gasket pairing network.
 *
 * <p>Each pairing links an output gasket UUID to an input gasket UUID,
 * forming the directed graph for goo transport. Locations cache where
 * each gasket currently exists (or null if displaced/in item form).</p>
 */
public class GasketRegistry extends SavedData {

    /** Output gasket -> input gasket (directional pairing). */
    private final Map<UUID, UUID> pairings;

    /** Input gasket -> output gasket (inverse index for O(1) source lookup). */
    private final Map<UUID, UUID> reversePairings;

    /** Gasket UUID -> current world location (null entries not stored). */
    private final Map<UUID, GasketLocation> locations;

    /** Codec for persistent serialization of the registry. */
    public static final Codec<GasketRegistry> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, UUIDUtil.STRING_CODEC)
                .fieldOf("pairings").forGetter(r -> r.pairings),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, GasketLocation.CODEC)
                .fieldOf("locations").forGetter(r -> r.locations)
        ).apply(instance, GasketRegistry::new)
    );

    /** SavedData type registration. Stored as goo/gasket_registry.dat in the server data storage. */
    public static final SavedDataType<GasketRegistry> TYPE =
        new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "gasket_registry"),
            GasketRegistry::new, CODEC);

    /** Creates a new empty registry. */
    public GasketRegistry() {
        this(new HashMap<>(), new HashMap<>());
    }

    /** Creates a registry from deserialized data, rebuilding the inverse index. */
    public GasketRegistry(Map<UUID, UUID> pairings, Map<UUID, GasketLocation> locations) {
        this.pairings = new HashMap<>(pairings);
        this.reversePairings = new HashMap<>();
        pairings.forEach((output, input) -> reversePairings.put(input, output));
        this.locations = new HashMap<>(locations);
    }

    /**
     * Retrieves the singleton GasketRegistry for the server.
     * Always stored in the overworld's data storage.
     */
    public static GasketRegistry get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Links an output gasket to an input gasket. Replaces any existing pairing
     * for either gasket (each gasket can only participate in one link).
     */
    public void link(UUID outputGasket, UUID inputGasket) {
        unlink(outputGasket);
        unlink(inputGasket);
        pairings.put(outputGasket, inputGasket);
        reversePairings.put(inputGasket, outputGasket);
        setDirty();
    }

    /**
     * Removes any pairing involving the given gasket, whether as source or target.
     */
    public void unlink(UUID gasketId) {
        boolean changed = false;
        UUID removedInput = pairings.remove(gasketId);
        if (removedInput != null) {
            reversePairings.remove(removedInput);
            changed = true;
        }
        UUID sourceKey = reversePairings.remove(gasketId);
        if (sourceKey != null && pairings.remove(sourceKey) != null) {
            changed = true;
        }
        if (changed) setDirty();
    }

    /** Returns the input gasket UUID paired to the given output, or null. */
    @Nullable
    public UUID getTarget(UUID outputGasket) {
        return pairings.get(outputGasket);
    }

    /** Returns the output gasket UUID feeding into the given input, or null. */
    @Nullable
    public UUID getSource(UUID inputGasket) {
        return reversePairings.get(inputGasket);
    }

    /**
     * Updates the cached world location for a gasket.
     * Pass null to indicate the gasket is displaced (in item form).
     */
    public void updateLocation(UUID gasketId, @Nullable GasketLocation location) {
        if (location == null) {
            locations.remove(gasketId);
        } else {
            locations.put(gasketId, location);
        }
        setDirty();
    }

    /** Returns the cached location for a gasket, or null if displaced. */
    @Nullable
    public GasketLocation getLocation(UUID gasketId) {
        return locations.get(gasketId);
    }

    /** Returns an unmodifiable view of all pairings (output -> input). */
    public Map<UUID, UUID> getPairings() {
        return Map.copyOf(pairings);
    }

    /** Returns an unmodifiable view of all locations. */
    public Map<UUID, GasketLocation> getLocations() {
        return Map.copyOf(locations);
    }

    /** Returns an unmodifiable view of all reverse pairings (input -> output). */
    public Map<UUID, UUID> getReversePairings() {
        return Map.copyOf(reversePairings);
    }
}
