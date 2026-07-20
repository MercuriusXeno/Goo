---
id: subplan-vital
title: "Vital -- Life Pulsing Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent pulsing node that triggers animal mating in a 5x5 area every 4 seconds. Each blob = one pulse.
---

# Vital -- Life Pulsing Node

## Design Source

DESIGN-TYPES: "Creates a life pulsing node, pulsing every 4 seconds. Each blob gives an additional pulse. Pulses cause animals in a 5x5 square centered around it to have a chance to mate. The mating chance is 100/h^0.8 where h = max health."

## Pattern

**Pulse node.** Shared `PulsingNodeBlock`/`PulsingNodeBlockEntity`.

## Parameters

| Field | Value |
|---|---|
| Pulse interval | 80 ticks (4 seconds) |
| Max stacks | 4 |
| Budget | stacks (each blob = one pulse) |
| BER | Slime-core orb, vital-tinted, breathes, aim-highlight |
| Ambient particles | HEART |

## Executor: `VitalPulseExecutor`

Each pulse:
1. Find all `Animal` entities in a 5x5x3 AABB centered on the node.
2. For each animal: compute mating chance = `100 / h^0.8` where h = `getMaxHealth()`. Clamp to [0, 100].
3. Roll against chance. On success: call `setInLove(null)` (or equivalent 26.1 API for triggering breeding).
4. Spawn HEART particles on affected animals.

## Testable Seams

- `computeMatingChance(double maxHealth)` -> pure math in `EffectMath`. Returns percentage [0..100].
  - h=1 -> 100%, h=10 -> ~15.8%, h=20 -> ~9.1%, h=100 -> ~2.5%

## Verification

- Throw 1 vital blob near cows -> node pulses, some cows enter love mode, node vanishes.
- Throw 4 blobs -> 4 pulses. High-health mobs (iron golem, h=100) rarely affected.
