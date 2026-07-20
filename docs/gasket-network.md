---
id: gasket-network
title: Gasket Transport System
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/data/GasketRegistry.java, src/main/java/com/mercuriusxeno/goo/data/GasketLocation.java, src/main/java/com/mercuriusxeno/goo/data/GooNetwork.java, src/main/java/com/mercuriusxeno/goo/block/GasketPusher.java, src/main/java/com/mercuriusxeno/goo/block/IGasketHolder.java, src/main/java/com/mercuriusxeno/goo/block/IGasketPusher.java, src/main/java/com/mercuriusxeno/goo/registry/GooCapabilities.java, src/main/java/com/mercuriusxeno/goo/registry/GooTickets.java]
created: 2026-03-22
updated: 2026-03-22
---

# Gasket Transport System

## Overview

Gaskets are UUID-identified endpoints that form a directed graph for goo transport. A transmitter gasket pushes goo to a linked receiver gasket. Links are stored in `GasketRegistry`, a world-level `SavedData`.

## GasketRegistry

**Location**: `data/GasketRegistry.java`. Singleton per world, stored in overworld as `goo_gasket_registry.dat`.

**Data model**:
- `pairings: Map<UUID, UUID>` -- output gasket -> input gasket (directed link).
- `locations: Map<UUID, GasketLocation>` -- gasket UUID -> world position cache.

**Key operations**:
- `link(output, input)` -- unlinks both first (each gasket in at most one link), then pairs.
- `unlink(gasketId)` -- removes any pairing involving the gasket (as source or target).
- `getTarget(outputGasket)` / `getSource(inputGasket)` -- single-hop lookup.
- `updateLocation(gasketId, location)` -- caches/removes world position.

**Persistence**: Codec-based via `UUIDUtil.STRING_CODEC` for UUIDs and `GasketLocation.CODEC`.

## GasketLocation

Record: `(ResourceKey<Level> dimension, BlockPos pos, boolean isTopFace, int slot)`. Identifies where a gasket physically exists in the world.

## Capabilities

**`GooCapabilities.GASKET_BLOCK`** -- `BlockCapability<ResourceHandler<FluidResource>, UUID>`. Context = target gasket UUID. Registered for:
- `CanisterBlockEntity` -- scans 9 slots for matching gasket UUID, returns `CanisterSlotFluidHandler`.
- `HubBlockEntity` -- checks intake gasket first, then scans 8 canister slots.
- `VatBlockEntity` -- checks cap/base gasket IDs against the query.
- `TapBlockEntity` -- checks gasket ID for remote fluid extraction.
- `PlexerBlockEntity` -- checks gasket ID for remote goo supply to reconstitution.

Hub and canister gasket UUIDs are opt-in: they only exist after a choral gasket is physically installed on a face. Without a gasket, these machines use copper fittings (physical adjacency) only.

**`GooCapabilities.GASKET_ENTITY`** -- `EntityCapability<ResourceHandler<FluidResource>, UUID>`. Registered for PLAYER entity type. Scans player inventory for canister items with matching gasket UUID, returns `PlayerInventorySlotHandler`.

## GasketPusher

**Location**: `block/GasketPusher.java`. Owned by machines with transmitter gaskets (currently Crucible).

**Tick logic**: Every 20 ticks (`PUSH_INTERVAL`), if reservoir has goo and a valid partner exists, pushes via `CruciblePushMath.computePush`. Two paths:
- **Block target**: Uses `BlockCapabilityCache` for `GASKET_BLOCK` capability. Cache built in `rebuildCache()`.
- **Entity target**: Looks up player by UUID, queries `GASKET_ENTITY` capability.

**Chunk loading**: On `rebuildCache()`, forces the target chunk via `GooTickets.GASKET_CHUNKS.forceChunk`. Released on `unforceChunk()` (called when cache is rebuilt or cleared). Ticket controller validates block entities still exist on world load.

## GooNetwork (Graph Utilities)

**Location**: `data/GooNetwork.java`. Pure utility, no state.

- `buildGraph(pairings)` -- converts `Map<UUID, UUID>` to adjacency list.
- `findCycles(graph)` -- iterative DFS with three-color marking (white/gray/black). Returns set of UUIDs in cycles.
- `getFlowPath(sourceGasket, pairings)` -- follows directed chain, stops on revisit.

## IGasketHolder Interface

Implemented by: `CrucibleBlockEntity`, `CanisterBlockEntity`, `HubBlockEntity`, `VatBlockEntity`, `TapBlockEntity`, `PlexerBlockEntity`.

Key methods:
- `resolveRole(BlockHitResult)` -- determines TRANSMITTER vs RECEIVER based on hit location.
- `resolveSlot(BlockHitResult)` -- for slotted containers (canister, hub), maps hit to slot index.
- `getGasketId(role)` / `ensureGasketId(role)` -- lazy UUID generation.
- `getPartner(role)` / `setPartner(role, partner)` -- linked partner reference.

## GasketRole

Enum: `TRANSMITTER`, `RECEIVER`. Crucible = transmitter only. Hub = receiver only (intake). Vat = both (cap=receiver, base=transmitter). Canister slots have per-gasket top/bottom UUIDs.

## Pairing Protocol

1. Player uses `ChoralTunerItem` on a machine face -> `IGasketHolder.resolveRole(hit)` determines role.
2. First use: stores source gasket UUID + location in `TunerState`.
3. Second use on different machine: `TunerLinkLogic` links transmitter output to receiver input in `GasketRegistry`.
4. Both machines' `setPartner` called with denormalized `GasketPartner` reference.
5. Transmitter's `GasketPusher.rebuildCache()` rebuilds the `BlockCapabilityCache`.

## Copper Fitting vs Gasket

Copper fittings and choral gaskets are mutually exclusive per face. A face uses either physical adjacency (copper fitting, default) or remote UUID-based transport (choral gasket, upgrade). Installing a gasket on a face replaces the copper fitting for that face. Removing the gasket restores copper fitting behavior.

Copper fittings require no capability registration — they operate via direct block entity adjacency checks. Gaskets use `GASKET_BLOCK` / `GASKET_ENTITY` capabilities with UUID context.

## GooTickets

**Location**: `registry/GooTickets.java`. Registers `GASKET_CHUNKS` ticket controller. Validates block entities on load; removes tickets for missing BEs.
