package com.mercuriusxeno.goo.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Locates a gasket in the world: dimension, block position, whether it's
 * the top (input) or bottom (output) gasket, and which slot within a
 * multi-canister block it belongs to.
 *
 * <p>For placed canisters, pos is the canister's block position and slot
 * identifies which sub-canister (0-8) in the 3x3 grid. For single-canister
 * blocks and machines, slot is 0.</p>
 *
 * <p>For entity targets (e.g. player inventory), entityId is non-null and
 * pos becomes a stale hint for display purposes only.</p>
 */
public record GasketLocation(
        ResourceKey<Level> dimension,
        BlockPos pos,
        boolean isTop,
        int slot,
        @Nullable UUID entityId) {

    /** Convenience constructor for block targets (no entity). */
    public GasketLocation(ResourceKey<Level> dimension, BlockPos pos, boolean isTop, int slot) {
        this(dimension, pos, isTop, slot, null);
    }

    /** Returns true if this location targets an entity rather than a block. */
    public boolean isEntityTarget() {
        return entityId != null;
    }

    /** Codec for persistent serialization. Slot and entityId are optional for backward compat. */
    public static final Codec<GasketLocation> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension")
                .forGetter(GasketLocation::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(GasketLocation::pos),
            Codec.BOOL.fieldOf("is_top").forGetter(GasketLocation::isTop),
            Codec.INT.optionalFieldOf("slot", 0).forGetter(GasketLocation::slot),
            UUIDUtil.STRING_CODEC.optionalFieldOf("entity_id")
                .forGetter(loc -> Optional.ofNullable(loc.entityId()))
        ).apply(instance, (dim, pos, isTop, slot, entityOpt) ->
            new GasketLocation(dim, pos, isTop, slot, entityOpt.orElse(null)))
    );
}
