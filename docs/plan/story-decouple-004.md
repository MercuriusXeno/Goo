---
id: story-decouple-004
title: Wire IGasketRegistryAccess into GasketPusher
type: user-story
status: proposed
author: project-manager
consumers: [pilot]
source: decoupling-arch (Layer 4, ADR-3)
created: 2026-03-30
updated: 2026-03-30
---

# Wire IGasketRegistryAccess into GasketPusher

## Problem

`GasketPusher.resolveTargetGasketId()` calls `GasketRegistry.get(serverLevel)` directly, and the static `forceTransmitterChunk()` takes `ServerLevel` to do the same. `CanisterBlockEntity` already uses `IGasketRegistryAccess` for identical purpose — the inconsistency blocks testing of pusher target resolution and chunk forcing logic.

## Acceptance Criteria

- `GasketPusher` constructor accepts `IGasketRegistryAccess` as 7th param
- `resolveTargetGasketId()` uses the injected `IGasketRegistryAccess` instead of `GasketRegistry.get(serverLevel)`
- `forceTransmitterChunk` static signature changes from `(UUID, ServerLevel, BlockPos)` to `(UUID, IGasketRegistryAccess, ServerLevel, BlockPos)`
- All 4 caller sites updated:
  - `CrucibleBlockEntity.onLoad()` — passes `() -> GasketRegistry.get(serverLevel)` inline
  - `CanisterBlockEntity.forceAllTransmitterChunks()` — passes existing `gasketRegistryAccess` field
  - `HubBlockEntity.forceAllTransmitterChunks()` — passes `IGasketRegistryAccess` (may need field or inline lambda)
  - `VatBlockEntity.onLoad()` — passes inline lambda
- `CrucibleBlockEntity` and `CanisterBlockEntity` pass `IGasketRegistryAccess` to `GasketPusher` constructor (captured in `setLevel()`)
- New unit tests:
  - `GasketPusherTargetResolutionTest` — tests `resolveTargetGasketId` with test `GasketRegistry`
  - `GasketPusherChunkForcingTest` — tests `forceTransmitterChunk` with test `IGasketRegistryAccess`
- All new tests run without registry bootstrap (`./gradlew gooTest`)
- Existing tests still pass
- In-game: gasket push and chunk forcing still work

## Scope

**In scope:** `GasketPusher`, `CrucibleBlockEntity`, `CanisterBlockEntity`, `HubBlockEntity`, `VatBlockEntity`, new test classes.

**Out of scope:** Other `GasketRegistry` usages outside the pusher path.

## Notes

Independent of stories 001-003 — can be worked in parallel. Constructor grows to 7 params; at the smell threshold but all are genuinely distinct suppliers.
