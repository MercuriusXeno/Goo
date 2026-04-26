package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.EnumMap;
import java.util.Map;
import static java.util.Map.entry;

/**
 * Registry that maps each goo type to its polymorphic world effect.
 * Dispatch is a single map lookup - no switch statements.
 */
public final class WorldEffects {

    /** One effect instance per goo type. */
    private static final Map<GooType, WorldEffect> EFFECTS = buildRegistry();

    private WorldEffects() {}

    /**
     * Applies the world effect for the given goo type at the target position.
     *
     * @param level      the world
     * @param pos        the target block position
     * @param type       the goo type whose effect to apply
     * @param targetFace the face of the block that was hit, or null if unknown
     */
    public static void apply(Level level, BlockPos pos, GooType type, @Nullable Direction targetFace) {
        WorldEffect effect = EFFECTS.get(type);
        if (effect != null) {
            effect.apply(level, pos, targetFace);
        }
    }

    /**
     * Builds the type-to-effect map. Every GooType should have an entry.
     * @return immutable type-to-effect map covering all 15 goo types
     */
    private static Map<GooType, WorldEffect> buildRegistry() {
        return new EnumMap<>(Map.ofEntries(
            entry(GooType.ROCK, new RockBehavior()),       entry(GooType.BLAZE, new BlazeBehavior()),
            entry(GooType.FROST, new FrostBehavior()),     entry(GooType.METAL, new MetalBehavior()),
            entry(GooType.CRYSTAL, new CrystalBehavior()), entry(GooType.HEX, new HexEffect()),
            entry(GooType.LEAF, new LeafEffect()),         entry(GooType.VITAL, new VitalEffect()),
            entry(GooType.SHROOM, new ShroomEffect()),     entry(GooType.TYPHOON, new TyphoonEffect()),
            entry(GooType.GLOW, new GlowBehavior()),       entry(GooType.PULSE, new PulseEffect()),
            entry(GooType.NETHER, new NetherBehavior()),   entry(GooType.ENDER, new EnderEffect()),
            entry(GooType.AEON, new AeonEffect()),         entry(GooType.UNSTABLE, new UnstableBehavior())));
    }
}
