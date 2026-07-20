---
id: subplan-metal
title: "Metal -- Caltrop Field"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent caltrop field that damages entities standing in it. Stacking increases radius.
---

# Metal -- Caltrop Field

## Design Source

DESIGN-TYPES: "Explodes into a field of caltrops. Caltrops deal damage as long as you're standing in them."

## Pattern

**Unique block.** Not a fuse chain or pulsing node. Instant placement of a `CaltropFieldBlock` that deals continuous contact damage and persists for a duration.

## Parameters

| Field | Value |
|---|---|
| Max stacks | 4 |
| Radius | 2 + stacks (3, 4, 5, 6) |
| Duration | 10 seconds per stack (200, 400, 600, 800 ticks) |
| Damage | 2.0 HP per second (1.0 per 10 ticks) |
| BER | Slime-core orb, metal-tinted, breathes, aim-highlight |
| Ambient particles | CRIT |

## Architecture

### New: `block/CaltropFieldBlock.java` + `block/CaltropFieldBlockEntity.java`

Similar lifecycle to `FrostFieldBlock`: instant effect on placement, persistent field with duration countdown. The BE ticks and damages entities within its radius every 10 ticks.

- `initField(int stacks)` -- sets radius, computes duration.
- `tryStack()` -- increments stacks, recomputes radius/duration.
- `serverTick()` -- decrements duration, damages entities in radius, removes on expiry.

### Executor: inline in `WorldEffects`

On blob impact:
1. Place or stack `CaltropFieldBlock` at the hit position.
2. Spawn CRIT particle burst.
3. Play metallic scatter sound.

No separate executor file needed -- the damage logic lives in the BE's tick.

## Testable Seams

- Radius formula `2 + stacks` in `EffectMath.computeCaltropRadius`.
- Duration formula `stacks * 200` in `EffectMath.computeCaltropDuration`.

## Verification

- Throw 1 metal blob -> caltrop field appears (r=3), standing in it deals damage, fades after 10s.
- Stack 4 -> r=6, 40s duration. BER orb grows with stacks.
