---
id: subplan-crystal
title: "Crystal -- Shard Field"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent field of suspended glass shards that shred moving entities. Fades over time and damage dealt.
---

# Crystal -- Shard Field

## Design Source

DESIGN-TYPES: "Suspended glass shards shred anything moving through without sneaking. Fades over time and damage dealt."

## Pattern

**Unique block.** Instant placement of a `ShardFieldBlock` that damages moving (non-sneaking) entities. Two depletion paths: time decay and damage budget.

## Parameters

| Field | Value |
|---|---|
| Max stacks | 4 |
| Radius | 2 + stacks (3, 4, 5, 6) |
| Duration | 15 seconds per stack (300, 600, 900, 1200 ticks) |
| Damage budget | 20 HP per stack (20, 40, 60, 80 total) |
| Damage per hit | 3.0 HP (ticks every 10 ticks for entities in motion) |
| BER | Slime-core orb, crystal-tinted, breathes, aim-highlight |
| Ambient particles | DAMAGE_INDICATOR (sparse) |

## Architecture

### New: `block/ShardFieldBlock.java` + `block/ShardFieldBlockEntity.java`

Similar to `CaltropFieldBlock` but with a damage budget that depletes alongside the timer. Whichever runs out first kills the field.

- `initField(int stacks)` -- sets radius, duration, damage budget.
- `tryStack()` -- increments stacks, recomputes all three.
- `serverTick()` -- decrements duration, checks entities in radius. Moving + not sneaking = hit. Subtracts from damage budget. Removes on expiry or budget depletion.

### Sneak exemption

`entity.isCrouching()` -- entities that are sneaking take no damage. This is the core design differentiator.

### Motion detection

Compare `entity.getDeltaMovement().lengthSqr()` against a threshold (e.g. 0.001). Stationary entities are safe.

## Testable Seams

- `isMovingAndNotSneaking(deltaMovement, isCrouching)` -- pure predicate.
- Radius/duration/budget formulas in `EffectMath`.

## Verification

- Throw 1 crystal blob -> shard field, walking through it deals damage, sneaking is safe.
- Stand still -> safe. Sprint through -> shredded. Field disappears when budget or timer runs out.
