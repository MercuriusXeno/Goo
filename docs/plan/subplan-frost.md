---
id: subplan-frost
title: "Frost -- Instant Freeze + Melt-Resist Field"
type: subplan
parent: plan-world-effects
phase: 2
status: consumed
created: 2026-04-02
updated: 2026-04-02
purpose: Instant freeze with anti-melt field that prevents block updates in the radius for a duration.
---

# Frost -- Instant Freeze + Melt-Resist Field

## Design (user-specified 2026-04-02)

Frost is an instant effect, not a fuse chain. Key behaviors:

1. **Hitting liquid resolves instantly.** No fuse delay when the blob lands on water/lava. The freeze happens on impact.
2. **Freeze field persists.** After freezing, a field prevents frozen blocks from updating (melting) for a duration of `2 * radius` seconds.
3. **Stacking on the field.** While the field exists, additional frost blobs landing in it stack up to 4 levels, increasing radius. Stacking recomputes the duration.
4. **Breaks plants.** Vines, lily pads, grass, flowers, etc. are destroyed in the freeze radius.

## Radii and Duration

| Stacks | Radius | Duration (2r seconds) |
|--------|--------|-----------------------|
| 1 | 3 | 6s (120 ticks) |
| 2 | 4 | 8s (160 ticks) |
| 3 | 5 | 10s (200 ticks) |
| 4 | 6 | 12s (240 ticks) |

Range formula: `2 + stacks` (3, 4, 5, 6). Update `EffectMath.computeFreezeRadius` accordingly (old formula was `3 + 2n`).

## Block Conversions

| Source | Target |
|--------|--------|
| `WATER` | `ICE` |
| `LAVA` | `OBSIDIAN` |
| `FIRE`, `SOUL_FIRE` | `AIR` |
| Plants (vines, lily pads, grass, tall grass, flowers, etc.) | `AIR` (drop nothing) |

## Architecture

Frost does NOT use the `ChainProfile` fuse system. It's instant. But it does need a persistent block for the melt-resist field.

### Option A: New block type (`FrostFieldBlock`)

A separate invisible block entity that ticks down a duration, suppresses neighbor updates in its radius, and removes itself on expiry. Stacking replaces it with updated radius/duration.

### Option B: Reuse `ChainMarkerBlock` with a "persistent mode"

Add a mode flag to `ChainMarkerBlockEntity` where instead of counting down a fuse to detonate, it counts down a duration and suppresses updates. The executor fires immediately on placement (not on fuse expiry).

**Recommendation: Option A.** Frost's lifecycle (execute immediately, then persist as a field) is fundamentally different from the fuse-then-detonate chain pattern. Forcing it into `ChainMarkerBlock` would require branching logic that makes the chain marker harder to reason about.

### Melt-resist mechanism

Ice melts via scheduled random ticks. The frost field must **cancel random ticks** for ice/obsidian blocks within its radius so they can't melt while the field is active.

**Mechanism: cancel random ticks via event.** Listen to NeoForge's `BlockEvent` (or the 26.1 equivalent random tick event). When a block at a position inside an active frost field's radius receives a random tick, cancel it. The next session should investigate which event fires before a random tick executes in 26.1 and whether it's cancellable. Candidates: `BlockEvent.randomTick` or a mixin on `ServerLevel.tickChunk` if no event exists.

**Fallback if no cancellable event exists:** Replace ice with packed ice (which doesn't melt) for the duration, then swap back to regular ice on field expiry. Less elegant but guaranteed to work without mixins.

### Instant resolution on liquid

`WorldEffects.frostFreeze()` checks if the hit block is water or lava. If so, skip fuse/chain entirely -- execute freeze immediately and place the field block at the impact pos.

## Implementation

### Update `EffectMath`

Change `computeFreezeRadius(int stacks)` to `2 + stacks` (was `3 + 2 * stacks`).

Add `computeFrostDuration(int radius)` returning `2 * radius * 20` ticks.

### New: `block/FrostFieldBlock.java`

Invisible, non-collidable block. Ticks via block entity. `animateTick` spawns snowflake particles. No selection shape (fully invisible).

### New: `block/FrostFieldBlockEntity.java`

Fields: `radius`, `stacks`, `maxStacks`, `durationRemaining`.

- `initField(int radius, int stacks)` -- sets radius and computes duration.
- `tryStack()` -- increments stacks, recomputes radius and duration.
- `serverTick()` -- decrements duration. On expiry, removes self.
- `containsPos(BlockPos)` -- returns true if a given pos is within this field's radius. Called by the random tick cancellation listener.

### New: `effect/FrostExecutor.java`

Static methods:
- `execute(ServerLevel, BlockPos, int radius)` -- spherical freeze + plant destruction + particles/sound.
- `isPlant(BlockState)` -- predicate for destructible plants.

### Modified: `WorldEffects.frostFreeze()`

```
1. Execute freeze immediately (FrostExecutor.execute)
2. Place FrostFieldBlock at the blob landing pos
3. If FrostFieldBlock already exists at pos, stack onto it
```

### Register

- `GooBlocks.FROST_FIELD`
- `GooBlockEntities.FROST_FIELD`
- BER: just `animateTick` snowflakes, no custom geometry needed (invisible field)
- `en_us.json`: `block.goo.frost_field`

## Testable Seams

- `EffectMath.computeFreezeRadius` -- update existing tests
- `EffectMath.computeFrostDuration` -- new tests
- `FrostExecutor.isPlant` -- new pure predicate tests (if framework-free, otherwise skip)

## Verification

- `./gradlew gooTest` + `./gradlew build`
- In-game: throw frost blob at water -> instant freeze, r=3, ice persists for 6s even in warm biome/desert
- Stack 4 blobs -> r=6, 12s resist
- Plants in radius destroyed
- After duration expires, ice begins melting naturally via normal random ticks
- Verify: ice does NOT melt while field is active (critical test case)
