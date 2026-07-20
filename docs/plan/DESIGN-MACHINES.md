---
id: DESIGN-MACHINES
title: Machine specs, upgrade formulas, crafting recipes, gasket networking
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
---

# Machines and Mechanics

## Intermediate Materials

### Exorite
```
Netherite Ingot + Echo Shard = 1 Exorite
```
Endgame material tier requiring exorite upgrade templates (ancient cities). 
Exorite tools don't break when their durability reaches 0 and are not dropped on death.
Exorite is used in the crafting of spawners.

## Upgrades

### Choral Gasket
```
 E  C = Popped Chorus fruit
ECE E = Ender Pearl
 E
```
Creates a choral gasket. Transmits or receives fluid remotely. Gaskets are per-face — one gasket item installs on one face (top or bottom). Click height determines which face.

### Choral Tuner
```
E E C = Popped Chorus fruit
CCC E = Ender Pearl
 E
```
Creates a choral tuning fork which can detect the connection of a gasket or override it. This is a secondary way of connecting gaskets.
You can also use a Choral Tuner to *name* your Canisters. This name is non-unique and enables you to differentiate canisters or designate their purpose.

## Crucible
```
I I I = Copper Ingot
FCN N = Netherite Scrap C= Cauldron
III F = Blast Furnace
```
Upgradable goo producer. Creates goo in the reservoir, but requires specific heat source as fuel.

### Extraction Pipeline

Per-tick drain model. Items create a `PartiallyMeltedItem` (PMI) holding `BucketContents`. 
Each tick with fuel, `extractionRate()` mB drains from the PMI's largest type into the reservoir. 
Fuel rods (depleted blaze rods) last 1,200 ticks (60s) per rod.

Blobs bypass the pipeline entirely - volume goes straight to the reservoir (raw goo bypass).

Dropped PMIs falling into the basin are re-absorbed. Break drops: PMI, fuel rod, reservoir as blobs.

### Container Evaluation

When a container item (shulker box, bundle) enters the crucible, it recursively walks the contents. Each contained item is checked: items with goo values have their values summed; items without goo values are ejected as item entities above the basin. Nested containers (e.g. a bundle inside a shulker) are recursed. The container shell's own goo value (if any) is added last.

### Auto-Push Flow

Every 20 ticks (1 second), the crucible attempts to push its reservoir contents to the gasket-linked destination. The flow:

1. Guard: reservoir non-empty and `gasketPartner` set.
2. Check that the destination chunk is loaded (skip if unloaded).
3. Resolve the destination block entity. Dispatch by type:
   - **CanisterBlockEntity**: `insertGoo(slot, type, volume)` per goo type.
   - **HubBlockEntity**: `routeGoo(type, volume)` (auto-route) or `insertGoo(slot, type, volume)` (direct slot).
   - **VatBlockEntity**: `insertGoo(type, volume)` per goo type.
4. Each destination returns the amount actually accepted. Unaccepted goo remains in the reservoir.
5. Sync to clients if the reservoir changed.

Push math is extracted to `CruciblePushMath.computePush()` for testability: iterates each type in the reservoir, offers volume to an acceptor function, splits results into accepted vs remaining.

### Collection

The crucible creates a slurry (mixed-type goo). Canisters siphon individual types; buckets collect everything. 
Networks siphon automatically. Dumping a bucket converts cooling slurry into blobs.

## Canister
```
QCQ Q = Quartz, C = Copper
G G G = Glass Pane
QCQ
```
Upgradeable goo container, item (not block), attachable and interchangeable with many machines. 
Copper fittings are the default physical connection (requires solid block or attachment-capable block such as hub/plexer below). 
Choral gaskets are an optional upgrade for remote fluid transfer.

Base capacity: 2^20 mB (~1M). Enchantment (Compression 1-5) allows up to 2^25 (32M)

Type-locked: a canister holds any mixture of goo.

Placed canisters form a 3x3 multi-canister block grid with sub-shape gasket regions (top/bottom).

### Placement Flow

Two paths: insertion into existing canister block, or new block placement.

**Insertion (existing block):** `CanisterItem.useOn()` -> `insertIntoExisting()` -> `resolveInsertionSlot()`:
1. `CanisterSlotLayout.placementSlot(face, px, pz)`: projects hit point to slot. Side faces lock one axis and offset the other toward the adjacent block.
2. If occupied: `adjacentByCursorLean(slot, px, pz)` picks the neighbor the cursor leans toward from slot center.
3. If still occupied: `findFirstEmpty()` scans slots 0-8.

**New block:** `BlockItem.place()` -> `CanisterItem.placeBlock()` -> `computePlacementSlot()` -> `assignPendingToSlot()`.

**Surface-below targeting:** clicking the top face of a block beneath a canister block routes to `insertIntoExisting` on the block above. `CanisterPlacementOverlay` renders a green wireframe preview.

### Punch Flow (Hold-to-Break)

Client: `CanisterPunchListener` tracks 10-tick left-click hold with aim verification. On completion, sends `CanisterPunchPayload(pos, slot)` to server.

Server: `CanisterPunchHandler.applyPunch()` validates range and cooldown, removes canister from slot, drops as item entity, plays dislodge sound. If no canisters remain, removes the block.

Red wireframe progress rendered by `SlotOutlineRenderer.renderPunchProgress()`.

See **Inventory Click Interactions** below for bundle-like slot behavior.

## Vat
```
QBQ Q = Quartz Block, B = Block of Copper
G G G = Glass Block
QBQ
```

Giant single-block canister. No sub-block grid.

Type-locked on first insert, unlocks when fully drained. Right-click with blob to insert, empty hand to extract.

Base capacity: 2^25 mB (~32M). Enchantment (Compression 1-5) allows up to 2^30 (2B)

## Tap
```
 II I = Iron Bar
CCC 
CC  C = Copper
```
Faucet connecting to a canister. Drips blobs at varying speeds. Drip rate mechanics: TBD (see goal-008 for design gaps). 
Copper fitting is the default connection (physical adjacency to container). Choral gasket is an optional upgrade.

## Hub
```
CCC C = Copper, I = Iron Bar
 I
CCC
```
A block which holds up to 8 canisters in radial slots (internal). Accepts 1 external canister on its center top face via copper fitting.

Canisters can be replaced freely.

Each can is physically connected to one of the hub's "spokes", or output pipes. 
The hub has a central input located in the center, on the top of the device. 
It automatically routes goo to any canister with the space and type to hold it.

## Plexer
```
SRS S = Cut Copper Slab R = Redstone Comparator
NC  N = Netherite Scrap, C = Crafter
SSS
```
Machine that reconstitutes items from goo. 
Canisters sit externally as a CanisterBlock on the plexer's top face. A 3x3 grid can be placed in no particular order.
The plexer will draw goo from these canisters and create the template item it is provided as long as it has the right goo.

## Reactor
```
SRS S = Cut Copper Slab R = Redstone Comparator
NCN N = Netherite Scrap, C = Cauldron
SSS
```
Machine that reconstitutes items from goo. 
Canisters sit externally as a CanisterBlock on the plexer's top face. A 3x3 grid can be placed in no particular order.
The plexer will draw goo from these canisters and create the template item it is provided as long as it has the right goo.

## Inventory Click Interactions

### Clicking blobs INTO containers

- **Blob onto canister**: entire volume transfers (up to capacity), remainder stays as smaller blob
- **Blob onto empty bucket**: creates bucket of goo (up to 8K mB cap)
- **Blob onto bucket of goo**: adds goo (up to 8K mB total, becomes slurry if different type)
- **Blob onto vat**: entire volume transfers (up to capacity)
- **Blob onto hub**: deferred (nested bundle behavior, TBD)

### Right-click drain (empty cursor)

- **Right-click canister**: drain up to 64,000 mB as blob item
- **Right-click vat**: drain up to 64,000 mB as blob item

### Right-click drain (bucket in cursor)

- **Empty bucket on canister**: drain up to 8,000 mB into new bucket
- **Partial bucket on canister**: drain up to remaining bucket capacity (8K - current)

## Networking

### Copper Fittings vs Gaskets

Gasket UUIDs are opt-in — only created when a choral gasket is physically installed on a face. Hub, canister, tap, and plexer start without gaskets (copper fittings only). Copper fittings provide physical adjacency-based fluid transfer; gaskets provide remote UUID-based transfer.

### Gasket Destinations

A crucible gasket can link to:
1. A hub's intake gasket
2. A specific hub canister slot (1-8)
3. A specific block-canister slot (1-9)
4. A canister in player inventory (the canister retains its link when picked up)

### Chunk Loading

When a player carries a connected canister in their inventory, the destination chunk is kept loaded. This loading cascades upstream through the gasket network: any machine that feeds the loaded destination also has its chunk loaded, recursing until the full upstream graph is exhausted. A player can walk away from their entire production chain and it keeps running as long as they carry the endpoint canister.

## Equipment

### Goo Glove
```
_LL L = Leather
LCL C = Copper Ingot
```
Coper plated glove for handling goo safely. While held, opens a radial menu for blob type selection.

### Goo Gauntlet

Goo Glove + Netherite Ingot. Fire immune.

### Exo Gauntlet

Goo Gauntlet + Exorite. Soul bound. All previous benefits.
