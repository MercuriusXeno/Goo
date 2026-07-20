---
id: subplan-ender
title: "Ender -- Teleporter Node"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Teleporter node with charges. Look + shift to warp. Additional blobs add charges.
---

# Ender -- Teleporter Node

## Design Source

DESIGN-TYPES: "Teleporter node you can teleport to by looking at and holding shift. Each use decreases its charge by 1. Additional blobs yield additional charges."

## Pattern

**Unique block.** Place an `EnderNodeBlock` with charge tracking. Players teleport to it via look+crouch. Each teleport depletes one charge. Additional blobs stack charges.

## Parameters

| Field | Value |
|---|---|
| Max charges | No hard cap (each blob = +1 charge) |
| Activation | Player looks at node + crouches |
| Activation range | 32 blocks (line of sight) |
| Teleport cost | 1 charge per use |
| BER | Slime-core orb, ender-tinted, breathes, aim-highlight. Charge count visible via scale. |
| Ambient particles | PORTAL |

## Architecture

### New: `block/EnderNodeBlock.java` + `block/EnderNodeBlockEntity.java`

Persistent block entity with `charges` field. Server-ticks to check for players looking at it while crouching within range.

- `initNode()` -- sets charges to 1.
- `addCharge()` -- increments charges (no cap, or a generous one like 16).
- `serverTick()` -- scan nearby players, check look direction + crouch state, teleport and decrement.
- On charges reaching 0 -> remove block.

### Look detection

Each server tick, iterate players within `activationRange`. For each crouching player, raycast from eye position in look direction. If the ray hits this block's position, teleport the player to the node position and decrement charge.

Throttle: 10-tick cooldown per player to prevent multi-fire.

### Teleport

`player.teleportTo(nodeX + 0.5, nodeY + 1.0, nodeZ + 0.5)` -- place player on top of the node. Play enderman teleport sound + particles at both origin and destination.

### WorldEffects dispatch

`enderNode()` -> place or add charge to `EnderNodeBlock` at hit face.

## Testable Seams

- Look-direction math (dot product between look vector and node direction) can be extracted as pure math.
- Charge management is trivial.

## Verification

- Throw ender blob -> node appears with 1 charge. Look at it + crouch -> teleported, node vanishes.
- Throw 3 blobs -> 3 charges, 3 teleports before depletion. Works at range. Requires line of sight.
