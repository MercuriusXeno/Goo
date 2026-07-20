---
id: DESIGN
title: Core vision, keystones, and design-doc hierarchy
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
purpose: Goo: decompose items into typed goo blobs, reconstitute them back, and use each blob type's innate abilities.
---

# High Level Overview

Goo: decompose items into typed goo blobs, reconstitute them back, and use each blob type's innate abilities.

## Keystones

- **FUN > Balance:** if something isn't fun, remove it.
- **Internally consistent:** deterministic unless designed as random.
- **Visual > UI:** prefer in-world visuals over inventory UIs.

## Documentation Conventions

Design docs follow `DESIGN-{TOPIC}-{SUBTOPIC}.md`. Parent files provide intent; children provide specs. Max depth: 3 levels.

`<!-- TBD -->` / `<!-- TODO: description -->` mark unresolved design decisions. Resolve before implementing.

Hierarchy:
- `DESIGN.md`: vision, keystones, core model (this file)
  - `DESIGN-TYPES.md`: 15 goo types, source items, effect specs
  - `DESIGN-MACHINES.md`: machines, upgrades, equipment, networking
  - `DESIGN-RENDERING.md`: BERs, particles, HUD, fluid textures
    - `DESIGN-RENDERING-SHADERS.md`: shader techniques
- `GLOSSARY.md`: terminology quick-reference
- `API-NOTES-1.21.11.md`: NeoForge migration pitfalls

### Design-Flow Conventions

1. **Spec, not story.** Write what it does, not how it feels. "Urchin spines, multi-hit damage zone" not "Spines protrude from the blob like an urchin."
2. **One fact, one line.** If a bullet has "and" connecting two different facts, split it.
3. **Formulas are specs; adjectives are not.** "25%/n^0.2 freeze buildup" is a spec. "severely negative" is not.
4. **No self-referential text.** Never write "this section describes X." Cross-ref: `See DESIGN-MACHINES.md § Section`.
5. **DRY across files.** A fact lives in one canonical location. Others reference, never repeat.
6. **TBD belongs in ROADMAP, not design docs.** Design docs describe decided things.
7. **Always-loaded files are premium real estate.** CLAUDE.md and MEMORY.md target under 800 words each.

## Storage

Goo resembles a fluid but is generally represented as items or data components. Internally all values are `long` microblobs.

### Stackable Blobs

One blob item per goo type, stackable to 64 (vanilla default). Each blob is exactly 1,000 mB. Throwing consumes one blob from the stack. Volume math centralized in `BlobStacks` utility.

### Omniblobs

One omniblob item per goo type, unstackable, uncapped capacity. Stores volume via `BLOB_VOLUME` data component. Used for amounts that don't fit in a blob stack: sub-blob remainders (< 1,000 mB) or amounts exceeding 64,000 mB. Machine output rule: if volume % 1000 == 0 and volume <= 64,000 mB, output a blob stack; otherwise output an omniblob.

Display tiers (omniblob only): Microblob (< 1,000 mB), Blob (1,000+), Kiloblob (10M+), Megablob (1B+), Gigablob (1T+), Terrablob (1P+).

Item model: blobs always use standard blob model. Omniblobs use 4 size tiers (tiny/small/base/large) via `range_dispatch` with `goo:blob_size`. Slot overlay on omniblobs only: type icon (top-right) + compact volume label (bottom-right, half-scale).

Cursor interactions (omniblob): right-click empty slot extracts 1 blob (shift: up to 64). Blob stack clicked onto omniblob absorbs. Blob stacks exceeding 64 overflow into omniblob.

### Bucket of Goo

Multi-type container (`BucketOfGooItem` + `BucketContents`). Hard cap: 8,000 mB. Single-type: "{Type} Bucket of Goo." Multi-type: "Slurry." Unstackable, craft remainder = vanilla bucket.

Right-click to place (single-type only, up to 8 blobs/block). Right-click goo block to pick up. Slurry cannot place. Exposes `IFluidHandlerItem`. Model: `neoforge:fluid_container`.

### Goo Fluids

15 non-flowing types. 8 blobs (8,000 mB) = full source block, 1-7 = partial. 1 mB = 1 NeoForge millibucket. Machines do NOT expose `IFluidHandler`.

## Units

All values stored as `long` microblobs. Display: mB if < 1,000, fractional blobs/KB/MB/GB otherwise. Example: string worth 3 vital = **3 mB vital**.

## Value System

~200 base items hand-valued; others derived from recipes via multi-pass derivation. See `DESIGN-MACHINES.md` § Crucible.

### Equivalency Rules

- Cobblestone = stone (smelt/break interchangeable)
- Storage blocks = 9x base item
- Nuggets = 1/9 ingot (ingot values must be divisible by 9)
- Slabs = 1/2 block (parent values must be even)
- Ore = ingot (unless Fortune multiplier applies)

## Throwing

Bare-handed throw triggers contact effect on release (goo touches hand before flight). Gloves block contact effects. Cursor targeting: blobs land where your crosshair aims, 100% accuracy.

See `DESIGN-TYPES.md` for the 15-type catalog. See `DESIGN-MACHINES.md` for machines and equipment.
