package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.GooType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Invisible marker entity anchored to a block position. All persistent world
 * effects extend this. Provides stacking protocol and ValueInput/ValueOutput persistence.
 */
public class GooWorldEffect extends Entity {

    private static final String TAG_ANCHOR = "AnchorPos";
    private static final String TAG_STACK = "StackCount";
    private static final String TAG_MAX_STACKS = "MaxStacks";
    private static final String TAG_GOO_TYPE = "GooType";

    private static final EntityDataAccessor<Integer> DATA_STACK_COUNT =
            SynchedEntityData.defineId(GooWorldEffect.class, EntityDataSerializers.INT);

    private BlockPos anchorPos;
    private int maxStacks;
    private GooType gooType;

    public GooWorldEffect(EntityType<? extends GooWorldEffect> type, Level level) {
        super(type, level);
        this.anchorPos = BlockPos.ZERO;
        this.maxStacks = 1;
        this.gooType = GooType.ROCK;
        this.noPhysics = true;
    }

    // ── Initialization ────────────────────────────────────────────────────

    /** Configures anchor, goo type, and max stacks after construction. */
    public void init(BlockPos anchor, GooType type, int maxStacks) {
        this.anchorPos = anchor;
        this.gooType = type;
        this.maxStacks = maxStacks;
        setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        entityData.set(DATA_STACK_COUNT, 1);
    }

    // ── Stacking ──────────────────────────────────────────────────────────

    /**
     * Attempts to increment the stack count. Returns true if successful.
     */
    public boolean tryStack() {
        int current = getStackCount();
        if (!EffectMath.canStack(current, maxStacks)) {
            return false;
        }
        entityData.set(DATA_STACK_COUNT, current + 1);
        return true;
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public BlockPos getAnchorPos() {
        return anchorPos;
    }

    public int getStackCount() {
        return entityData.get(DATA_STACK_COUNT);
    }

    public int getMaxStacks() {
        return maxStacks;
    }

    public GooType getGooType() {
        return gooType;
    }

    // ── Entity plumbing ───────────────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STACK_COUNT, 1);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.getIntArray(TAG_ANCHOR).ifPresent(coords -> {
            if (coords.length == 3) {
                anchorPos = new BlockPos(coords[0], coords[1], coords[2]);
            }
        });
        entityData.set(DATA_STACK_COUNT, input.getIntOr(TAG_STACK, 1));
        maxStacks = input.getIntOr(TAG_MAX_STACKS, 1);
        GooType loaded = GooType.fromId(input.getStringOr(TAG_GOO_TYPE, "rock"));
        gooType = loaded != null ? loaded : GooType.ROCK;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putIntArray(TAG_ANCHOR, new int[]{anchorPos.getX(), anchorPos.getY(), anchorPos.getZ()});
        output.putInt(TAG_STACK, getStackCount());
        output.putInt(TAG_MAX_STACKS, maxStacks);
        output.putString(TAG_GOO_TYPE, gooType.getId());
    }
}
