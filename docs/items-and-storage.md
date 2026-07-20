---
id: items-and-storage
title: Item Hierarchy and Storage
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/item/GooBlobItem.java, src/main/java/com/mercuriusxeno/goo/item/GooOmniblobItem.java, src/main/java/com/mercuriusxeno/goo/item/BlobStacks.java, src/main/java/com/mercuriusxeno/goo/item/BlobTiers.java, src/main/java/com/mercuriusxeno/goo/item/CanisterItem.java, src/main/java/com/mercuriusxeno/goo/item/GooContents.java, src/main/java/com/mercuriusxeno/goo/item/BucketOfGooItem.java, src/main/java/com/mercuriusxeno/goo/item/OmniblobQuickCraft.java, src/main/java/com/mercuriusxeno/goo/mixin/]
created: 2026-03-22
updated: 2026-03-22
---

# Item Hierarchy and Storage

## GooBlobItem

One registration per goo type (15 total). Each blob = exactly 1,000 mB. Stacks to 64 (vanilla default).

**Throw**: Right-click spawns `ThrownBlobEntity` with cursor-targeting (raycasts to find entity at crosshair, 64-block range). Consumes 1 blob. 10-tick cooldown.

**Migration**: `inventoryTick` converts legacy volumetric blobs (with `BLOB_VOLUME` component) to stackable format. Remainder -> omniblob via `PlayerUtils.addOrDrop`.

**Overflow merge**: `overrideOtherStackedOnMe` -- when left-clicking a same-type blob stack onto a full stack, creates an omniblob with combined volume.

## GooOmniblobItem

One registration per goo type. Stack size 1. Stores arbitrary volume in `BLOB_VOLUME` data component (`long`). Named by `BlobTiers.computeTierName(volume)`.

**Throw**: Right-click costs 1,000 mB. If remaining <= 0 after throw, consumes the item.

**Cursor interactions** (inventory click protocol):

| Action | Cursor | Target | Result |
|--------|--------|--------|--------|
| Right-click | Omniblob | Empty slot | Place 1 blob (1,000 mB) in slot |
| Left-click | Omniblob | Same-type blob | Merge all into one omniblob in slot |
| Right-click | Omniblob | Same-type blob | Add 1 blob to stack from omniblob |
| Right-click | Empty | Omniblob in slot | Split volume in half |
| Left-click | Same-type blob | Omniblob in slot | Absorb entire blob stack |
| Right-click | Same-type blob | Omniblob in slot | Absorb 1 blob (shift: all) |
| Any click | Same-type omniblob | Omniblob in slot | Combine volumes |

**Output rule**: When remaining volume is a clean blob stack (divisible by 1000, <= 64,000 mB), converts back to a `GooBlobItem` stack. Handled via `BlobStacks.isCleanBlobStack` / `BlobStacks.createForOutput`.

## BlobStacks (Pure Utility)

Constants: `MB_PER_BLOB = 1000`, `MAX_STACK = 64`, `MAX_BLOB_STACK_VOLUME = 64,000`.

Key methods:
- `volumeOf(stack)` -- returns mB for blob/omniblob stacks.
- `createForOutput(type, volumeMb)` -- output rule: blob stack if clean, omniblob otherwise.
- `deplete(stack, accepted, player)` -- shrinks blob count or deducts omniblob volume.
- `dropAll(contents, level, pos)` -- drops all goo types as items using output rule.

## GooContents (Data Component)

Immutable `record GooContents(Map<GooType, Long> contents)`. Implements `TooltipProvider`. Used as data component for canisters, buckets, vats, crucible reservoirs, and PMIs.

- **Codec**: `CODEC` for persistent storage, `STREAM_CODEC` for network sync (ordinal + VarLong).
- **Mutation**: `withAdded`, `withRemoved`, `mergeWith`, `withCappedAdd` -- all return new instances.
- **Queries**: `totalVolume`, `typeCount`, `isSingleType`, `largestType`, `getVolume(type)`.

## CanisterItem

Multi-type goo container item. Stored in canister block entity slots (3x3 grid), hub slots (8 radial), and player inventory.

Key data components: `GOO_CONTENTS` (GooContents), `CANISTER_METADATA` (CanisterMetadata with matrices, gasket UUIDs, label, partners).

Static methods: `getGooContents(stack)`, `setGooContents(stack, contents)`, `getMetadata(stack)`, `setMetadata(stack, meta)`, `addGoo(stack, type, amount)`, `removeGoo(stack, type, amount)`.

Capacity: base 2^16 mB, scaled by `ContainerCapacity.canisterCapacity(matrices)` with 2^n matrix scaling (max 5).

## BucketOfGooItem

Single-type goo bucket. `BucketGooFluidHandler` provides `Capabilities.Fluid.ITEM`. Can pour into crucible basin (bypasses melting pipeline, directly fills reservoir).

## OmniblobQuickCraft

Pure utility for drag-to-distribute. `isOmniblobQuickCraft(stack)` returns true for omniblobs with volume.

- Left-click drag (charitable): `totalVolume / slotCount` per slot.
- Right-click drag (greedy): 1,000 mB per slot.

Mixin (`OmniblobQuickCraftMixin`) intercepts `AbstractContainerMenu` quickcraft phases.

## IGooItemInteraction

Interface for items that interact with canister blocks. `canisterInteraction()` returns `GooInteractionType` (BLOB_INSERT, etc.). Implemented by `GooBlobItem`, `GooOmniblobItem`.
