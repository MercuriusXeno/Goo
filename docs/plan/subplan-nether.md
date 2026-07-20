---
id: subplan-nether
title: "Nether — Conversion Chain"
type: subplan
parent: plan-world-effects
phase: 2
status: consumed
created: 2026-04-02
updated: 2026-04-10
purpose: Converts blocks in area to goo blob items. Chain max 4, radius 3/5/7/9.
---

# Nether — Conversion Chain

## Design Source

DESIGN-TYPES: "Negative energy converts blocks in area to goo blobs (item form). Converts living goo to normal blobs. Chain max 4, radius 3/5/7/9 m³."

## Current State

`WorldEffects.netherConvert()` destroys blocks in a fixed r=3 sphere (hardness ≤ 3.0f), dropping vanilla items. No goo conversion, no stacking, no fuse. Sends `SOUL` particles.

## Target

Register a `ChainProfile` for `GooType.NETHER`:
- **Fuse:** 10 ticks
- **Max stacks:** 4
- **Range formula:** `EffectMath::computeNetherRadius` (1 + 2n → 3, 5, 7, 9)
- **Executor:** convert blocks to goo blob items, convert goo entities to blob items

| Stacks | Radius |
|--------|--------|
| 1 | 3 |
| 2 | 5 |
| 3 | 7 |
| 4 | 9 |

## Implementation

### In `ChainProfiles.java`

```java
ChainProfile.register(GooType.NETHER, new ChainProfile(
    10, 4,
    EffectMath::computeNetherRadius,
    NetherExecutor::execute
));
```

### New file: `effect/NetherExecutor.java`

Static executor. On fire:

1. **Block conversion:** Iterate spherical volume. For each non-air block with hardness ≤ 3.0f:
   - Get the block's item form: `new ItemStack(block.asItem())`
   - Look up goo value: `GooValueRegistry.lookup(itemStack)`
   - If goo value exists → for each `(GooType, amount)` entry, spawn a goo blob/omniblob item via `BlobStacks.createForOutput(type, amount)`. `GooValue` amounts are in microblobs (mB); pass them through verbatim.
   - If no goo value → `Block.dropResources()` (vanilla fallback)
   - Remove the block

2. **Effect block conversion:** Find all effect blocks (ChainMarkerBlock, FrostFieldBlock, PulsingNodeBlock) in radius.
   - For each: read goo type from the block entity, spawn 1 blob item of that type, then `level.removeBlock()`.

3. **FX:** `SOUL` particle burst (same as current).

### Integration: `GooValueRegistry`

Access path: `level.getServer()` → get the registry instance. The registry is loaded on server start and available via a static accessor or server data.

### Modified: `WorldEffects.netherConvert()`

Replace with `placeOrStackChain(level, pos, targetFace, GooType.NETHER)`.

## Testable Seams

Radius math already tested in `EffectMathTest.NetherRadius` (4 cases). Block→goo conversion is framework-coupled. The executor itself is integration-heavy.

## Open Questions

- **Hardness cap (≤ 3.0f):** Current placeholder. DESIGN-TYPES doesn't specify a hardness limit. Keep for now — revisit if it feels wrong in playtesting.
- **Blob spawn volume:** One blob/omniblob item per goo type in the block's value. `GooValue.get(type)` returns microblobs (mB) directly. Values are typically sub-blob (e.g. grass block = `rock: 1152 mB ≈ 1.1 blobs`), so output is usually an omniblob.

## Assets

None new. Reuse `SOUL` particles.

## Verification

- `./gradlew gooTest` passes
- `./gradlew build` compiles
- In-game: throw 1 nether blob → r=3 conversion. Blocks become goo blob items. Existing world effects in radius become blobs. Rapid-throw 4 → r=9.
