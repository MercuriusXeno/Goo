---
id: subplan-blaze
title: "Blaze — Explosion Chain"
type: subplan
parent: plan-world-effects
phase: 2
status: consumed
created: 2026-04-02
updated: 2026-04-02
purpose: DESIGN-TYPES "Explosion in 3/5/7/9 m³ area, max 4 chain." First chain profile implementation.
---

# Blaze — Explosion Chain

## Design Source

DESIGN-TYPES: "Explosion in 3/5/7/9 m³ area, max 4 chain."

## Current State

`WorldEffects.blazeExplosion()` fires a single 3.0f explosion instantly. No stacking, no fuse.

## Target

Register a `ChainProfile` for `GooType.BLAZE`:
- **Fuse:** 10 ticks
- **Max stacks:** 4
- **Range formula:** `EffectMath::computeExplosionRadius` (1 + 2n → 3, 5, 7, 9)
- **Executor:** `level.explode(null, x, y, z, range, TNT)`

| Stacks | Radius |
|--------|--------|
| 1 | 3 |
| 2 | 5 |
| 3 | 7 |
| 4 | 9 |

## Implementation

### New file: `effect/ChainProfiles.java`

Central registration class for all chain profiles. Called during mod init. Blaze is the first entry:

```java
ChainProfile.register(GooType.BLAZE, new ChainProfile(
    10, 4,
    EffectMath::computeExplosionRadius,  // int → int, but explosion needs float — cast in executor
    (level, pos, range, stacks) -> {
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            (float) range, Level.ExplosionInteraction.TNT);
    }
));
```

### Modified: `WorldEffects.blazeExplosion()`

Replace one-shot with find-or-create:
1. `EffectStackFinder.findAt(level, pos, ChainMarkerEffect.class)` filtered by `gooType == BLAZE`
2. If found → `tryStack()`
3. If not found → spawn `ChainMarkerEffect` via `GooEntities.CHAIN_MARKER`, call `initChain(pos, BLAZE)`

### Modified: `en_us.json`

Add `entity.goo.chain_marker` display name (shared by all chain types).

## Testable Seams

Already tested in `EffectMathTest.ExplosionRadius` (4 cases). No new seams needed — the executor is a one-liner delegating to vanilla `explode()`.

## Assets

None. Vanilla explosion handles visuals and sound.

## Verification

- `./gradlew gooTest` passes (existing range math tests)
- `./gradlew build` compiles
- In-game: throw 1 blaze blob → 3-radius explosion after 0.5s. Rapid-throw 4 → 9-radius.
