---
id: subplan-pulse
title: "Pulse -- Redstone Pulsing Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent pulsing node that triggers all nearby redstone receivers every 4 seconds. Each blob = one pulse.
---

# Pulse -- Redstone Pulsing Node

## Design Source

DESIGN-TYPES: "After 4 seconds, pulses. The pulse triggers nearby redstone receivers (all of them). Additional blobs yield additional pulses."

## Pattern

**Pulse node.** Shared `PulsingNodeBlock`/`PulsingNodeBlockEntity`.

## Parameters

| Field | Value |
|---|---|
| Pulse interval | 80 ticks (4 seconds) |
| Max stacks | 4 |
| Budget | stacks (each blob = one pulse) |
| Trigger radius | 6 blocks |
| BER | Slime-core orb, pulse-tinted, breathes, aim-highlight |
| Ambient particles | Small redstone dust |

## Executor: `PulsePulseExecutor`

Each pulse:
1. Iterate all block positions in a sphere (radius = trigger radius).
2. For each position: call `level.neighborChanged()` to trigger redstone updates on observers, pistons, dispensers, droppers, note blocks, etc.
3. Play NOTE_BLOCK_BELL sound.
4. Spawn redstone particles.

## Design Note

This is intentionally simple -- it fires neighbor updates, which is how redstone propagation works. Observers detect state changes, so a single `neighborChanged` call is enough to trigger them. Pistons and dispensers respond to block updates directly.

## Testable Seams

- Radius iteration is shared math (spherical scan). No new pure seams.

## Verification

- Place pulsing node near a piston -> piston fires after 4s, node vanishes.
- 4 blobs -> 4 pulses, piston extends/retracts 4 times. Observers, dispensers respond.
