---
id: machines
title: Machine Block Entities
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/block/CrucibleBlockEntity.java, src/main/java/com/mercuriusxeno/goo/block/CanisterBlockEntity.java, src/main/java/com/mercuriusxeno/goo/block/HubBlockEntity.java, src/main/java/com/mercuriusxeno/goo/block/VatBlockEntity.java, src/main/java/com/mercuriusxeno/goo/block/PlexerBlockEntity.java, src/main/java/com/mercuriusxeno/goo/block/TapBlockEntity.java]
created: 2026-03-22
updated: 2026-03-22
---

# Machine Block Entities

## Crucible (CrucibleBlockEntity)

**Role**: Melts items into goo. Entry point for goo production.

**Tick pipeline** (`serverTick`): syncMatricesToBlockState -> tickPlatform -> tickIgnitionSpray -> handleMeltingTick -> handleBoilingEffects -> gasketPusher.tick.

**Melting mechanics**: Items inserted via `insertItem(stack, count)` -> `GooValue.toGooContents(count)` -> merged into a shared PMI (PartiallyMeltedItem) pool. Each tick, `drainFromPool` extracts `CrucibleMath.extractionRate(remaining, matrices)` mB, distributed proportionally across all goo types via `CrucibleMath.computeDrainShares`. Drained goo enters the `GooFluidHandler` reservoir.

**Platform system**: Physical `platformY` moves at 1px/tick toward target. Target = `BASIN_Y - PLAT_THICKNESS - rodHeight`. Melting only starts when rod contacts basin (`isRodContactingBasin`). Platform travel IS the startup delay. Fuel depletion causes rod to shrink; platform rises to maintain contact.

**Fuel**: Vanilla blaze rod -> converts to `DepletedBlazeRodItem` on first burn tick. `fuelFraction()` drives rod height. Depleted rods stay in slot until replaced.

**Upgrades**: Up to 5 rune ink matrices. Stored in `matrices` field, synced to blockstate via `CrucibleBlock.MATRICES`. Affects extraction rate exponent.

**Gasket**: TRANSMITTER only. Copper fitting (default), choral gasket (upgrade). Single gasket UUID when gasket installed. `GasketPusher` pushes reservoir contents to linked partner every 20 ticks.

**Serialization**: `ValueOutput`/`ValueInput` with Codec-based fields (MeltingItem, FuelRod, Reservoir, GasketId, GasketPartner). Client sync via `ClientboundBlockEntityDataPacket`.

## Canister Block (CanisterBlockEntity)

**Role**: Multi-canister storage. 3x3 grid (9 slots) of canister items in one block.

**Slot management**: `insertCanister(slot, stack)`, `removeCanister(slot)`. Each slot holds one `CanisterItem` ItemStack. Dynamic `VoxelShape` computed from occupied slots. `CanisterBlock.hitSlot(hit, pos)` resolves ray-cast to slot index.

**Gasket integration**: Copper fitting (default), choral gasket (upgrade). No machine-level gasket. Each sub-canister has its own top/bottom gasket UUIDs in `CanisterMetadata`, created only when a gasket is installed. On insert: `registerSlotGaskets` updates `GasketRegistry` with `GasketLocation`. On remove: deregisters.

**Placement**: `applyImplicitComponents` captures `GooContents` + `CanisterMetadata` from item. `assignPendingToSlot(slot)` builds canister ItemStack from pending data.

**Owner**: `ownerUuid` set on placement. Used for tuner ownership checks (`allowsTuning`).

## Hub (HubBlockEntity)

**Role**: Routes incoming goo to up to 8 canisters in radial slots (N, NE, E, SE, S, SW, W, NW).

**Routing**: `routeGoo(type, amount)` iterates slots, calling `CanisterItem.addGoo` until all goo is placed. Returns amount routed.

**Gasket**: RECEIVER only (intake at top). Copper fitting (default), choral gasket (upgrade). `intakeGasketId` + `intakePartner` created only when gasket installed. Fluid handler exposed via `HubFluidHandler` wrapping the hub.

**Fluid capability**: `Capabilities.Fluid.BLOCK` registered on UP and null sides. `GASKET_BLOCK` scans canisters for matching gasket UUID, or checks intake gasket.

## Vat (VatBlockEntity)

**Role**: Bulk single-container goo storage. Base capacity 2^20 mB (1M), doubled per matrix up to 6 (64M max via `ContainerCapacity.vatCapacity`).

**Storage**: Single `GooFluidHandler`. Multi-type. `Capabilities.Fluid.BLOCK` on UP, DOWN, and null sides.

**Gasket**: Dual gasket: cap (RECEIVER) + base (TRANSMITTER). Copper fitting (default), choral gasket (upgrade). `GasketRegionResolver.resolveVatRole` maps hit direction + localY to role. Blockstate tracks `GASKET_CAP`/`GASKET_BASE`.

**Implements**: `ISlottedGooContainer` (slot 0 only), `IGasketHolder`, `IGooReservoir`.

## Plexer (PlexerBlockEntity)

**Role**: Reconstitutes items from goo. 2 canister slots (3 as triplexer via rune ink upgrade).

**Reconstitution**: `tryReconstitute()` looks up target item's `GooValue`, checks if canisters have enough of each type, then consumes goo and returns the result item. Simple all-or-nothing check.

**Target**: Observer slot (`targetItem`). Set by player placing an item.

**Gasket**: Copper fitting (default), choral gasket (upgrade). Optional gasket for remote goo supply.

**Upgrade**: `upgradeToTriplexer()` sets `maxSlots = 3`. Persisted via `IS_TRIPLEXER` data component.

## Tap (TapBlockEntity)

**Role**: Faucet connecting to a container. Drips blobs at varying speeds.

**Gasket**: Copper fitting (default), choral gasket (upgrade). Optional gasket for remote fluid extraction.

## Common Patterns

- **Sync**: All machines use `BlockEntitySync.markDirtyAndSync(this)` which calls `setChanged()` + sends `ClientboundBlockEntityDataPacket`.
- **Component bridge**: `collectImplicitComponents`/`applyImplicitComponents` enable loot table `copy_components` for pick-block and silk-touch-like preservation.
- **Serialization**: All use `ValueOutput`/`ValueInput` with `Codec`-based `store`/`read` calls.
