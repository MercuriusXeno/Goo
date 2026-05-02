package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A ChainBehavior composed from data-driven behavior building blocks.
 * Each block is created from a BehaviorEntry in the AbilityDefinition
 * via the BehaviorType factory registry. The composed behavior delegates
 * lifecycle calls to each block in sequence.
 */
public final class DataDrivenChainBehavior implements ChainBehavior {

    private final AbilityDefinition definition;
    private final List<ChainBehavior> blocks;

    /**
     * Creates a data-driven behavior from an ability definition.
     *
     * @param definition the ability definition to compose from
     */
    public DataDrivenChainBehavior(AbilityDefinition definition) {
        this.definition = definition;
        this.blocks = new ArrayList<>(definition.behaviors().size());
        for (AbilityDefinition.BehaviorEntry entry : definition.behaviors()) {
            blocks.add(BehaviorType.create(entry, definition));
        }
    }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        for (ChainBehavior block : blocks) {
            block.onFuseExpired(level, pos, be);
        }
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        for (ChainBehavior block : blocks) {
            block.serverTick(level, pos, be);
        }
    }

    @Override
    public boolean isActive() {
        for (ChainBehavior block : blocks) {
            if (block.isActive()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean allowsTopOff() {
        for (ChainBehavior block : blocks) {
            if (block.allowsTopOff()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTopOff(ChainMarkerBlockEntity be) {
        for (ChainBehavior block : blocks) {
            block.onTopOff(be);
        }
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        for (ChainBehavior block : blocks) {
            block.saveAdditional(output);
        }
    }

    @Override
    public void loadAdditional(ValueInput input) {
        for (ChainBehavior block : blocks) {
            block.loadAdditional(input);
        }
    }

    /**
     * Returns the parent ability definition.
     *
     * @return the definition this behavior was composed from
     */
    public AbilityDefinition getDefinition() {
        return definition;
    }

    /**
     * Returns the first composed block of the given type, or null.
     * Lets client visuals reach the concrete inner behavior (e.g.
     * {@code CrystalBehavior}) when the ability path wraps it.
     *
     * @param <T>  the requested behavior block type
     * @param type the class to match against
     * @return the matching block, or null if none
     */
    public <T extends ChainBehavior> @Nullable T findInner(Class<T> type) {
        for (ChainBehavior block : blocks) {
            if (type.isInstance(block)) {
                return type.cast(block);
            }
        }
        return null;
    }
}
