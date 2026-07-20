---
id: subplan-hex
title: "Hex -- Mob Spawner Pulsing Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent pulsing node that spawns a random hostile mob every 4 seconds. Each blob = one pulse.
---

# Hex -- Mob Spawner Pulsing Node

## Design Source

DESIGN-TYPES: "After 4 seconds, pulses. The pulse spawns a random hostile mob, depleting one blob. Additional blobs yield additional pulses."

## Pattern

**Pulse node.** Shared `PulsingNodeBlock`/`PulsingNodeBlockEntity`.

## Parameters

| Field | Value |
|---|---|
| Pulse interval | 80 ticks (4 seconds) |
| Max stacks | 4 |
| Budget | stacks (each blob = one pulse) |
| BER | Slime-core orb, hex-tinted, breathes, aim-highlight |
| Ambient particles | WITCH |

## Executor: `HexPulseExecutor`

Each pulse:
1. Select a random hostile mob type from a curated list (zombie, skeleton, spider, creeper, witch, enderman). Weighted or uniform.
2. Find a valid spawn position near the node (within 4 blocks, on solid ground, in air).
3. Spawn the mob via `level.addFreshEntity()`.
4. Spawn WITCH + SMOKE particles.
5. Play a spooky sound (AMBIENT_CAVE or similar).

## Open Questions

- Should the mob list be biome-aware? (Probably not for v1 -- keep it simple.)
- Should the spawned mob be persistent or despawn normally? (Normal despawn rules.)

## Testable Seams

- Mob selection (if extracted as a weighted random with a seed, testable).
- Spawn position finding is framework-coupled.

## Verification

- Throw 1 hex blob -> node appears, spawns a hostile mob after 4s, node vanishes.
- Throw 4 blobs -> 4 mobs over 16 seconds.
