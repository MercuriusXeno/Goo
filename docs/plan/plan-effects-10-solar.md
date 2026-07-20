---
id: plan-effects-10-solar
title: "Effect 10: Glow — Solar"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 10: Glow — Solar

**Class:** `effect/brew/SolarEffect.java` | **Category:** BENEFICIAL | **Color:** `0xFFD700`

## Design Spec

> "Solar - nearby undead treat you as sunlight. Highlighted, aggro from further."
> — DESIGN-TYPES.md, Glow brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Nearby" radius not specified. | 8 + 4*LVL blocks. LVL 1 = 12, LVL 2 = 16. |
| 2 | "Treat as sunlight" — burn or behavioral? | Burn. Undead take 1 HP/s fire damage as if in direct sunlight. This is the offensive utility. |
| 3 | "Highlighted" — vanilla Glowing? | Yes. Apply Glowing to the player for the duration. Tradeoff: visible through walls to all mobs. |
| 4 | "Aggro from further" — all mobs or just undead? How much? | The Glowing effect itself IS the aggro increase. Glowing entities are visible through walls, effectively extending aggro range for all mobs. No separate mechanic needed. |

## Prerequisites

- Infra A (foundation)
- Infra C (Proximity Scanning)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/SolarEffect.java` | Undead burn + Glowing self-application |
| `test/.../effect/brew/SolarEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add SOLAR holder |
| `registry/GooPotions.java` | GLOW case: use custom effect |
| `en_us.json` | Add `"effect.goo.solar": "Solar"` |

### SolarEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: every 20 ticks (1 second)
- `applyEffectTick()`:
  - Scan for undead (`entity.isInvertedHealAndHarm()`) within `sunlightRadius(amplifier)`
  - Deal 1 HP fire damage to each undead found
  - Ensure self has Glowing (re-apply if stripped)
- `onEffectAdded()`: apply Glowing to self

**Glowing persistence:** Apply Glowing with duration matching Solar's remaining duration. Re-apply on each tick in case it's dispelled.

### Testable Seams

```java
static int sunlightRadius(int amplifier) {
    return 8 + 4 * level(amplifier);
}
static boolean isUndead(LivingEntity entity) {
    return entity.isInvertedHealAndHarm();
}
static float sunlightDamage() {
    return 1.0f; // 1 HP per second, applied every 20 ticks
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `sunlightRadius_level1` | `sunlightRadius(0)` == 12 |
| `sunlightRadius_level2` | `sunlightRadius(1)` == 16 |
| `sunlightRadius_level4` | `sunlightRadius(3)` == 24 |
| `sunlightDamage` | `sunlightDamage()` == 1.0 |

## Rendering

- Golden glow particles radiating outward from player
- Undead within range: brief flame particle on each damage tick

## Complexity

Simple — proximity scan + periodic damage. Most straightforward effect after foundation.
