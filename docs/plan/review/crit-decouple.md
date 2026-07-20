---
id: crit-decouple
title: Review of decoupling-arch.md
type: review
lifecycle: ephemeral
status: proposed
author: critic
reviewed-artifact: decoupling-arch
grade: A-
highest-severity: suggestion
created: 2026-03-30
updated: 2026-03-30
---

# Review: Registry and Singleton Decoupling Architecture

## Blockers

None.

## Concerns

### C1: `IGooValueLookup.lookup(ItemStack)` still calls `BuiltInRegistries` internally

**What:** The doc proposes injecting `IGooValueLookup` into `canInsertItem`, `insertItem`, `insertContainer`, and `tryReconstitute` (Sections 3.2, 3.3). The interface exists and `GooValueRegistry` implements it. But the `lookup(ItemStack)` method at `GooValueRegistry.java:366` calls `BuiltInRegistries.ITEM.getKey(stack.getItem())` internally. The doc never addresses this.

**Why:** The proposed test pattern -- `canInsertItem(stack, lookup)` with a test `GooValueRegistry` via `setBaseValues()` -- will fail at test time because `lookup(ItemStack)` resolves the stack's registry key through `BuiltInRegistries`, which requires a bootstrapped registry. The only way this works in tests is if the test `GooValueRegistry` is pre-populated with `Identifier` keys and tests call `lookup(Identifier)` directly, but the method signatures in the domain layer accept `ItemStack` and call `lookup(ItemStack)`.

**Risk:** The entire Layer 2 test gain (Sections 3.2, 7) is blocked by this hidden coupling. `CrucibleInsertTest`, `PlexerReconstitutionTest`, and `ContainerEvaluatorTest` will hit `BuiltInRegistries` during execution and crash without a running game instance, defeating the purpose of the decoupling.

**Actionable:** The architect must decide whether `lookup(ItemStack)` should resolve registry keys via an injected `Function<Item, Identifier>` (same pattern as Layer 3), or whether the domain-layer methods should accept `Identifier` instead of `ItemStack`. Either way, the current design has a gap here.

### C2: `IContainerEvaluator.evaluate()` signature says `IGooValueLookup` but actual code takes `GooValueRegistry`

**What:** Section 4.6 proposes changing `IContainerEvaluator.evaluate(ItemStack, GooValueRegistry)` to accept `IGooValueLookup`. ADR-4 calls this "non-breaking" because `GooValueRegistry implements IGooValueLookup`.

**Why:** The actual `ContainerEvaluator` implementation (`ContainerEvaluator.java:26`) calls `registry.lookup(item)` (line 62) and `registry.lookup(container)` (line 73) -- both are the `lookup(ItemStack)` overload. If the parameter type narrows to `IGooValueLookup`, the method `lookup(ItemStack)` is available on the interface. But per C1 above, the real implementation of that method calls `BuiltInRegistries` -- so the narrowing is cosmetically correct but doesn't actually unlock testability.

**Risk:** ADR-4 creates a false sense of progress. The signature change compiles, the test still crashes.

**Actionable:** ADR-4 must be sequenced after resolving C1. The two are not independent.

### C3: Factual errors in the affected-files inventory

**What:** Section 3.1 claims VatBlock uses `stack.is(GooItems.CANISTER.get())` and lists it as an affected file for `.is()` conversion. Section 3.1 also claims `PlayerInventorySlotHandler` uses `stack.is(GooItems.CANISTER.get())`.

**Why:** Grepping the actual source: `VatBlock.java` has zero `stack.is(GooItems.CANISTER.get())` calls -- it references `GooItems` only at line 194 for `CHORAL_GASKET`. `PlayerInventorySlotHandler.java` uses `instanceof CanisterItem` (lines 82, 93), not `.is()`. The doc's affected-files list does not match the codebase.

Meanwhile, the grep reveals `.is()` calls the doc *misses*: `CrucibleBlock.java:205,217` uses `stack.is(GooItems.RUNE_INK.get())`, and `PlexerBlock.java:54` uses `stack.is(GooItems.RUNE_INK.get())`. These are additional coupling sites in the domain layer that the doc does not account for.

**Risk:** Implementation follows the doc's list, converts files that don't need conversion, misses files that do. The convention test (Section 5.1) would later catch the missed sites, but the coverage estimate (Section 7) and implementation order (Appendix A) are based on wrong data.

**Actionable:** The architect must re-audit `.is()` call sites before implementation. The "5 sites" claim is wrong; the actual number and locations differ.

### C4: File paths throughout the document are wrong

**What:** Section 3.1 references `CrucibleBlockEntity.java`, `PlexerBlockEntity.java`, `CanisterBlockEntity.java` as being in `com.mercuriusxeno.goo.block.entity`. The CLAUDE.md project instructions also reference this wrong package path.

**Why:** These files actually live in `com.mercuriusxeno.goo.block` (no `.entity` subpackage). Similarly, `GooValueRegistry.java` is in `com.mercuriusxeno.goo.data`, not `com.mercuriusxeno.goo.values`. The doc's Section 5.1 import ban rule says "Files under `com.mercuriusxeno.goo.block`" which happens to be correct for the *actual* package structure, but the line references elsewhere are misleading.

**Risk:** Developers following the doc will look in wrong packages. The convention test regex patterns (Section 5.1) may target wrong paths if the dev takes the doc's package references literally.

**Actionable:** Fix all package and path references to match actual source tree.

### C5: `CanisterBlockEntity.buildCanisterFromPending()` needs `new ItemStack(GooItems.CANISTER.get())`, not `instanceof`

**What:** Section 3.1 says `CanisterBlockEntity.java:250` uses `GooItems.CANISTER.get()` in `buildCanisterFromPending` and recommends "Convert to `CanisterItem` class reference." But the actual usage (line 250, 267) is `new ItemStack(GooItems.CANISTER.get())` -- it's constructing a new ItemStack, not checking a type.

**Why:** You can't convert `new ItemStack(GooItems.CANISTER.get())` to an `instanceof` check. This is an item *creation* site, not a type *check* site. Constructing an `ItemStack` from a `DeferredItem` is fundamentally different from comparing against one. The deferred holder `.get()` call is unavoidable here because you need the actual `Item` instance to construct the stack.

**Risk:** The doc's recommendation for this site is inapplicable. If followed literally, it would produce a compile error. The real fix requires either leaving this in the adapter boundary (it's a wiring concern) or injecting a `Supplier<Item>` for the canister item -- neither of which the doc proposes.

**Actionable:** Remove `CanisterBlockEntity.java` from the Layer 1 affected files list (it's not a type check). Evaluate whether `buildCanisterFromPending` and `buildCanisterFromStack` should accept an `Item` parameter or stay in the adapter boundary.

### C6: `GasketPusher` constructor param count claim is wrong

**What:** Section 8, R3 says "GasketPusher already takes 6 constructor params. Adding `IGasketRegistryAccess` makes 7."

**Why:** The actual constructor (`GasketPusher.java:55-60`) already takes 6 params and does NOT include `IGasketRegistryAccess`. But the design proposes adding it. The problem is that Section 3.4 says "Add `IGasketRegistryAccess` as a constructor param" while `GasketPusher` accesses the registry via `GasketRegistry.get(serverLevel)` at line 213 using the `level` supplier it already has. Adding `IGasketRegistryAccess` is valid for testability, but R3 should acknowledge 7 params is already at the smell threshold, and the `forceTransmitterChunk` static method (line 224) takes `ServerLevel` directly -- converting it to instance or parameterized breaks callers in `CrucibleBlockEntity.onLoad()` (line 666) and `CanisterBlockEntity.forceAllTransmitterChunks()` (line 576) that both call the static method without having a `GasketPusher` instance.

**Risk:** The static-to-instance conversion of `forceTransmitterChunk` is more invasive than the doc suggests. Both `CrucibleBlockEntity` and `CanisterBlockEntity` call this static method from `onLoad()`, which runs before pushers may be fully initialized. The doc hand-waves this as "constructor change + test expansion."

**Actionable:** The architect must specify how `forceTransmitterChunk` callers will be updated. The static method is called by block entities that don't own the pusher for that gasket (the *receiver* forces the *transmitter's* chunk). This is structurally different from the pusher's own `resolveTargetGasketId`.

## Suggestions

### S1: `GooValueRegistry implements IGooValueLookup` but `Goo.GOO_VALUES` is typed as `GooValueRegistry`

**What:** `Goo.java:50` declares `public static final GooValueRegistry GOO_VALUES`. Section 3.2 says pass `Goo.GOO_VALUES` as `IGooValueLookup`.

**Why:** This works (widening conversion), but the singleton remains the concrete type everywhere it's accessed. The doc could strengthen the boundary by recommending the field type change to `IGooValueLookup` on `Goo.java`. This would make the compiler enforce the narrower contract at the declaration site, not just at the callee parameter.

**Actionable:** Change `Goo.GOO_VALUES` field type to `IGooValueLookup` (with a cast/accessor for the few places that need the full `GooValueRegistry` -- `GooCommand`, `onServerStarting`).

### S2: Plexer slot count discrepancy between DESIGN-MACHINES.md and actual code

**What:** DESIGN-MACHINES.md says "Base: 2 canister slots. Each runic matrix adds 1 slot, max 5 matrices = 7 slots total." The actual `PlexerBlockEntity` has `maxSlots = 2`, `upgradeToTriplexer()` going to 3, and a hardcoded `NonNullList.withSize(3, ...)`.

**Why:** Not a decoupling-arch concern directly, but the decoupling doc inherits this discrepancy when referencing Plexer behavior. If the Plexer is later expanded to 7 slots per DESIGN-MACHINES, the `Function<Item, Identifier>` approach for `tryReconstitute` remains valid -- no impact on the decoupling design. Noting for completeness.

### S3: Convention test should also ban `Goo.GOO_VALUES` in domain code

**What:** Section 5.2 mentions a "Test Canary" that greps for `Goo\.GOO_VALUES` but frames it as reviewer convention. Section 5.1 only bans `GooItems`, `GooBlocks`, `GooFluids` imports.

**Why:** `Goo.GOO_VALUES` is the singleton access pattern the decoupling is trying to eliminate. It should be in the convention test alongside the registry import bans, not just a reviewer suggestion.

**Actionable:** Add `Goo.GOO_VALUES` to the `ConventionTest` banned-patterns list for domain layer files.

## Grade: B

The architecture is sound in structure. The three-layer diagram, the YAGNI-first interface catalog, the ADR decisions, and the alignment with DESIGN-TESTING.md's two-phase seam strategy are all well-reasoned. The doc correctly identifies which interfaces already exist and avoids creating unnecessary new ones. The `instanceof` over `IItemClassifier` decision (ADR-1) is the right call.

Deductions:

- **C1 + C2** are the heaviest: the `lookup(ItemStack)` hidden `BuiltInRegistries` dependency means the entire Layer 2 test gain -- the doc's highest-value deliverable -- does not work as described. This is not a blocker because the fix is straightforward (add a `Function<Item, Identifier>` to the lookup path or restructure to use `Identifier`-based lookups), but it's a significant gap in the analysis.
- **C3 + C5** show the audit of current call sites was done from memory or outdated information rather than from grepping the actual source. Multiple affected-file claims are wrong. This is recoverable but undermines trust in the implementation plan's accuracy.
- **C4** is cosmetic but pervasive.
- **C6** reveals an under-specified migration path for a static utility method that cannot simply become an instance method.

## Risk Summary: What Goes Wrong If Built As-Is

1. **Layer 2 tests crash on `BuiltInRegistries`** because `IGooValueLookup.lookup(ItemStack)` internally resolves registry keys. Dev writes `CrucibleInsertTest`, it fails with `NullPointerException` or `IllegalStateException` from the registry, dev wastes a session diagnosing why the decoupled interface still touches Minecraft internals.

2. **Wrong files get modified** in Layer 1: `VatBlock` and `PlayerInventorySlotHandler` are listed for `.is()` conversion but already use `instanceof`. Meanwhile `CrucibleBlock` and `PlexerBlock` have `.is(GooItems.RUNE_INK.get())` calls that the doc doesn't mention, leaving coupling points intact after "Layer 1 complete."

3. **`CanisterBlockEntity.buildCanisterFromPending`** cannot be converted to `instanceof` -- it's a construction site, not a check site. Attempting the doc's recommendation produces a compile error, blocking the Layer 1 PR.

4. **`forceTransmitterChunk` static method** resists the proposed GasketPusher constructor injection. Callers in `CrucibleBlockEntity.onLoad()` and `CanisterBlockEntity.forceAllTransmitterChunks()` don't have a `GasketPusher` instance available. The migration path is unspecified, leading to ad-hoc solutions during implementation.

## Pass 2

### Finding Resolution Status

**C1: `IGooValueLookup.lookup(ItemStack)` hidden `BuiltInRegistries` coupling — RESOLVED.**

The revision addresses this thoroughly. Section 3.2 now proposes removing `lookup(ItemStack)` from the `IGooValueLookup` interface entirely (Option C), keeping it as a concrete method on `GooValueRegistry` for adapter code. Domain-layer methods accept `Identifier` + `IGooValueLookup` and call `lookup(Identifier)`, which is a pure map get. The adapter wrapper resolves `ItemStack -> Identifier` via `BuiltInRegistries`. ADR-2 documents this decision with alternatives considered. The concrete code example in Section 3.2 and Section 8 R2 both show the pattern clearly.

Source verification: `IGooValueLookup.java` currently still has `lookup(ItemStack)` on the interface (line 29), and `GooValueRegistry.java:366` does call `BuiltInRegistries.ITEM.getKey()` as the doc claims. The proposed removal is accurate about the current state.

**C2: `IContainerEvaluator.evaluate()` signature — RESOLVED.**

Section 4.6 now proposes `evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup)` and ADR-4 explicitly states it is sequenced after ADR-2. The `Identifier` parameter handles value lookup; the `ItemStack` is retained for content extraction (what items are inside the container). This is correct — `ContainerEvaluator.java:62` calls `registry.lookup(item)` and line 73 calls `registry.lookup(container)`, both using the `ItemStack` overload. The revision properly identifies that both calls need the `Identifier`-based path after conversion.

Source verification: `IContainerEvaluator.java` currently shows `evaluate(ItemStack container, GooValueRegistry registry)` at line 40. The proposed signature change is a real change with correct rationale.

**C3: Factual errors in affected-files inventory — RESOLVED.**

Section 3.1 now correctly separates three patterns: Pattern A (`.is()` type checks, 3 domain-layer sites — `CrucibleBlock.java:205,217` and `PlexerBlock.java:54`, all `RUNE_INK`), Pattern B (item construction, 7 sites listed accurately), and Pattern C (`instanceof` checks, ~30 sites, already clean). The false claims about `VatBlock` and `PlayerInventorySlotHandler` using `.is()` are gone. `PlayerInventorySlotHandler` is correctly listed under Pattern C as using `instanceof`.

Source verification: `CrucibleBlock.java:205` does show `stack.is(GooItems.RUNE_INK.get())`. `PlexerBlock.java:54` does show `stack.is(GooItems.RUNE_INK.get())`. Line counts match.

**C4: Wrong package paths — RESOLVED.**

The revision no longer references `com.mercuriusxeno.goo.block.entity` or `com.mercuriusxeno.goo.values`. All file references use bare filenames with line numbers (e.g., `CrucibleBlock.java:205,217`). Section 5.1 correctly targets `com.mercuriusxeno.goo.block` and `com.mercuriusxeno.goo.item` as domain layer packages.

**C5: `CanisterBlockEntity.buildCanisterFromPending` misclassified as type check — RESOLVED.**

Section 3.1 Pattern B now correctly identifies this as an item *construction* site (`new ItemStack(GooItems.CANISTER.get())`), not a type check. The recommendation is Option B: leave in the adapter boundary. The doc explicitly states "Item construction is adapter-boundary work" and rejects `Supplier<Item>` wrapping as YAGNI. ADR-1 documents the decision.

Source verification confirms these are construction sites, not check sites. The recommendation is sound.

**C6: `GasketPusher` constructor param count and `forceTransmitterChunk` migration — RESOLVED.**

Section 3.4 now correctly states 6 current params, adding `IGasketRegistryAccess` makes 7, and acknowledges 7 is "at the smell threshold." The `forceTransmitterChunk` migration is fully specified: the method stays static but gains an `IGasketRegistryAccess` parameter in addition to `ServerLevel`. Callers create inline lambdas (`() -> GasketRegistry.get(serverLevel)`). The doc explains why the method must remain static (callers force the *transmitter's* chunk from the *receiver's* side, without owning a `GasketPusher` instance). R3 acknowledges 7 params and notes the group-into-record escape hatch.

Source verification: `GasketPusher.java:55-60` shows 6 params. `forceTransmitterChunk` at line 224 is indeed static, takes `(UUID, ServerLevel, BlockPos)`, and calls `GasketRegistry.get(serverLevel)` at line 229. The proposed signature change to `(UUID, IGasketRegistryAccess, ServerLevel, BlockPos)` is accurate.

**S1: `Goo.GOO_VALUES` field type — NOT ADDRESSED (downgraded to non-issue).**

The revision does not change `Goo.GOO_VALUES` field type to `IGooValueLookup`. However, Section 5.2 now bans `Goo.GOO_VALUES` access from domain-layer code via convention test. The adapter wrappers pass `Goo.GOO_VALUES` by-value to the `IGooValueLookup` parameter. Changing the field's declared type would add a cast in `GooCommand` and `onServerStarting` for zero additional safety — the convention test already catches domain-layer violations. This is fine as-is. Deleted.

**S2: Plexer slot count discrepancy — NOT IN SCOPE.**

Correctly not addressed. This is a DESIGN-MACHINES.md concern, not a decoupling-arch concern.

**S3: Convention test should ban `Goo.GOO_VALUES` — RESOLVED.**

Section 5.2 now explicitly adds `Goo.GOO_VALUES` to the `ConventionTest` banned-patterns list with a regex scan for `Goo\.GOO_VALUES` in domain-layer packages. Section 5.3 adds `BuiltInRegistries` as a separate ban. Both are enforced alongside the registry import bans.

### Fix-Induced Regressions

**R-NEW-1: `ContainerEvaluator.evaluate()` recursive call needs `Identifier` resolution per nested item (SUGGESTION)**

**What:** Section 4.6 changes the signature to `evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup)`. The current implementation at `ContainerEvaluator.java:32-33` recursively calls `evaluate(item, registry)` for nested containers. After the signature change, the recursive call needs an `Identifier` for each nested item. The adapter layer resolves the top-level container's `Identifier`, but the `ContainerEvaluator` itself must resolve nested items' Identifiers — which means it needs `BuiltInRegistries` access inside the recursion.

**Why:** The doc's proposed signature solves the top-level call but pushes the `BuiltInRegistries` coupling into the recursive body. `evaluateItemOrEject` at line 62 calls `registry.lookup(item)` — after the change, this needs an `Identifier` for each `item` inside the container. The evaluator either needs an `Identifier` resolver (contradicts the "no `Function<Item, Identifier>` params" decision from ADR-2) or the `ContainerEvaluator` itself stays in the adapter boundary.

**Risk:** Low. The fix is straightforward: either (a) the `ContainerEvaluator` stays in the adapter boundary and tests mock it via the `IContainerEvaluator` interface, or (b) it accepts a `Function<ItemStack, Identifier>` resolver for recursive resolution. Option (a) is consistent with the doc's own YAGNI principle — the evaluator's logic is simple (sum and eject), and the value of testing it independently is modest.

- **Actionable** because the architect must decide whether `ContainerEvaluator` is adapter-boundary or domain-layer code, since the current doc implicitly treats it as domain-layer (proposes testing it) but the recursive `BuiltInRegistries` dependency makes it adapter-boundary.
- **Consequential** because if unaddressed, implementing the signature change will produce a method that compiles but still calls `BuiltInRegistries` internally for nested items, reproducing the exact C1 problem at a different level of recursion.

### Cross-Artifact Ripple Effects

**DESIGN-TESTING.md:** No conflict. The revision follows the SHELTERED_LOGIC pattern correctly — adapter wrappers are thin, testable seams accept interfaces and primitives. The `Identifier`-based overload pattern (Section 3.2 code example) is textbook Phase 1 extraction.

**test-ethos.md:** No conflict. The proposed tests use `GooValueRegistry` with `setBaseValues()` (existing test infrastructure), Identifiers as test data, and pure method calls. No filesystem, network, or clock access. ArchUnit convention tests align with the `ConventionTest.java` pattern already in the project.

**DESIGN-MACHINES.md:** The Plexer slot count discrepancy (S2) persists but is orthogonal to the decoupling architecture. No ripple effect.

**DESIGN-CAPABILITIES.md:** Not referenced by the revision. The capability registration in `Goo.java` is correctly classified as adapter-boundary (Section 3.1 "Adapter-boundary sites"). No conflict.

### Updated Grade: A-

The revision substantially addresses all six concerns and both actionable suggestions from pass 1. The affected-files inventory is now accurate (verified against source). The `lookup(ItemStack)` hidden coupling — the heaviest pass 1 finding — has a clean, well-reasoned solution (remove from interface, use `Identifier`-based lookups in domain layer). The `forceTransmitterChunk` migration path is fully specified. The Pattern A/B/C taxonomy in Section 3.1 correctly separates type checks, construction sites, and already-clean `instanceof` usage.

One new suggestion (R-NEW-1) identifies a secondary `BuiltInRegistries` leak in `ContainerEvaluator`'s recursive body, but the fix is straightforward and the risk is low.

Deduction from A:
- **R-NEW-1** is a genuine gap — the recursive `evaluate()` call inside `ContainerEvaluator` still needs `BuiltInRegistries` to resolve nested items' Identifiers. This is a suggestion-level finding because the fix is obvious (declare `ContainerEvaluator` as adapter-boundary code, or add a resolver param), but it should be decided before implementation to avoid mid-PR design churn.

### Updated Risk Summary

The pass 1 risks are resolved:

1. ~~Layer 2 tests crash on `BuiltInRegistries`~~ — Fixed. Domain-layer methods use `lookup(Identifier)`. No registry cascade.
2. ~~Wrong files modified in Layer 1~~ — Fixed. Accurate Pattern A/B/C inventory.
3. ~~`buildCanisterFromPending` compile error~~ — Fixed. Correctly classified as adapter-boundary construction site.
4. ~~`forceTransmitterChunk` migration unspecified~~ — Fixed. Stays static, gains `IGasketRegistryAccess` param.

Remaining risk: `ContainerEvaluator`'s recursive evaluation needs an adapter-boundary or resolver decision before the `IContainerEvaluator.evaluate()` signature change ships. Low severity — the evaluator is simple and the decision is straightforward.
