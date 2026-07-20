---
id: plan-effects-8-endothermic
title: "Effect 8: Frost — Endothermic"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 8: Frost — Endothermic

**Class:** `effect/brew/EndothermicEffect.java` | **Category:** BENEFICIAL | **Color:** `0xADD8E6`

## Design Spec

> "Endothermic - drain heat per step (weakest frost world-effect). Extinguish projectiles/fires. Damage blazes/magma cubes within 8 blocks (proximity scaling), buff snow golems inversely. Heat absorption causes fire damage at threshold. Tolerance proportional to current health, cooldown proportional to missing health."
> — DESIGN-TYPES.md, Frost brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Drain heat per step" — is "heat" a tracked stat? What does "step" mean? | "Step" = walking. Apply weakest frost world-effect (freeze water, extinguish fires) in 3-block radius as player moves. "Heat" is a tracked float accumulating from fire interactions. |
| 2 | "Extinguish projectiles/fires" — radius? | Fires: 3-block radius (same as frost trail). Projectiles: 4-block radius. |
| 3 | Blaze/magma damage scaling not quantified. | 2 * (1 - distance/8) HP per second. At 0 blocks = 2 DPS, at 4 = 1 DPS, at 8 = 0. |
| 4 | "Buff snow golems inversely" — inverse to what? | Inverse to distance. Snow golems closer = stronger buff. Apply Regeneration with amplifier scaling inversely: `amp = 3 * (1 - distance/8)`. |
| 5 | "Heat absorption causes fire damage at threshold" — what's the threshold? | Threshold = `5 * (currentHP / maxHP)`. Full HP = threshold 5. Half HP = 2.5. Each fire extinguished adds 1.0 heat, each blaze damaged adds 0.5. |
| 6 | "Cooldown proportional to missing health" — cooldown on what? | Cooldown between fire damage ticks from overheating. `20 * (1 - currentHP/maxHP)` ticks. Full HP = 0 tick cooldown (instant). Half HP = 10 ticks. Low HP = longer mercy window. |
| 7 | "Tolerance proportional to current health" — isn't this the same as the threshold? | Yes, tolerance IS the threshold. Higher health = higher tolerance = can absorb more heat before fire damage. |

## Prerequisites

- Infra A (foundation)
- Infra C (Proximity Scanning)
- Custom data attachment for heat tracking

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/EndothermicEffect.java` | Every-tick proximity effects + heat state machine |
| `effect/brew/EndothermicHeatData.java` | Tracks accumulated heat + cooldown |
| `test/.../effect/brew/EndothermicEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add ENDOTHERMIC holder |
| `registry/GooPotions.java` | FROST case: use custom effect |
| `en_us.json` | Add `"effect.goo.endothermic": "Endothermic"` |

### Tick Logic (every tick)

1. **Frost trail:** If player moved since last tick, scan 3-block radius for fire blocks → extinguish. Scan for water → freeze to ice. Add heat per fire extinguished.
2. **Projectile scan:** 4-block radius for fire-tagged projectiles → `setRemainingFireTicks(0)`.
3. **Blaze/magma scan:** 8-block radius. For each, deal `blazeDamageAtDistance(dist)` per second (divide by 20 for per-tick). Add 0.5 heat per blaze damaged.
4. **Snow golem scan:** 8-block radius. Apply Regeneration with distance-scaled amplifier.
5. **Heat check:** If heat >= threshold → fire damage to player, reset heat. Respect cooldown.

### Testable Seams

```java
static float blazeDamageAtDistance(double distance) {
    return (float) Math.max(0, 2.0 * (1.0 - distance / 8.0));
}
static float heatThreshold(float currentHP, float maxHP) {
    return 5.0f * (currentHP / maxHP);
}
static int cooldownTicks(float currentHP, float maxHP) {
    return (int) (20.0f * (1.0f - currentHP / maxHP));
}
static boolean shouldTriggerFireDamage(float heat, float threshold) {
    return heat >= threshold;
}
static int snowGolemRegenAmplifier(double distance) {
    return (int) Math.max(0, 3.0 * (1.0 - distance / 8.0));
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `blazeDamage_distance0` | `blazeDamageAtDistance(0)` == 2.0 |
| `blazeDamage_distance4` | `blazeDamageAtDistance(4)` == 1.0 |
| `blazeDamage_distance8` | `blazeDamageAtDistance(8)` == 0.0 |
| `blazeDamage_beyond8` | `blazeDamageAtDistance(10)` == 0.0 |
| `heatThreshold_fullHP` | `heatThreshold(20, 20)` == 5.0 |
| `heatThreshold_halfHP` | `heatThreshold(10, 20)` == 2.5 |
| `cooldown_fullHP` | `cooldownTicks(20, 20)` == 0 |
| `cooldown_halfHP` | `cooldownTicks(10, 20)` == 10 |
| `cooldown_noHP` | `cooldownTicks(1, 20)` == 19 |
| `snowGolem_distance0` | `snowGolemRegenAmplifier(0)` == 3 |
| `snowGolem_distance8` | `snowGolemRegenAmplifier(8)` == 0 |

## Rendering

- Frost particles trailing the player as they walk
- Snowflake burst particles when extinguishing fires
- Cold aura shimmer around the player

## Complexity

Hard — many interacting systems per tick (fire, projectiles, blazes, golems, heat), proximity scanning, heat state machine.
