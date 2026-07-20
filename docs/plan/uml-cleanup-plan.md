---
id: uml-cleanup-plan
title: UML cleanup plan from class-diagram dives
type: plan
status: proposed
created: 2026-04-25
source_diagrams:
  - build/reports/diagrams/class/ability-effect.puml
  - build/reports/diagrams/class/block.puml
tracks:
  - src/main/java/com/mercuriusxeno/goo/ability/
  - src/main/java/com/mercuriusxeno/goo/effect/
  - src/main/java/com/mercuriusxeno/goo/block/
purpose: Wishlist consolidated from the two umldoclet class-diagram dives on the `uml-tooling-automation` branch. Each item is...
---

# UML cleanup plan

Wishlist consolidated from the two umldoclet class-diagram dives on the `uml-tooling-automation` branch. Each item is a concrete cleanup target surfaced by reading the generated `.puml` text. Items grouped by area; pick scope on the upcoming branch.

## Package structure and nomenclature

### A1. Consolidate `ability/` and `effect/` into one root, dissolve the cycle

The class diagrams expose a circular package dependency: `ChainBehavior` interface lives in `effect/` while four implementors (`DataDrivenChainBehavior`, `EntityEffectBehavior`, `ParameterizedExplosion`, `ProgressiveAreaBlock`) live in `ability/`. Plus `DataDrivenChainBehavior` aggregates `ChainBehavior` instances across the package boundary.

Target layout:

```
ability/
  <abstraction at root: ChainBehavior, AbilityCost, AbilityDefinition, ...>
  mob/   - mob-effect classes (entity-targeted)
  world/ - world-effect classes (block-position-targeted)
  block/ - chain-marker block-side companion (if not relocated under block/)
```

The `effect/` package is legacy nomenclature and should disappear. Use "ability" throughout.

*Source:* ability-effect dive, item 1.
*Risk:* moves a lot of files; downstream imports and any reflection/data references need updating.
*Scope:* large.

### A2. Move `ChainMarkerBlockEntity` to `block/ability/`

Surfaced by block dive. ChainMarker is the only block entity in the slice with no fluid logistics or gasket capability. Its only structural relationship is `behavior : ChainBehavior`. It is conceptually an ability companion living among logistics blocks; it belongs under `block/ability/` (pairs with A1's "ability not effect" naming).

*Source:* block dive, item 8.
*Risk:* low; isolated relocation.
*Scope:* small.

## Abstraction extraction

### B1. Extract `CommonBehavior` abstract base for the `*Behavior` clone family

The eight effect-package `*Behavior` classes (`BlazeBehavior`, `CrystalBehavior`, `FrostBehavior`, `GlowBehavior`, `MetalBehavior`, `NetherBehavior`, `RockBehavior`, `UnstableBehavior`) carry near-identical:

- Fields: `miningDepth`, `pipelineTick`, `placedFace`, `stackCount`
- Constants: `TAG_FACE_SNAPSHOT`, `TAG_MINING_DEPTH`, `TAG_PIPELINE_TICK`, `TAG_STACK_SNAPSHOT`, `DEFAULT_FACE_NAME`, `PREVIEW_DELAY_TICKS`
- Method overrides: `getMinedLayers`, `loadAdditional`, `saveAdditional`, `serverTick`

Roughly 91 duplicated member-points across the eight. Extract to `CommonBehavior` (or a sibling-set base named appropriately under the new ability/ root). Per saved memory `feedback_extract_common_core.md`: extract the common core, do not relocate methods to paired siblings.

*Source:* ability-effect dive, item 2.
*Risk:* needs to land before B2 to avoid double-disruption.
*Scope:* medium.

### B2. Recombine `*Behavior` and `*Executor` pairs

`BlazeBehavior` + `BlazeExecutor`, `FrostBehavior` + `FrostExecutor`, `NetherBehavior` + `NetherExecutor`, `RockBehavior` + `RockExecutor`. Per saved memory `feedback_split_only_on_semantic_boundary.md`: behavior and executor are the same concept; the split is a TMM-shift-right that creates ambiguity without removing duplication. After B1 absorbs the boilerplate, the executors can fold back into their behaviors with no class exceeding the complexity threshold.

*Source:* ability-effect dive, items 2-3 (combined per user feedback).
*Risk:* moderate; preserves call sites of static executor methods. Test coverage will catch breakage.
*Scope:* medium.

### B3. Extract `GasketCapableBlockEntity` abstract base

Seven gasket-capable BEs (`Canister`, `ChoralGasket`, `Crucible`, `Hub`, `Plexer` (pending C1), `Tap`, `Vat`) duplicate ~13 member-points each:

- `gasketRegistryAccess` field
- `gasketState() : GasketState`
- `gasketSyncCallback() : Runnable`
- `clearGasket(GasketRole)`
- `setPartner(GasketRole, [int,] GasketPartner)`
- `supportsRole(GasketRole)`
- `onLoad()`
- `setLevel(Level)`
- `getUpdatePacket()`
- `getUpdateTag(Provider)`
- `loadAdditional(ValueInput)`
- `saveAdditional(ValueOutput)`
- `serverTick(Level, BlockPos, BlockState, ...)`

The `IGasketHolder` interface already has several non-abstract default methods; some of this absorption may happen by leaning on those defaults more aggressively rather than by introducing a new abstract base. Decide on extraction shape during the work.

*Source:* block dive, item 3.
*Risk:* careful with `serverTick` because the BE-specific tick logic still has to layer on top of any base contribution.
*Scope:* medium-large.

## Stale code

### C1. Strip `IGasketHolder` from `PlexerBlockEntity` (and verify Reactor)

The class diagram shows `PlexerBlockEntity ..|> IGasketHolder` but plexer attachment is physical, not gasket-mediated. There is no surface on the plexer to put a gasket. Remove the `implements` and any unreferenced gasket-capability methods that follow. Verify `ReactorBlockEntity` is not similarly stale (currently it does NOT implement IGasketHolder per the diagram, which is correct).

*Source:* block dive, item 2.
*Risk:* low; if anything outside the class still expects the capability, that caller is also stale.
*Scope:* small.

## Wheel removal

### D1. Remove `IGooSource`; normalize on NeoForge `ResourceHandler`

`IGooSource` is implemented by only 2 of 5 fluid handlers in the mod (`GooFluidHandler`, `CanisterSlotFluidHandler`). The other three (`HubFluidHandler`, `InfiniteWaterSource`, `PlayerInventorySlotHandler`) handle fluids without it. Per saved memory `feedback_no_parallel_capabilities.md`: do not bifurcate fluid handling into goo-only vs vanilla. Goo IS a fluid; normalize across vanilla and goo via NeoForge's `ResourceHandler`. The interface either covers all fluid sources or it does not deserve to exist; the current state shows it does not.

Action:
1. Inventory every `IGooSource` use site
2. For each, replace with `ResourceHandler` or a NeoForge capability primitive
3. Delete `IGooSource`

*Source:* block dive, item 5.
*Risk:* touches all five fluid handlers and their consumers; needs care to preserve type-keyed behavior where it actually matters.
*Scope:* medium-large.

## Suggested ordering

1. **C1** (small, isolated, retires stale data first so subsequent diagrams are cleaner)
2. **A2** (small, isolated, single file move)
3. **A1** (large, foundational, but enables clean naming for B1/B2)
4. **B1** then **B2** (B2 depends on B1's common-core extraction)
5. **B3** (independent; can interleave with the others)
6. **D1** (independent; can land any time but worth separating in its own commit/PR)

## Out of scope for this plan

- `*MobEffect` family unification - the prior dive incorrectly proposed merging with `WorldEffect`; the two are different domains. Until mob effects are themselves more finished, no extraction target.
- `AbilityCost` data-driven evolution - WIP, follows a separate trajectory.

## Rerunning the diagrams

After each cleanup item lands, re-run `./gradlew umlDiagramsAbility` or `umlDiagramsBlock` to confirm the relationships shift as expected. The `.puml` files are diff-reviewable; expect arrow counts to drop as duplication collapses.
