---
id: value-system
title: Goo Value Pipeline
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/data/GooValueRegistry.java, src/main/java/com/mercuriusxeno/goo/data/GooValueDerivation.java, src/main/java/com/mercuriusxeno/goo/data/GooValue.java, src/main/java/com/mercuriusxeno/goo/network/GooValueSync.java]
created: 2026-03-22
updated: 2026-03-22
---

# Goo Value Pipeline

## Overview

Every Minecraft item can have a goo value: a `Map<GooType, Integer>` representing how many mB of each type it decomposes into. Values come from two sources: hand-keyed base values and recipe-derived values.

## Key Classes

- **`GooValue`** -- immutable value record. `Map<GooType, Integer>`. Methods: `add`, `subtract`, `scale`, `divide`, `totalBlobs`, `largestType`, `toGooContents`.
- **`GooValueRegistry`** -- central registry. Singleton at `Goo.GOO_VALUES`. Holds `baseValues`, `effectiveValues`, `deniedItems`, `constants`.
- **`GooValueDerivation`** -- pure derivation engine. No MC dependencies. Takes `List<RecipeInput>` + base values + denied set, returns `DerivationResult`.
- **`RecipeInput`** -- MC-free record: `output` ID, `resultCount`, `ingredientAlternatives` (list of sets), `containerItems` map.
- **`DerivationResult`** -- immutable result: `derivedValues`, `derivationSources`, `effectiveValues`, `conflicts`, `cycles`, `divisibilityLosses`.

## Data Flow

### Server Start
1. `Goo.onServerStarting` -> `GOO_VALUES.loadBaseValues()` -- reads `/data/goo/goo_values/base_values.json` from classpath. Parses `_constants` block, then item entries (value objects or `"denied"` strings).
2. `GOO_VALUES.loadDerivedCache()` -- reads `config/goo_derived_values.json` if it exists. Merges with base via `GooValueDerivation.buildEffectiveValues`.

### Recipe Derivation (`/goo regen`)
1. `GooValueRegistry.deriveFromRecipes(server)` -- adapts all `RecipeHolder`s to `RecipeInput` records.
2. Delegates to `GooValueDerivation.derive(recipes, baseValues, deniedItems, baseOverride)`.
3. Multi-pass loop (max 20): for each output item, finds cheapest recipe using LCD rule. Per ingredient slot: pick cheapest alternative, subtract container item value.
4. Produces `DerivationResult` with derived values, conflicts, cycles (via `DirectedGraphUtils.findStronglyConnectedComponents`), and divisibility losses.
5. Effective values = merge of base + derived. If `BASE_VALUES_OVERRIDE_RECIPES` config is true, base always wins. Otherwise, cheaper value wins.
6. `saveDerivedCache()` persists to disk.

### Client Sync
1. `Goo.onPlayerLoggedIn` -> `GooValueSync.sendToPlayer(serverPlayer)`.
2. Builds `GooValueSyncPayload` from `GOO_VALUES.getEffectiveValues()`.
3. Client receives via `GooValueSyncHandler` -> `GOO_VALUES.receiveClientValues(values)`.
4. On disconnect: `GOO_VALUES.clearAll()` (from `GooClientSetup.onClientDisconnect`).

## Lookup API

- `GOO_VALUES.lookup(Identifier)` -- returns `GooValue` or null.
- `GOO_VALUES.lookup(ItemStack)` -- falls back to `IComponentValueProvider.computeComponentValue` for items with dynamic values.
- Used by: `CrucibleBlockEntity.canInsertItem`, `PlexerBlockEntity.tryReconstitute`, tooltip rendering.

## Audit Data

- `getLastCycles()` -- SCCs in the recipe dependency graph.
- `getLastConflicts()` -- base vs derived disagreements.
- `getLastDivisibilityLosses()` -- recipes where integer division loses blobs.
- `getDerivationSource(Identifier)` -- which recipe produced a derived value.

## JSON Format

Base values: `GooValueJsonFormat.parseGooValue` reads `{ "metal": 5, "rock": 3 }` objects with optional constant references. Constants defined in `_constants` block at JSON root.
