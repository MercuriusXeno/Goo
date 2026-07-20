---
id: story-decouple-003
title: Identifier-based seams for value lookup and registry keys
type: user-story
status: proposed
author: project-manager
consumers: [pilot]
source: decoupling-arch (Layers 2+3, ADR-2, ADR-4)
depends-on: [story-decouple-002]
created: 2026-03-30
updated: 2026-03-30
---

# Identifier-based seams for value lookup and registry keys

## Problem

`CrucibleBlockEntity.canInsertItem`, `insertItem`, `insertContainer`, and `PlexerBlockEntity.tryReconstitute` access `Goo.GOO_VALUES` as a static singleton and call `BuiltInRegistries.ITEM.getKey()` directly. These methods contain testable business logic (goo value comparison, consumption math, container evaluation) but cannot be unit tested without a bootstrapped registry.

## Acceptance Criteria

- `CrucibleBlockEntity` has adapter wrappers for `canInsertItem(ItemStack)`, `insertItem(ItemStack, int)`, `insertContainer(ItemStack)` that resolve the `Identifier` via `BuiltInRegistries` and delegate to testable seams
- Testable seams accept `Identifier` + `IGooValueLookup` and call `lookup(Identifier)` — no `BuiltInRegistries` in the seam
- `PlexerBlockEntity.tryReconstitute()` has an adapter wrapper; testable seam accepts `Identifier targetId, IGooValueLookup lookup`
- `IContainerEvaluator.evaluate()` signature changes to `evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup)`
- `ContainerEvaluator` implementation updated to match new signature
- New unit tests:
  - `CrucibleInsertTest` — tests `canInsertItem(Identifier, IGooValueLookup)` and `insertItem(Identifier, int, IGooValueLookup)` with test `GooValueRegistry` via `setBaseValues()`
  - `PlexerReconstitutionTest` — tests `tryReconstitute(Identifier, IGooValueLookup)` with known values
  - `ContainerEvaluatorTest` — tests `evaluate(Identifier, ItemStack, IGooValueLookup)` with controlled values
- All new tests run without registry bootstrap (`./gradlew gooTest`)
- Existing tests still pass
- In-game: crucible item insertion, plexer reconstitution, container melting all function correctly

## Scope

**In scope:** `CrucibleBlockEntity`, `PlexerBlockEntity`, `IContainerEvaluator`, `ContainerEvaluator`, new test classes.

**Out of scope:** `GooCommand` (stays in adapter boundary). `GooValueRegistry` recipe derivation methods (already decoupled). Fluid-to-type resolution (Layer 5, deferred).

## Notes

Depends on story-decouple-002 (IGooValueLookup narrowing). This is the highest-value story in the series — unlocks 3-5 new test classes covering core game logic.
