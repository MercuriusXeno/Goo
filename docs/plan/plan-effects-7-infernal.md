---
id: plan-effects-7-infernal
title: "Effect 7: Blaze — Infernal"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 7: Blaze — Infernal

**Class:** `effect/brew/InfernalEffect.java` | **Category:** BENEFICIAL | **Color:** `0xFF6600`

## Design Spec

> "Infernal - health converts to blaze hearts. Ash over 2n seconds (n=potency). Ash hearts take 2x damage. Water/snow/ice/frost goo = instant ash. Fire damage heals and reignites."
> — DESIGN-TYPES.md, Blaze brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Health converts to blaze hearts" — all HP? Gradually? Rate? | All current HP converts instantly on application. Store as `blazeHP` in data attachment, set real HP to 1 (minimum alive). |
| 2 | "Ash over 2n seconds" — per-heart decay or bulk? | Per-heart. One blaze heart (2 HP) decays to ash every `2*LVL` seconds. At LVL 1: one heart ashes every 2s. At LVL 3: every 6s. Higher level = slower decay = longer blaze phase. |
| 3 | "Ash hearts take 2x damage" — to player or per-heart? | Incoming damage multiplied by `1 + ashRatio`. At 100% ash: 2x damage. At 50% ash: 1.5x. Proportional scaling. |
| 4 | "Water/snow/ice/frost goo = instant ash" — rain counts? | Yes. `entity.isInWaterOrRain()` triggers instant all-ash. Also frost goo contact effect. |
| 5 | "Fire damage heals and reignites" — restores ash to blaze? Heals real HP? | Converts ash hearts back to blaze hearts, 1 HP of fire damage = 1 HP of ash→blaze conversion. Does not grant new hearts beyond original pool. |
| 6 | What happens when all hearts (blaze+ash) are consumed by damage? | Entity dies. The blaze/ash pool IS the entity's effective HP. |
| 7 | What happens on effect removal (milk, expiry)? | Remaining blaze+ash HP restored as real HP. If pool is 0, entity is already dead. |

## Prerequisites

- Infra A (foundation)
- Infra B (Custom Heart Overlay)
- Custom data attachment for InfernalHeartData

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/InfernalEffect.java` | HP conversion, decay, fire healing, water vulnerability |
| `effect/brew/InfernalHeartData.java` | Record: `int blazeHP, int ashHP, int decayTimer` + tick logic |
| `test/.../effect/brew/InfernalEffectTest.java` | Pure-function tests |
| `test/.../effect/brew/InfernalHeartDataTest.java` | State machine tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add INFERNAL holder |
| `registry/GooPotions.java` | BLAZE case: use custom effect |
| `registry/GooDataComponents.java` | Register InfernalHeartData attachment (or use entity data attachment) |
| `en_us.json` | Add `"effect.goo.infernal": "Infernal"` |

### InfernalHeartData (state machine)

```java
record InfernalHeartData(int blazeHP, int ashHP, int decayTimer) {
    /** Tick the decay timer. Returns updated data, possibly with one blaze→ash conversion. */
    InfernalHeartData tick(int decayInterval) { ... }
    /** Convert all remaining blaze to ash (water contact). */
    InfernalHeartData instantAsh() { ... }
    /** Convert ash back to blaze from fire healing. */
    InfernalHeartData reignite(int amount) { ... }
    /** Apply damage, consuming ash first (at 2x rate), then blaze. */
    InfernalHeartData applyDamage(int damage, boolean isFire) { ... }
    /** Total remaining HP. */
    int totalHP() { return blazeHP + ashHP; }
    /** Ratio of ash to total (0.0 = all blaze, 1.0 = all ash). */
    float ashRatio() { ... }
}
```

### InfernalEffect

**MobEffect overrides:**
- `onEffectAdded()`: store `entity.getHealth()` as `blazeHP` in data attachment. Set `entity.setHealth(1)`. Set `entity.setInvulnerable(false)` (ensure entity can still take damage through the custom system).
- `applyEffectTick()` every tick:
  - Tick the decay timer
  - Check `entity.isInWaterOrRain()` → instant ash
  - Sync data attachment
- `onMobHurt()`:
  - If fire damage: reignite ash hearts, cancel the damage (fire heals)
  - If non-fire: apply ash damage multiplier
  - Update entity health/death based on remaining pool
- `onMobRemoved()`: restore remaining blaze+ash as real HP

### Testable Seams

```java
static int decayInterval(int amplifier) { return 40 * level(amplifier); } // 2*LVL seconds in ticks
static float ashDamageMultiplier(int blazeHP, int ashHP) {
    if (blazeHP + ashHP == 0) return 1.0f;
    return 1.0f + (float) ashHP / (blazeHP + ashHP);
}
static int fireHealAmount(float fireDamage, int currentAshHP) {
    return Math.min((int) fireDamage, currentAshHP);
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `decayInterval_level1` | `decayInterval(0)` == 40 (2 seconds) |
| `decayInterval_level3` | `decayInterval(2)` == 120 (6 seconds) |
| `ashMultiplier_allBlaze` | `ashDamageMultiplier(10, 0)` == 1.0 |
| `ashMultiplier_allAsh` | `ashDamageMultiplier(0, 10)` == 2.0 |
| `ashMultiplier_halfAsh` | `ashDamageMultiplier(5, 5)` == 1.5 |
| `fireHeal_partial` | `fireHealAmount(3, 5)` == 3 |
| `fireHeal_capped` | `fireHealAmount(10, 3)` == 3 |
| `heartData_tick_decay` | `new InfernalHeartData(10, 0, 1).tick(40)` decrements timer |
| `heartData_tick_converts` | Timer reaches 0 → one blaze→ash conversion |
| `heartData_instantAsh` | All blaze becomes ash |
| `heartData_reignite` | Ash converts back to blaze |

## Rendering

- Two heart types via Infra B:
  - Blaze hearts: orange/flame texture (`blaze_heart.png`)
  - Ash hearts: gray/crumbling texture (`ash_heart.png`)
- Flame particles while blaze hearts active
- Smoke/ember particles during ash decay
- Most complex heart rendering of all effects

## Complexity

Hard — complex state machine, custom HP management, multiple damage type interactions, bidirectional state transitions (blaze↔ash). Most complex single effect.
