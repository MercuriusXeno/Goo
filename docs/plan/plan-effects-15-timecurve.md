---
id: plan-effects-15-timecurve
title: "Effect 15: Aeon — Timecurve"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 15: Aeon — Timecurve

**Class:** `effect/brew/TimecurveEffect.java` | **Category:** BENEFICIAL | **Color:** `0xDAA520`

## Design Spec

> "Timecurve - slow mobs relative to proximity. Effect starts at ~8 blocks, stronger approaching 0."
> — DESIGN-TYPES.md, Aeon brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | Max slowness at distance 0 — full time-stop or very slow? | 90% slowness (near-stop). Full time-stop would freeze AI making mobs unkillable — not fun. |
| 2 | Does LVL increase the 8-block radius? | Yes. Radius = 8*LVL. LVL 1 = 8, LVL 2 = 16, LVL 3 = 24. |
| 3 | Falloff curve shape? | Linear: `strength = 0.9 * (1 - distance / maxRadius)`. Predictable and testable. |
| 4 | Affects all mobs or just hostile? Other players? | All mobs except players. PvP time-stop is overpowered and unfun. |
| 5 | How does slowness map to vanilla Slowness amplifier? | Map 0.0–0.9 strength to amplifier 0–4. Amplifier 0 = 20% slow, 4 = 100% slow. Formula: `amplifier = (int)(strength * 5) - 1`, clamped 0–4. |
| 6 | Cleanup — what happens when effect expires or mobs leave range? | Remove applied Slowness from all tracked entities on effect removal. Track affected entities in a WeakHashSet. |

## Prerequisites

- Infra A (foundation)
- Infra C (Proximity Scanning)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/TimecurveEffect.java` | Proximity-based graduated slowness |
| `test/.../effect/brew/TimecurveEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add TIMECURVE holder |
| `registry/GooPotions.java` | AEON case: use custom effect |
| `en_us.json` | Add `"effect.goo.timecurve": "Timecurve"` |

### TimecurveEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: every 20 ticks (re-evaluate slowness levels)
- `applyEffectTick()`:
  1. Scan entities within `maxRadius(amplifier)` using Infra C
  2. For each non-player LivingEntity:
     - Calculate distance to bearer
     - Calculate `slownessStrength(distance, maxRadius)`
     - Map to `slownessAmplifier(strength)`
     - Apply/update Slowness with that amplifier (duration = 30 ticks to auto-expire between checks)
  3. Entities that left range: their 30-tick Slowness expires naturally
- `onMobRemoved()`: no explicit cleanup needed — applied Slowness durations are short (30 ticks) and expire on their own

**Short-duration Slowness approach:** Instead of tracking affected entities, apply Slowness with 30-tick duration (1.5 seconds). If the entity is still in range, it gets re-applied on the next 20-tick check. If they leave range, it naturally expires in ~10 ticks. Clean and stateless.

### Testable Seams

```java
static int maxRadius(int amplifier) {
    return 8 * level(amplifier);
}

static double slownessStrength(double distance, double maxRadius) {
    if (distance >= maxRadius) return 0.0;
    return 0.9 * (1.0 - distance / maxRadius);
}

static int slownessAmplifier(double strength) {
    if (strength <= 0) return -1; // no effect
    return Math.min(4, (int) (strength * 5));
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `maxRadius_level1` | `maxRadius(0)` == 8 |
| `maxRadius_level2` | `maxRadius(1)` == 16 |
| `maxRadius_level3` | `maxRadius(2)` == 24 |
| `strength_distance0` | `slownessStrength(0, 8)` == 0.9 |
| `strength_distance4` | `slownessStrength(4, 8)` ≈ 0.45 |
| `strength_distance8` | `slownessStrength(8, 8)` == 0.0 |
| `strength_beyondRadius` | `slownessStrength(10, 8)` == 0.0 |
| `amplifier_maxStrength` | `slownessAmplifier(0.9)` == 4 |
| `amplifier_halfStrength` | `slownessAmplifier(0.45)` == 2 |
| `amplifier_zeroStrength` | `slownessAmplifier(0.0)` == -1 |
| `amplifier_tinyStrength` | `slownessAmplifier(0.1)` == 0 |

## Rendering

- Time-distortion particles around affected mobs (amber clock/sand particles)
- Optional: visual "time bubble" sphere around the player showing the radius
- Slowness-affected mobs already show the vanilla slowness particle effect

## Complexity

Medium — proximity scan + graduated attribute application. The short-duration Slowness approach eliminates cleanup complexity. Stateless per-tick design.
