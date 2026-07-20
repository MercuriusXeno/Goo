---
id: plan-effects-2-mineral-sense
title: "Effect 2: Crystal — Mineral Sense"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 2: Crystal — Mineral Sense

**Class:** `effect/brew/MineralSenseEffect.java` | **Category:** BENEFICIAL | **Color:** `0x4FC1E9`

## Design Spec

> "Mineral Sense - detect ores through walls via unique particles. Potency increases range."
> — DESIGN-TYPES.md, Crystal brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | What counts as "ore"? No tag specified. | Use `c:ores` common tag + custom `goo:mineral_sense_targets` for modpack extension. |
| 2 | Base range and per-level scaling not specified. | Base 16 blocks, +8/LVL. LVL 1 = 16, LVL 2 = 24, LVL 3 = 32. |
| 3 | "Unique particles" — one type for all ores, or per-ore? | Single particle type tinted to the ore block's map color. Visual distinction without 50+ particle types. |
| 4 | Server-side or client-side scanning? | Client-side only. The effect is purely visual — no gameplay mechanic depends on the scan results. |

## Prerequisites

- Infra A (foundation)
- Infra E (Client Block Scanning): `client/render/BlockScanRenderer.java`

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/MineralSenseEffect.java` | Nearly-empty server MobEffect (just registers, no tick) |
| `client/render/BlockScanRenderer.java` | Client-side block scanning + particle spawning (Infra E) |
| `test/.../effect/brew/MineralSenseEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add MINERAL_SENSE holder |
| `registry/GooPotions.java` | CRYSTAL case: use custom effect |
| `en_us.json` | Add `"effect.goo.mineral_sense": "Mineral Sense"` |
| `client/GooClientSetup.java` | Register BlockScanRenderer tick handler |

### BlockScanRenderer (Infra E)

Client-side system reusable by Shroom (Mycelial Network).

**Architecture:**
- Per-tick client handler subscribed to `ClientTickEvent.Post`
- Checks if local player has Mineral Sense (or Mycelial Network) active
- Scans 1/8 of the sphere per tick (octant rotation) to spread load across 8 ticks
- For each matching block, spawn a tinted particle at its center
- Throttle: skip if FPS < 30

**Octant scanning:**
```java
static int octantForTick(int tickCount) {
    return tickCount % 8;
}
```

Each octant covers one quadrant of x/y/z signs. A full scan completes every 8 ticks (0.4s).

**Block matching:**
```java
static boolean isOreBlock(BlockState state) {
    return state.is(BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "ores")))
        || state.is(GooTags.MINERAL_SENSE_TARGETS);
}
```

### MineralSenseEffect

Minimal server-side class:
- Constructor: BENEFICIAL, crystal color, no attribute modifiers
- `shouldApplyEffectTickThisTick()`: return false (no server tick needed)
- All real work happens client-side in BlockScanRenderer

### Testable Seams

```java
/** Block scanning radius in blocks. */
static int scanRadius(int amplifier) {
    return 16 + 8 * level(amplifier);
}

/** Which octant to scan this tick (0-7). */
static int octantForTick(int tickCount) {
    return tickCount % 8;
}

/** Whether a block position falls in the given octant relative to center. */
static boolean isInOctant(int dx, int dy, int dz, int octant) {
    boolean xPos = (octant & 1) != 0;
    boolean yPos = (octant & 2) != 0;
    boolean zPos = (octant & 4) != 0;
    return (dx >= 0) == xPos && (dy >= 0) == yPos && (dz >= 0) == zPos;
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `scanRadius_level1` | `scanRadius(0)` == 16 |
| `scanRadius_level2` | `scanRadius(1)` == 24 |
| `scanRadius_level4` | `scanRadius(3)` == 40 |
| `octantForTick_cycles` | `octantForTick(0)` == 0, `octantForTick(7)` == 7, `octantForTick(8)` == 0 |
| `isInOctant_origin` | `isInOctant(0, 0, 0, 0)` == true (zero is positive side) |
| `isInOctant_negativesInOctant0` | `isInOctant(-1, -1, -1, 0)` == false |

## Rendering

- Tinted particles at ore positions, visible through walls
- Particle color = ore block's `MaterialColor.col` (map color)
- Particle lifetime: 8 ticks (fast flicker so new scan results replace old)
- Performance: octant rotation limits to ~1/8 of total blocks per tick

## Complexity

Medium — server-side is trivial, client-side block scanning is the hard part.
