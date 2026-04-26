package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.BehaviorEntry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.effect.ChainBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * No-op ChainBehavior placeholder for entity-targeted effects.
 * Entity effects are dispatched by EntityEffectRegistry on blob impact,
 * not through the chain marker tick pipeline. This class exists so the
 * BehaviorType factory can load entity_effect entries without error.
 */
public final class EntityEffectBehavior implements ChainBehavior {

    private static final String PARAM_HANDLER = "handler";
    private static final String NO_HANDLER = "";

    private final String handlerName;

    /** Creates an entity effect behavior referencing the named handler.
     *
     * @param handlerName the handler key in EntityEffectRegistry
     */
    public EntityEffectBehavior(String handlerName) {
        this.handlerName = handlerName;
    }

    /** Factory for BehaviorType registration.
     *
     * @param entry the behavior entry with params
     * @param def   the parent ability definition
     * @return a new EntityEffectBehavior
     */
    public static ChainBehavior fromEntry(BehaviorEntry entry, AbilityDefinition def) {
        return new EntityEffectBehavior(entry.params().getOrDefault(PARAM_HANDLER, NO_HANDLER));
    }

    /** Returns the handler name for EntityEffectRegistry lookup.
     *
     * @return the handler name string
     */
    public String handlerName() { return handlerName; }

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {}

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {}

    @Override
    public boolean isActive() { return false; }

    @Override
    public void saveAdditional(ValueOutput output) {}

    @Override
    public void loadAdditional(ValueInput input) {}
}
