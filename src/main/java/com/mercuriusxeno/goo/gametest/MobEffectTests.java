package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.effect.AeonMobEffect;
import com.mercuriusxeno.goo.effect.BlazeMobEffect;
import com.mercuriusxeno.goo.effect.CrystalMobEffect;
import com.mercuriusxeno.goo.effect.EnderMobEffect;
import com.mercuriusxeno.goo.effect.FrostMobEffect;
import com.mercuriusxeno.goo.effect.GlowMobEffect;
import com.mercuriusxeno.goo.effect.GooMobEffects;
import com.mercuriusxeno.goo.effect.HexMobEffect;
import com.mercuriusxeno.goo.effect.LeafMobEffect;
import com.mercuriusxeno.goo.effect.MetalMobEffect;
import com.mercuriusxeno.goo.effect.NetherMobEffect;
import com.mercuriusxeno.goo.effect.PulseMobEffect;
import com.mercuriusxeno.goo.effect.RockMobEffect;
import com.mercuriusxeno.goo.effect.ShroomMobEffect;
import com.mercuriusxeno.goo.effect.TyphoonMobEffect;
import com.mercuriusxeno.goo.effect.UnstableMobEffect;
import com.mercuriusxeno.goo.effect.VitalMobEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Gametests for the 16 per-goo-type mob effects. Each test spawns a mob,
 * applies the effect, and asserts the expected outcome (damage, status
 * effect, fire, AI state, etc.).
 */
public final class MobEffectTests {

    private static final BlockPos SPAWN_POS = new BlockPos(1, 1, 1);
    private static final String SHOULD_HAVE_SLOWNESS = "Target should have slowness";
    private static final String SHOULD_HAVE_POISON = "Target should have poison";
    private static final String SHOULD_HAVE_WEAKNESS = "Target should have weakness";
    private static final String SHOULD_HAVE_GLOWING = "Target should have glowing";
    private static final String SHOULD_HAVE_WITHER = "Target should have wither";
    private static final String SHOULD_TAKE_DAMAGE = "Target should have taken damage";
    private static final String SHOULD_BE_ON_FIRE = "Target should be on fire";
    private static final String SHOULD_HAVE_NO_AI = "Target should have AI disabled";
    private static final String SHOULD_BE_INVULNERABLE = "Target should be invulnerable";
    private static final String SHOULD_HAVE_LEVITATION = "Target should have levitation";

    private MobEffectTests() {}

    /**
     * Metal javelin deals direct magic damage.
     * @param helper the gametest helper
     */
    public static void metalJavelin(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        MetalMobEffect.apply(mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.succeed();
    }

    /**
     * Crystal flechettes deal damage to the primary target.
     * @param helper the gametest helper
     */
    public static void crystalFlechettes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        CrystalMobEffect.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.succeed();
    }

    /**
     * Leaf entangle applies slowness and poison.
     * @param helper the gametest helper
     */
    public static void leafEntangle(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        LeafMobEffect.apply(mob);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
    }

    /**
     * Vital clone attempts to clone the mob (probabilistic - just verify no crash).
     * @param helper the gametest helper
     */
    public static void vitalClone(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.CHICKEN, SPAWN_POS);
        VitalMobEffect.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Shroom debuff applies slowness, weakness, and poison.
     * @param helper the gametest helper
     */
    public static void shroomDebuff(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        ShroomMobEffect.apply(mob);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), SHOULD_HAVE_WEAKNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
    }

    /**
     * Rock petrify applies max slowness.
     * @param helper the gametest helper
     */
    public static void rockPetrify(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        RockMobEffect.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
    }

    /**
     * Blaze ignite sets the target on fire.
     * @param helper the gametest helper
     */
    public static void blazeIgnite(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        BlazeMobEffect.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.isOnFire(), SHOULD_BE_ON_FIRE);
        helper.succeed();
    }

    /**
     * Frost snap deals damage and applies slowness.
     * @param helper the gametest helper
     */
    public static void frostSnap(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        FrostMobEffect.apply(mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
    }

    /**
     * Typhoon applies levitation.
     * @param helper the gametest helper
     */
    public static void typhoonLevitate(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        TyphoonMobEffect.apply(mob);
        helper.assertTrue(mob.hasEffect(MobEffects.LEVITATION), SHOULD_HAVE_LEVITATION);
        helper.succeed();
    }

    /**
     * Glow laser deals damage and applies glowing.
     * @param helper the gametest helper
     */
    public static void glowLaser(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        GlowMobEffect.apply(helper.getLevel(), mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * Hex charm applies weakness and glowing to mobs.
     * @param helper the gametest helper
     */
    public static void hexCharm(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        HexMobEffect.apply(mob, null);
        helper.assertTrue(mob.hasEffect(MobEffects.WEAKNESS), SHOULD_HAVE_WEAKNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * Pulse stun disables AI and applies max slowness.
     * @param helper the gametest helper
     */
    public static void pulseStun(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        PulseMobEffect.apply(mob);
        helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.succeed();
    }

    /**
     * Nether wither halves health and applies wither.
     * @param helper the gametest helper
     */
    public static void netherWither(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        float before = mob.getHealth();
        NetherMobEffect.apply(mob);
        helper.assertTrue(mob.getHealth() < before, SHOULD_TAKE_DAMAGE);
        helper.assertTrue(mob.hasEffect(MobEffects.WITHER), SHOULD_HAVE_WITHER);
        helper.succeed();
    }

    /**
     * Ender teleport moves the target (just verify no crash).
     * @param helper the gametest helper
     */
    public static void enderTeleport(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        EnderMobEffect.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Unstable explode detonates at the target (just verify no crash).
     * @param helper the gametest helper
     */
    public static void unstableExplode(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        UnstableMobEffect.apply(helper.getLevel(), mob);
        helper.succeed();
    }

    /**
     * Aeon time stop disables AI and makes invulnerable.
     * @param helper the gametest helper
     */
    public static void aeonTimeStop(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        AeonMobEffect.apply(mob);
        helper.assertTrue(mob.isNoAi(), SHOULD_HAVE_NO_AI);
        helper.assertTrue(mob.isInvulnerable(), SHOULD_BE_INVULNERABLE);
        helper.assertTrue(mob.hasEffect(MobEffects.GLOWING), SHOULD_HAVE_GLOWING);
        helper.succeed();
    }

    /**
     * GooMobEffects.apply() dispatcher routes to the correct per-type handler.
     * Exercises the dispatch map that was previously the monolith entry point.
     *
     * @param helper the gametest helper
     */
    public static void dispatcherRoutes(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.COW, SPAWN_POS);
        GooMobEffects.apply(helper.getLevel(), mob, GooType.LEAF, null);
        helper.assertTrue(mob.hasEffect(MobEffects.SLOWNESS), SHOULD_HAVE_SLOWNESS);
        helper.assertTrue(mob.hasEffect(MobEffects.POISON), SHOULD_HAVE_POISON);
        helper.succeed();
    }
}
