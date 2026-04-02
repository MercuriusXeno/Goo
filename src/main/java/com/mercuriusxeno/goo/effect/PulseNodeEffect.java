package com.mercuriusxeno.goo.effect;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Shared base for Leaf and Vital pulse nodes. Pulses at an interval that
 * halves with each stack (capped at 4). Depletes one blob per pulse;
 * self-removes at zero. Subclasses implement {@link #onPulse()}.
 */
public abstract class PulseNodeEffect extends GooWorldEffect {

    private static final String TAG_BLOB_COUNT = "BlobCount";
    private static final String TAG_PULSE_TIMER = "PulseTimer";

    private int blobCount;
    private int pulseTimer;

    public PulseNodeEffect(EntityType<? extends PulseNodeEffect> type, Level level) {
        super(type, level);
    }

    /** Delegates to {@link EffectMath#computePulseInterval(int)}. */
    public static int computePulseInterval(int stackCount) {
        return EffectMath.computePulseInterval(stackCount);
    }

    // ── Blob management ───────────────────────────────────────────────────

    /** Adds blobs and grants one immediate pulse. */
    public void addBlobs(int count) {
        blobCount += count;
        onPulse();
    }

    public int getBlobCount() {
        return blobCount;
    }

    public void setBlobCount(int count) {
        this.blobCount = count;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        pulseTimer--;
        if (pulseTimer <= 0) {
            blobCount--;
            if (blobCount <= 0) {
                discard();
                return;
            }
            onPulse();
            pulseTimer = computePulseInterval(getStackCount());
        }
    }

    /** Subclass hook: execute the pulse effect (growth, mating, etc.). */
    protected abstract void onPulse();

    // ── Persistence ───────────────────────────────────────────────────────

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        blobCount = input.getIntOr(TAG_BLOB_COUNT, 0);
        pulseTimer = input.getIntOr(TAG_PULSE_TIMER, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt(TAG_BLOB_COUNT, blobCount);
        output.putInt(TAG_PULSE_TIMER, pulseTimer);
    }
}
