---
id: subplan-leaf
title: "Leaf -- Growth Pulsing Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent pulsing node that grows plants in a 5x5 area every 4 seconds. Each blob = one pulse.
---

# Leaf -- Growth Pulsing Node

## Design Source

DESIGN-TYPES: "Creates a growth pulsing node, pulsing every 4 seconds. Each blob gives an additional pulse. Plants within a 5x5 square centered around the node grow. This reach extends to any farmland within a 9x9 if the node is in water hydrating that farmland."

## Pattern

**Pulse node.** Shared with vital, shroom, hex, pulse. Place a `PulsingNodeBlock`/`PulsingNodeBlockEntity`, tick on an interval, fire a type-specific executor each pulse, deplete one blob per pulse. Remove self when budget exhausted.

## Parameters

| Field | Value |
|---|---|
| Pulse interval | 80 ticks (4 seconds) |
| Max stacks | 4 |
| Budget | stacks (each blob = one pulse) |
| BER | Slime-core orb, leaf-tinted, breathes, aim-highlight |
| Ambient particles | HAPPY_VILLAGER |

## Executor: `LeafPulseExecutor`

Each pulse:
1. Iterate 5x5 area (dx/dz in [-2, 2]) at node Y.
2. For each position: if `isRandomlyTicking()`, call `randomTick()` 3 times.
3. Also check one block above (crops on farmland).
4. If the node block is in water: extend reach to 9x9 (dx/dz in [-4, 4]) but only for positions on farmland (`Blocks.FARMLAND`).
5. Spawn HAPPY_VILLAGER particles.

## Testable Seams

- Pulse interval / budget math in `EffectMath` (if extracted).
- Water-detection and farmland-extension logic can be a pure predicate if we pass block state suppliers.

## Verification

- Throw 1 leaf blob -> node appears, pulses once after 4s, plants grow, node vanishes.
- Throw 3 blobs -> 3 pulses. Place node in water -> 9x9 farmland reach.
