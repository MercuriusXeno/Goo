package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.DataDrivenChainBehavior;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.effect.ChainBehavior;
import com.mercuriusxeno.goo.effect.ChainProfiles.ChainProfile;
import com.mercuriusxeno.goo.effect.EffectMath;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Ticking block entity for chain effects. Owns only the shared state:
 * goo type, stack count, fuse countdown, placed face. The type-specific
 * post-fuse behavior is delegated to a {@link ChainBehavior} instance
 * created from the goo type's profile at fuse expiry.
 *
 * <p>Rock progressive mining is currently still inline here under the
 * legacy {@code layerExecutor} path; it will be extracted into a
 * RockBehavior in a follow-up pass.
 */
public class ChainMarkerBlockEntity extends BlockEntity {

    private static final String TAG_GOO_TYPE = "GooType";
    private static final String TAG_STACK_COUNT = "StackCount";
    private static final String TAG_MAX_STACKS = "MaxStacks";
    private static final String TAG_FUSE_REMAINING = "FuseRemaining";
    private static final String TAG_PLACED_FACE = "PlacedFace";
    /** Default goo type id when loading from NBT. */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /** Default face name when loading from NBT. */
    private static final String DEFAULT_FACE = "up";
    private static final String TAG_BLOB_SHAPE = "BlobShape";
    private static final String TAG_AREA_MODE = "AreaMode";
    private static final String TAG_LAST_STACK_TICK = "LastStackTick";
    private static final String TAG_ABILITY_ID = "AbilityId";
    /** Default area mode for legacy profiles. */
    private static final String DEFAULT_AREA_MODE = "tunnel";
    /** Behavior type key for progressive_area (used to extract areaMode). */
    private static final String PROGRESSIVE_AREA_TYPE = "progressive_area";
    /** Param key for area mode in progressive_area behaviors. */
    private static final String PARAM_AREA_MODE = "areaMode";
    /** Empty ability id sentinel for legacy ChainProfile path. */
    private static final String NO_ABILITY = "";

    /** How often to sync fuse to client (every N ticks). */
    private static final int SYNC_INTERVAL = 5;
    /** Ticks before detonation where we sync every tick for smooth implosion. */
    private static final int IMPLOSION_SYNC_THRESHOLD = 8;

    private GooType gooType = GooType.ROCK;
    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining;
    private Direction placedFace = Direction.UP;
    /** Cosmetic blob shape: "blob" or "flat". Affects BER mesh only. */
    private String blobShape = AbilityDefinition.ChainConfig.SHAPE_BLOB;
    /** Delivery area mode: "tunnel", "flat_circle", or "sphere". Drives footprint. */
    private String areaMode = DEFAULT_AREA_MODE;
    /** Game tick when the last stack was added (for client pulse animation). */
    private long lastStackTick;
    /** Active post-fuse behavior; null during FUSE phase. Set at fuse
     * expiry when the profile has a behavior factory, and nulled out
     * implicitly when the BE removes itself. */
    @Nullable
    private ChainBehavior behavior;
    /** Ability id for data-driven behaviors; empty for legacy ChainProfile path. */
    private String abilityId = "";

    /** Creates a chain marker block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChainMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHAIN_MARKER.get(), pos, state);
    }


    /**
     * Configures this marker from a chain profile. Call immediately after
     * placement via {@code level.setBlock()}.
     *
     * @param type the goo type (determines chain behavior)
     * @param face the face of the block this marker was placed on
     */
    public void initChain(GooType type, Direction face) {
        ChainProfile profile = ChainProfile.forType(type);
        this.gooType = type;
        this.placedFace = face;
        this.stackCount = 1;
        this.maxStacks = profile.maxStacks();
        this.fuseRemaining = profile.fuseTicks();
        this.abilityId = NO_ABILITY;
        setChanged();
        syncToClient();
    }

    /**
     * Configures this marker from a data-driven ability definition.
     *
     * @param type      the goo type
     * @param face      the placed face
     * @param ability   the ability definition
     */
    public void initChainFromAbility(GooType type, Direction face, AbilityDefinition ability) {
        AbilityDefinition.ChainConfig chain = ability.chain();
        this.gooType = type;
        this.placedFace = face;
        this.stackCount = 1;
        this.maxStacks = chain.maxStacks();
        this.fuseRemaining = chain.fuseTicks();
        this.abilityId = ability.id().toString();
        this.blobShape = chain.blobShape();
        this.areaMode = extractAreaMode(ability);
        setChanged();
        syncToClient();
    }

    /** Extracts the areaMode from the first progressive_area behavior entry.
     *
     * @param ability the ability definition
     * @return the area mode string, or "tunnel" if none found
     */
    private static String extractAreaMode(AbilityDefinition ability) {
        for (AbilityDefinition.BehaviorEntry entry : ability.behaviors()) {
            if (PROGRESSIVE_AREA_TYPE.equals(entry.type())) {
                return entry.params().getOrDefault(PARAM_AREA_MODE, DEFAULT_AREA_MODE);
            }
        }
        return DEFAULT_AREA_MODE;
    }


    /**
     * Attempts to increment the stack count. Returns true if successful.
     * Resets the fuse timer on each successful stack.
     *
     * @return true if the stack count was incremented
     */
    public boolean tryStack() {
        if (behavior != null && !behavior.allowsTopOff()) { return false; }
        if (!EffectMath.canStack(stackCount, maxStacks)) { return false; }
        stackCount++;
        lastStackTick = level != null ? level.getGameTime() : 0;
        applyStackEffect();
        setChanged();
        syncToClient();
        return true;
    }

    /** Delegates the fuse reset to the behavior if active, otherwise sets fuse from profile. */
    private void applyStackEffect() {
        if (behavior != null) {
            behavior.onTopOff(this);
        } else {
            fuseRemaining = ChainProfile.forType(gooType).fuseTicks();
        }
    }

    /**
     * Decrements the stack count by one. Used by behaviors that consume
     * stacks as charges (metal, crystal). Syncs to client.
     */
    public void decrementStack() {
        if (stackCount > 0) {
            stackCount--;
            setChanged();
            syncToClient();
        }
    }

    /**
     * Restores full state after a fall re-placement. Called by
     * {@link ChainMarkerFallScheduler} after the flight animation completes.
     *
     * @param stacks    the snapshotted stack count
     * @param max       the snapshotted max stacks
     * @param fuse      the snapshotted fuse remaining
     * @param shape     the snapshotted blob shape
     * @param area      the snapshotted area mode
     */
    public void restoreFromFall(int stacks, int max, int fuse, String shape, String area) {
        this.stackCount = stacks;
        this.maxStacks = max;
        this.fuseRemaining = fuse;
        this.blobShape = shape;
        this.areaMode = area;
        setChanged();
        syncToClient();
    }

    /**
     * Resets the fuse timer to the profile's full duration. Called by the
     * server when a throw is declared toward this marker, keeping the
     * fuse alive while blobs are in flight.
     */
    public void stallFuse() {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile != null) {
            fuseRemaining = profile.fuseTicks();
            setChanged();
        }
    }


    /**
     * Forces immediate detonation by zeroing the fuse. The next tick
     * will fire the behavior. Used by unstable goo on punch.
     */
    public void instantDetonate() {
        fuseRemaining = 0;
        setChanged();
        syncToClient();
    }

    /** Returns the cosmetic blob shape ("blob" or "flat").
     *
     * @return the blob shape string
     */
    public String getBlobShape() {
        return blobShape;
    }

    /** Returns the delivery area mode ("tunnel", "flat_circle", or "sphere").
     *
     * @return the area mode string
     */
    public String getAreaMode() {
        return areaMode;
    }

    /** Returns true if the blob should render as squished (flat shape).
     *
     * @return true for flat blob visual
     */
    public boolean isFlatBlob() {
        return AbilityDefinition.ChainConfig.SHAPE_FLAT.equals(blobShape);
    }

    /**
     * Returns the game tick when the last blob was stacked.
     *
     * @return the game tick of the last stack event
     */
    public long getLastStackTick() {
        return lastStackTick;
    }


    /** Server tick: either a post-fuse behavior is active (delegate) or
     * the fuse is still counting down (or rock progressive mining is
     * running under the legacy path).
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        if (be.behavior != null) {
            be.behavior.serverTick(server, pos, be);
            if (!be.behavior.isActive()) {
                server.removeBlock(pos, false);
                return;
            }
            be.setChanged();
            be.syncToClient();
            return;
        }
        be.tickFuse(server, pos);
    }

    /** FUSE-phase tick: counts the fuse down and detonates on expiry.
     *
     * @param level the server level
     * @param pos   the block position
     */
    private void tickFuse(ServerLevel level, BlockPos pos) {
        if (fuseRemaining < 0) { return; }
        fuseRemaining--;
        if (!EffectMath.isFuseLive(fuseRemaining)) {
            detonate(level, pos);
            return;
        }
        syncIfNeeded();
    }

    /** Sends a client sync packet when entering implosion zone or at regular intervals. */
    private void syncIfNeeded() {
        boolean implosionZone = fuseRemaining <= IMPLOSION_SYNC_THRESHOLD;
        if (implosionZone || fuseRemaining % SYNC_INTERVAL == 0) {
            syncToClient();
        }
    }

    /** Fires the chain effect for this goo type by creating the profile's
     * {@link ChainBehavior} and invoking {@code onFuseExpired}. If the
     * behavior finishes immediately (instant one-shot like blaze), the BE
     * is removed on the same tick; otherwise the BE stays and
     * {@link #serverTick} will delegate to {@link ChainBehavior#serverTick}
     * on subsequent ticks.
     *
     * @param level the current level
     * @param pos   the block position
     */
    private void detonate(ServerLevel level, BlockPos pos) {
        behavior = createBehavior();
        if (behavior == null) {
            level.removeBlock(pos, false);
            return;
        }
        behavior.onFuseExpired(level, pos, this);
        if (!behavior.isActive()) {
            if (level.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get())) {
                level.removeBlock(pos, false);
            }
            return;
        }
        setChanged();
        syncToClient();
    }


    /** Creates the post-fuse behavior: ability-driven if abilityId is set, otherwise ChainProfile.
     *
     * @return the new behavior, or null if neither path resolves
     */
    private @Nullable ChainBehavior createBehavior() {
        ChainBehavior fromAbility = createFromAbility();
        if (fromAbility != null) { return fromAbility; }
        return createFromProfile();
    }

    /** Attempts to create a behavior from the ability registry.
     *
     * @return the data-driven behavior, or null if no ability is set
     */
    private @Nullable ChainBehavior createFromAbility() {
        if (abilityId.isEmpty()) { return null; }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(abilityId);
        if (id == null) { return null; }
        AbilityDefinition def = AbilityRegistry.getAbility(id);
        return def != null ? new DataDrivenChainBehavior(def) : null;
    }

    /** Attempts to create a behavior from the legacy ChainProfile.
     *
     * @return the legacy behavior, or null if no profile exists
     */
    private @Nullable ChainBehavior createFromProfile() {
        ChainProfile profile = ChainProfile.forType(gooType);
        if (profile == null || profile.behaviorFactory() == null) { return null; }
        return profile.behaviorFactory().get();
    }

    /** Returns the goo type driving this chain effect.
     *
     * @return the goo type
     */
    public GooType getGooType() {
        return gooType;
    }

    /** Returns the current stack count (number of blobs absorbed).
     *
     * @return the stack count
     */
    public int getStackCount() {
        return stackCount;
    }

    /** Returns the maximum stacks allowed by the chain profile.
     *
     * @return the max stacks
     */
    public int getMaxStacks() {
        return maxStacks;
    }

    /** Returns the remaining fuse ticks before detonation.
     *
     * @return the fuse remaining
     */
    public int getFuseRemaining() {
        return fuseRemaining;
    }

    /** Returns the block face this marker was placed on.
     *
     * @return the placed face
     */
    public Direction getPlacedFace() {
        return placedFace;
    }

    /** Returns the active post-fuse behavior, or null if still in FUSE.
     * The BER calls this to {@code instanceof}-check for type-specific
     * render paths (e.g. nether black-hole sphere).
     *
     * @return the active chain behavior, or null
     */
    @Nullable
    public ChainBehavior getBehavior() {
        return behavior;
    }


    /** Restores chain state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        loadSharedFields(input);
        placedFace = loadFace(input);
        reconstituteBehaviorIfNeeded(input);
    }

    /** Restores goo type, stack count, max stacks, and fuse from
     * persistent data.
     *
     * @param input the value input to read from
     */
    private void loadSharedFields(ValueInput input) {
        GooType loaded = GooType.fromId(input.getStringOr(TAG_GOO_TYPE, DEFAULT_GOO_TYPE));
        gooType = loaded != null ? loaded : GooType.ROCK;
        stackCount = input.getIntOr(TAG_STACK_COUNT, 1);
        maxStacks = input.getIntOr(TAG_MAX_STACKS, 1);
        fuseRemaining = input.getIntOr(TAG_FUSE_REMAINING, 0);
        blobShape = input.getStringOr(TAG_BLOB_SHAPE, AbilityDefinition.ChainConfig.SHAPE_BLOB);
        areaMode = input.getStringOr(TAG_AREA_MODE, DEFAULT_AREA_MODE);
        lastStackTick = input.getLongOr(TAG_LAST_STACK_TICK, 0);
        abilityId = input.getStringOr(TAG_ABILITY_ID, NO_ABILITY);
    }

    /** If the fuse has already expired, re-creates the behavior instance
     * via the profile factory and lets it reload its own state from the
     * same value stream. Called after the shared fields have been loaded.
     *
     * @param input the value input to read from
     */
    private void reconstituteBehaviorIfNeeded(ValueInput input) {
        if (fuseRemaining > 0) { return; }
        behavior = createBehavior();
        if (behavior == null) { return; }
        behavior.loadAdditional(input);
    }

    /** Loads the placed face direction, defaulting to UP if unrecognized.
     *
     * @param input the value input to read from
     * @return the placed face direction
     */
    private Direction loadFace(ValueInput input) {
        String faceName = input.getStringOr(TAG_PLACED_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        return dir != null ? dir : Direction.UP;
    }

    /** Writes chain state to persistent storage.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString(TAG_GOO_TYPE, gooType.getId());
        output.putInt(TAG_STACK_COUNT, stackCount);
        output.putInt(TAG_MAX_STACKS, maxStacks);
        output.putInt(TAG_FUSE_REMAINING, fuseRemaining);
        output.putString(TAG_PLACED_FACE, placedFace.getName());
        output.putString(TAG_BLOB_SHAPE, blobShape);
        output.putString(TAG_AREA_MODE, areaMode);
        output.putLong(TAG_LAST_STACK_TICK, lastStackTick);
        output.putString(TAG_ABILITY_ID, abilityId);
        if (behavior != null) {
            behavior.saveAdditional(output);
        }
    }


    /** Returns the sync packet sent when block entity data changes.
     *
     * @return the update packet
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Returns the full NBT for initial chunk sync to clients.
     *
     * @param registries the registry provider
     * @return the update tag
     */
    @Override
    public @NonNull CompoundTag getUpdateTag(HolderLookup.@NonNull Provider registries) {
        return saveCustomOnly(registries);
    }

    /** Sends a block update to tracking clients so the BER can render. */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(),
                BlockEntitySync.BLOCK_UPDATE_FLAGS);
        }
    }
}
