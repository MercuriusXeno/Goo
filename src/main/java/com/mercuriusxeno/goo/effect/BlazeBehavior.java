package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.block.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;
import java.util.Optional;

/**
 * Blaze chain behavior: instant explosion + flame particles + scattered
 * fire on fuse expiry. All work happens in
 * {@link #onFuseExpired}; {@link #isActive} is false immediately
 * afterward, so the chain marker BE removes itself on the same tick.
 *
 * <p>This behavior has no persistent state - the explosion is a single
 * TNT-style blast with a particle burst and a random fire scatter
 * across the blast footprint. Stack count scales both the explosion
 * radius (via {@link EffectMath#computeExplosionRadius}) and the
 * particle/fire density.
 */
public final class BlazeBehavior implements ChainBehavior {

    /** Offset to get block center from integer position. */
    private static final double BLOCK_CENTER_OFFSET = 0.5;
    /** Particles per stack for the flame burst. */
    private static final int FLAME_PARTICLES_PER_STACK = 20;
    /** Spread multiplier applied to the explosion range for particle distribution. */
    private static final double FLAME_SPREAD_FACTOR = 0.6;
    /** Upward velocity for flame particles. */
    private static final double FLAME_PARTICLE_SPEED = 0.05;
    /** Lava particle count divisor relative to flame count. */
    private static final int LAVA_PARTICLE_DIVISOR = 2;
    /** Smoke particle count divisor relative to flame count. */
    private static final int SMOKE_PARTICLE_DIVISOR = 3;
    /** Vertical spread multiplier for the smoke plume. */
    private static final double SMOKE_SPREAD_MULTIPLIER = 1.5;
    /** Upward velocity for smoke particles. */
    private static final double SMOKE_PARTICLE_SPEED = 0.02;
    /** Fortune level applied to ore drops. */
    private static final int FORTUNE_LEVEL = 3;
    /** Block break level event ID (sends break particles to clients). */
    private static final int BREAK_EFFECT_EVENT = 2001;

    @Override
    public void onFuseExpired(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        int stackCount = be.getStackCount();
        int range = (int) EffectMath.computeExplosionRadius(stackCount);
        double cx = pos.getX() + BLOCK_CENTER_OFFSET;
        double cy = pos.getY() + BLOCK_CENTER_OFFSET;
        double cz = pos.getZ() + BLOCK_CENTER_OFFSET;

        // Entity damage + knockback only; blocks handled separately
        // with fortune 3 + auto-smelt and no random drop destruction.
        level.explode(null, cx, cy, cz, (float) range,
                Level.ExplosionInteraction.NONE);

        breakBlocksInRadius(level, pos, range);
        emitParticles(level, cx, cy, cz, range, stackCount);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, ChainMarkerBlockEntity be) {
        // Instant behavior: never ticks.
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void saveAdditional(ValueOutput output) {
        // No state.
    }

    @Override
    public void loadAdditional(ValueInput input) {
        // No state.
    }

    // ── Custom fortune + smelt block breaking ─────────────────────────────

    /** Breaks all destructible blocks in the explosion sphere, dropping
     * fortune-3 loot auto-smelted via furnace recipes. No random drop
     * destruction - every item survives.
     *
     * @param level the server level
     * @param center the explosion center
     * @param range the explosion radius
     */
    private static void breakBlocksInRadius(ServerLevel level, BlockPos center, int range) {
        ItemStack fortuneTool = buildFortuneTool(level);
        EffectMath.forEachInSphere(center, range, pos -> {
            tryBreakBlock(level, pos, fortuneTool);
        });
    }

    /** Creates a diamond pickaxe with fortune 3 for loot context.
     *
     * @param level the server level (provides registry access)
     * @return a fortune-3 diamond pickaxe
     */
    private static ItemStack buildFortuneTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        Holder<Enchantment> fortune = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        tool.enchant(fortune, FORTUNE_LEVEL);
        return tool;
    }

    /** Breaks a single block if destructible, spawning fortune + smelted drops.
     *
     * @param level the server level
     * @param pos   the block position
     * @param tool  the fortune-enchanted tool for loot context
     */
    private static void tryBreakBlock(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) { return; }
        if (state.getDestroySpeed(level, pos) < 0) { return; }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, null, tool);
        for (ItemStack drop : drops) {
            ItemStack smelted = trySmelting(level, drop);
            Block.popResource(level, pos, smelted);
        }
        state.spawnAfterBreak(level, pos, tool, true);

        level.levelEvent(BREAK_EFFECT_EVENT, pos, Block.getId(state));
        level.removeBlock(pos, false);
    }

    /** Attempts to smelt an item via furnace recipe. Returns the smelted
     * result at the same stack count, or the original if no recipe exists.
     *
     * @param level the server level
     * @param drop  the item to try smelting
     * @return smelted result or the original drop
     */
    private static ItemStack trySmelting(ServerLevel level, ItemStack drop) {
        Optional<RecipeHolder<SmeltingRecipe>> recipe = level.recipeAccess()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
        if (recipe.isPresent()) {
            ItemStack result = recipe.get().value()
                    .assemble(new SingleRecipeInput(drop));
            result.setCount(drop.getCount());
            return result;
        }
        return drop;
    }

    // ── Particles ────────────────────────────────────────────────────────

    /** Sends flame, lava, and smoke particles scaled by explosion range and stack count.
     *
     * @param level      the server level to spawn particles in
     * @param cx         the explosion center X coordinate
     * @param cy         the explosion center Y coordinate
     * @param cz         the explosion center Z coordinate
     * @param range      the explosion radius controlling particle spread
     * @param stackCount the number of stacked blobs controlling particle density
     */
    private static void emitParticles(ServerLevel level,
            double cx, double cy, double cz, int range, int stackCount) {
        int particleCount = FLAME_PARTICLES_PER_STACK * stackCount;
        double spread = range * FLAME_SPREAD_FACTOR;
        level.sendParticles(ParticleTypes.FLAME,
                cx, cy, cz, particleCount, spread, spread, spread, FLAME_PARTICLE_SPEED);
        level.sendParticles(ParticleTypes.LAVA,
                cx, cy, cz, particleCount / LAVA_PARTICLE_DIVISOR, spread, spread, spread, 0.0);
        level.sendParticles(ParticleTypes.SMOKE,
                cx, cy + BLOCK_CENTER_OFFSET, cz, particleCount / SMOKE_PARTICLE_DIVISOR,
                spread, spread * SMOKE_SPREAD_MULTIPLIER, spread, SMOKE_PARTICLE_SPEED);
    }
}
