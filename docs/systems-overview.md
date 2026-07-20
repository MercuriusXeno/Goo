---
id: systems-overview
title: Goo Mod Systems Overview
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/Goo.java, src/main/java/com/mercuriusxeno/goo/GooType.java, src/main/java/com/mercuriusxeno/goo/registry/]
created: 2026-03-22
updated: 2026-03-22
---

# Systems Overview

## Entry Point

`Goo.java` is the `@Mod` class. Constructor registers all DeferredRegister holders and wires event listeners. Key lifecycle:

1. **Constructor** -- registers FluidTypes, Fluids, Blocks, Items, BlockEntities, Entities, DataComponents, Potions, Particles, CreativeTabs, Capabilities, TicketControllers, and config.
2. **`onServerStarting`** -- loads `GooValueRegistry` (base values from JSON, derived cache from disk).
3. **`onPlayerLoggedIn`** -- sends full value map to client via `GooValueSync.sendToPlayer`.
4. **`onRegisterCommands`** -- registers `/goo` command tree.

## GooType Enum

15 types: AEON, BLAZE, CRYSTAL, ENDER, FROST, GLOW, HEX, LEAF, METAL, NETHER, PULSE, ROCK, SHROOM, TYPHOON, VITAL. Each has an `id` string and `color` int. Used as keys everywhere.

## Package Map

| Package | Entry Point | Responsibility |
|---------|------------|----------------|
| `block/` | `CrucibleBlockEntity`, `CanisterBlockEntity` | Machine blocks, block entities, fluid handlers, gasket integration |
| `client/` | `GooClientSetup` | BERs, HUD renderers, particles, overlays, item decorators |
| `command/` | `GooCommand` | `/goo lookup`, `/goo reload`, `/goo regen`, `/goo audit` |
| `data/` | `GooValueRegistry`, `GasketRegistry` | Value pipeline, gasket network persistence, graph utilities |
| `effect/` | `WorldEffects`, `GooMobEffects`, `ContactEffects` | All 15-type effect dispatch tables |
| `entity/` | `ThrownBlobEntity` | Cursor-targeting projectile, hits dispatch to effects |
| `fluid/` | `GooFluid` | 15 non-flowing Source/Flowing fluid subclasses |
| `item/` | `GooBlobItem`, `GooOmniblobItem`, `CanisterItem` | Blob/omniblob stacking, canister storage, gasket pairing items |
| `mixin/` | `OmniblobQuickCraftMixin` | Drag-to-distribute for omniblobs in crafting grid |
| `network/` | `GooNetworking`, `GooValueSync` | Value sync, canister punch/rename/unlink, tuner feedback |
| `registry/` | `GooBlocks`, `GooItems`, `GooCapabilities` | DeferredRegister holders for all registered objects |
| `tools/` | `FluidTextureGenerator` | Offline CA-based texture datagen |

## Key Singletons

- `Goo.GOO_VALUES` -- static `GooValueRegistry` instance, loaded on server start, synced to clients on login.
- `GasketRegistry` -- per-world `SavedData`, accessed via `GasketRegistry.get(serverLevel)`.

## Subsystem Quick Links

- **Value pipeline**: `GooValueRegistry` -> `GooValueDerivation` -> `GooValueSync`. See `value-system.md`.
- **Machine chain**: Crucible (extract) -> Canister (store) -> Hub (route) -> Vat (bulk) -> Plexer (reconstitute). See `machines.md`.
- **Transport**: `GasketRegistry` + `GasketPusher` + `GooCapabilities`. See `gasket-network.md`.
- **Items**: `GooBlobItem`, `GooOmniblobItem`, `BlobStacks`, `CanisterItem`. See `items-and-storage.md`.
- **Rendering**: BERs in `client/`, particles in `client/particle/`. See `rendering.md`.
- **Effects**: `ThrownBlobEntity` dispatches to `WorldEffects`/`GooMobEffects`/`ContactEffects`. See `effects.md`.

## Units

- **microblob (mB)**: base unit. `long` everywhere. 1 mB = 1 NeoForge millibucket.
- **blob**: 1,000 mB. One `GooBlobItem` = 1 blob. Stacks to 64.
- **GooValue**: `Map<GooType, Integer>` -- item decomposition values in mB.
- **GooContents**: `Map<GooType, Long>` -- runtime container storage in mB.
