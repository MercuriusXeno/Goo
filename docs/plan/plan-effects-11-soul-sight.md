---
id: plan-effects-11-soul-sight
title: "Effect 11: Hex — Soul Sight"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 11: Hex — Soul Sight

**Class:** `effect/brew/SoulSightEffect.java` | **Category:** BENEFICIAL | **Color:** `0x2C3E50`

## Design Spec

> "Soul sight - sense mob auras through walls. Red/crackling = hostile, blue/wavy = peaceful. Angers endermen."
> — DESIGN-TYPES.md, Hex brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | Sense range not specified. | 16 + 8*LVL blocks. LVL 1 = 24, LVL 2 = 32. Generous range since it's purely informational (no damage). |
| 2 | "Angers endermen" — all in range auto-aggro, or only when looked at? | All in range auto-aggro. Soul sight radiates psychic disturbance. Endermen sense it regardless of eye contact. |
| 3 | "Auras" — particles or outline rendering? | Start with colored particles at entity positions (visible through walls). Red crackling for hostile, blue wavy for peaceful. Upgrade to team-color Glowing outlines later if desired. |
| 4 | Does Soul Sight reveal invisible entities? | Yes. The aura is soul-based, not optical. Invisible entities still have visible auras. |
| 5 | Does it distinguish neutral mobs (wolves, iron golems)? | Neutral = blue (peaceful). Only `Monster` subclasses get red. |

## Prerequisites

- Infra A (foundation)
- Infra C (Proximity Scanning)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/SoulSightEffect.java` | Entity scanning + Glowing + enderman aggro |
| `test/.../effect/brew/SoulSightEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add SOUL_SIGHT holder |
| `registry/GooPotions.java` | HEX case: use custom effect |
| `en_us.json` | Add `"effect.goo.soul_sight": "Soul Sight"` |

### SoulSightEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: every 10 ticks
- `applyEffectTick()`:
  - Scan entities within `senseRadius(amplifier)` using Infra C
  - Apply Glowing to each detected entity (makes them visible through walls)
  - For each enderman in range: set attack target to the effect bearer
  - Track affected entities for cleanup on removal
- `onMobRemoved()`: remove Glowing from all entities we applied it to (if they're still in range and alive)

**Enderman aggro:** `if (entity instanceof EnderMan enderman) { enderman.setTarget(bearer); }`

**Client-side particles:** Subscribe to `ClientTickEvent.Post`. If local player has Soul Sight, spawn tinted particles at each Glowing entity's position:
- Red crackling particles for `entity instanceof Monster`
- Blue wavy particles for all others

### Testable Seams

```java
static int senseRadius(int amplifier) {
    return 16 + 8 * level(amplifier);
}
static int auraColor(LivingEntity entity) {
    return entity instanceof Monster ? 0xFF0000 : 0x0000FF;
}
static boolean isEnderman(LivingEntity entity) {
    return entity instanceof net.minecraft.world.entity.monster.EnderMan;
}
static boolean isHostile(LivingEntity entity) {
    return entity instanceof Monster;
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `senseRadius_level1` | `senseRadius(0)` == 24 |
| `senseRadius_level2` | `senseRadius(1)` == 32 |
| `senseRadius_level3` | `senseRadius(2)` == 40 |
| `auraColor_hostile` | Red (0xFF0000) for Monster instances |
| `auraColor_peaceful` | Blue (0x0000FF) for non-Monster |

## Rendering

- Red crackling particles at hostile mob positions (through walls)
- Blue wavy particles at peaceful mob positions (through walls)
- Particle visibility through walls via `RenderType.textSeeThrough()` or by spawning at client camera-relative positions

## Complexity

Medium — entity scanning is straightforward. Colored particle rendering through walls is the main challenge. Enderman aggro is trivial.
