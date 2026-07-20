---
id: subplan-glow
title: "Glow -- Permanent Light Crystal"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Permanent non-colliding light source placed on the face hit. Lasts until broken.
---

# Glow -- Permanent Light Crystal

## Design Source

DESIGN-TYPES: "Crystallizes into flattish cuboid, non-colliding light source on the face hit. Lasts until destroyed."

## Pattern

**Unique block.** Permanent placement (no duration, no stacking budget). A `GlowCrystalBlock` that emits light and persists until the player breaks it. Not stackable -- each blob is a separate light.

## Parameters

| Field | Value |
|---|---|
| Light level | 15 (max) |
| Collision | None |
| Shape | Flat cuboid on the hit face (like a button/torch) |
| Permanence | Until broken by player |
| BER | Slime-core orb, glow-tinted, breathes, aim-highlight. Fullbright. |
| Ambient particles | Occasional GLOW_SQUID_INK or ELECTRIC_SPARK |

## Architecture

### New: `block/GlowCrystalBlock.java`

Simple non-collidable block with `lightEmission(15)`. Directional placement like a wall torch -- shape and model orient to the face hit. No block entity needed for basic version (no ticking, no state beyond placement).

However, to get the slime-core BER, it needs a block entity. The BE is minimal: just goo type (always GLOW) for the BER to read.

### New: `block/GlowCrystalBlockEntity.java`

Minimal. Exists solely so the BER can render the slime-core orb. No ticking, no serialized state beyond the block entity type itself.

### Directional shape

Use a `FACING` blockstate property (Direction). Shape is a flat 8x3x8 pixel cuboid flush to the placed face. Six voxel shapes, one per direction.

### WorldEffects dispatch

`glowDome()` -> place `GlowCrystalBlock` on the hit face (air check). No stacking -- if a glow crystal is already there, do nothing.

## Testable Seams

- None significant. This is a simple placement block.

## Verification

- Throw glow blob at a wall -> light crystal appears on that face, emits light level 15.
- Persists forever. Break by hand -> drops nothing (goo is spent). BER shows glow-tinted orb.
