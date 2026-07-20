---
id: subplan-rock
title: "Rock — Implosion"
type: subplan
parent: plan-world-effects
phase: 2
status: consumed
created: 2026-04-02
updated: 2026-04-02
purpose: Breaks pure-rock blocks in a 3x3xn² column along `placedFace.getOpposite()` (into the surface the marker was placed on) after delay. Uses GooValueRegistry for purity check.
---

# Rock — Implosion

## Design Source

DESIGN-TYPES: "Implosion breaks pure rock in 3×3×(n²) area after delay. Depth stacks exponentially, max 5 (25 deep)."

## Current State

`WorldEffects.rockImplosion()` breaks blocks in a 3×3×3 column instantly using a hardcoded `isRockType()` predicate (stone/cobble/deepslate/etc). No delay, no stacking, no goo-based purity check.

## Key Design Clarification

**"Pure rock"** means the block's goo value contains only rock and/or crystal types — not a hardcoded block list. Blocks like granite and andesite inherit crystal from quartz ancestry, so crystal is allowed. Any other goo type (metal, leaf, etc.) disqualifies the block.

- `{ROCK: 3}` → breakable (pure rock)
- `{ROCK: 2, CRYSTAL: 1}` → breakable (granite/andesite style)
- `{CRYSTAL: 2}` → breakable (quartz)
- `{ROCK: 2, METAL: 1}` → NOT breakable (ore)

This requires `GooValueRegistry` to check block composition at runtime.

## Target

Register a `ChainProfile` for `GooType.ROCK`:
- **Fuse:** 20 ticks (1 second — longer than other chains)
- **Max stacks:** 5
- **Range formula:** `EffectMath::computeImplosionDepth` (n² → 1, 4, 9, 16, 25)
- **Executor:** break pure-rock blocks in a 3×3×depth column along `placedFace.getOpposite()` (into the surface the marker was stuck to).

| Stacks | Depth (n²) |
|--------|-----------|
| 1 | 1 |
| 2 | 4 |
| 3 | 9 |
| 4 | 16 |
| 5 | 25 |

Note: the `range` parameter from the profile represents **depth**, not radius. The 3×3 footprint is fixed. The executor interprets `range` as the column depth.

## Implementation

### In `ChainProfiles.java`

```java
ChainProfile.register(GooType.ROCK, new ChainProfile(
    20, 5,
    EffectMath::computeImplosionDepth,
    RockExecutor::execute
));
```

### New file: `effect/RockExecutor.java`

Static executor. On fire:

1. Let `dir = placedFace.getOpposite()`. Iterate a 3×3 footprint in the plane perpendicular to `dir`, stepping `range` cells along `dir`.
2. For each position: look up the block's item form → `GooValueRegistry.lookup()` → check if the value contains only rock and/or crystal goo types.
3. If rock-compatible → `level.destroyBlock(target, true)` (drops items).
4. Play `GENERIC_EXPLODE` sound.

### Rock-compatible predicate

Blocks like granite and andesite inherit crystal goo from their quartz ancestry. The implosion should break these too. The predicate allows rock and/or crystal — any other goo type disqualifies the block.

```java
private static final Set<GooType> ROCK_COMPATIBLE = EnumSet.of(GooType.ROCK, GooType.CRYSTAL);

static boolean isRockCompatible(GooValue value) {
    if (value == null || value.isEmpty()) return false;
    return ROCK_COMPATIBLE.containsAll(value.getAll().keySet());
}
```

This is testable — `GooValue` is a plain data class. Add to `EffectMath` or `RockExecutor`.

### Delete: `WorldEffects.isRockType()`

The hardcoded block predicate becomes dead code. Remove it.

### Modified: `WorldEffects.rockImplosion()`

Replace with `placeOrStackChain(level, pos, targetFace, GooType.ROCK)`.

## Testable Seams

| Seam | Location | Tests |
|------|----------|-------|
| `computeImplosionDepth` | `EffectMath` | Already in `EffectMathTest.ImplosionDepth` (5 cases) |
| `isRockCompatible(GooValue)` | New | Rock-only → true, rock+crystal → true, rock+metal → false, crystal-only → true, empty → false, null → false |

## Assets

None. Invisible marker. Reuse `GENERIC_EXPLODE` sound and vanilla block-break particles.

## Verification

- `./gradlew gooTest` — depth math + isPureRock tests pass
- `./gradlew build` compiles
- In-game: throw 1 rock blob → 3×3×1 break after 1s. Rapid-throw 5 → 3×3×25 column. Breaks blocks composed of rock and/or crystal goo only (stone, granite, andesite, etc). Blocks with other goo types (e.g., ore with metal) survive.
