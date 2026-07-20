---
id: subplan-shroom
title: "Shroom -- Death Pulsing Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Persistent pulsing node that triggers on nearby mob death, growing mushrooms in a radius. Each blob = one pulse.
---

# Shroom -- Death Pulsing Node

## Design Source

DESIGN-TYPES: "Pulses when mobs die nearby, which has a chance to grow mushrooms anywhere in a radius. Each blob gives an additional pulse."

## Pattern

**Pulse node (death-triggered variant).** Uses `PulsingNodeBlock`/`PulsingNodeBlockEntity` but pulses are triggered by `LivingDeathEvent` within radius, not a timer.

## Parameters

| Field | Value |
|---|---|
| Trigger | `LivingDeathEvent` within detection radius |
| Detection radius | 8 blocks |
| Mushroom spread radius | 5 blocks |
| Max stacks | 4 |
| Budget | stacks (each death consumes one pulse) |
| BER | Slime-core orb, shroom-tinted, breathes, aim-highlight |
| Ambient particles | SPORE_BLOSSOM_AIR |

## Architecture Note

Unlike timer-based pulsing nodes, shroom listens for a NeoForge event. Two options:
- **A: Event listener queries all active shroom nodes.** A static registry of active shroom node positions (like `FrostFieldBlockEntity` but simpler). On `LivingDeathEvent`, scan the registry for nodes within range of the death position.
- **B: Block entity polls for deaths.** Server-tick checks a death cache. Less elegant.

**Recommendation: Option A.** A `Set<GlobalPos>` static registry in the BE class, updated on place/remove. The event listener iterates the set (small, usually <10 entries).

## Executor: `ShroomPulseExecutor`

Each pulse (on mob death):
1. Iterate spherical volume (radius = mushroom spread radius) around the node.
2. For each air block above a solid surface: chance to place red or brown mushroom (20% per block, `canSurvive` check).
3. Convert grass/dirt to mycelium in the spread radius.
4. Spawn SPORE_BLOSSOM_AIR particles.

## Testable Seams

- Mushroom placement chance and mycelium conversion are framework-coupled.
- Registry add/remove logic is testable.

## Verification

- Place shroom node, kill a mob within 8 blocks -> mushrooms grow, node depletes one charge.
- Kill mob outside 8 blocks -> no effect. 4 blobs -> 4 death-triggered pulses.
