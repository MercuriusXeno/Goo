# Client Rendering Synthesis

Cross-cluster findings from the `client-rendering-uml` branch analysis pass.
Five client subpackages analyzed: `ber`, `core`, `interaction`, `hud-overlay`,
`particle`, plus the new `ability` slice we built.

## Patterns that span clusters

### 1. Untyped data smuggling → typed records (highest leverage)
Recurring anti-pattern: structured data moved through untyped slots (parallel
arrays, velocity args, ordinals as doubles). The fix is always a typed record.

| Site | Smuggle | Fix | Status |
|---|---|---|---|
| Hub/Canister/Tap/Reactor RenderState | parallel arrays addressed by slot | `SlotState[]` | shipped (#10) |
| `SlottedCanisterData` (server BE) | parallel arrays | `CanisterSlot[]` | pre-existing (PR #25) |
| Cone-basis math duplication | identical math under different names | `ConeGeometry` primitive | shipped (#13) |
| `OrientedBoomParticle` | `Direction.ordinal()` cast through `xAux` double | `OrientedBoomParticleOptions` record + codec | **pending #17** |

### 2. Per-machine UI/render triplet pattern (high leverage)
Two classes of repeated per-machine scaffolding:

- **BER triplet** (BlockEntityRenderer + FluidRenderer + RenderState): cleaned
  via `SlottedFluidContainer` runner + `SlotState`.
- **HUD pair** (HudRenderer + PanelPainter): three sibling renderers
  (Canister/Crucible/Vat) share the same emerge/retract animation state
  machine. **Pending #16 — HudAnimator scaffold.**

### 3. Shared rendering primitives placement (structural lesson)
`client.ber/` had accumulated cross-cluster primitives (CuboidBounds,
RenderContext, FlatQuadContext, LineContext) consumed by ber, ability,
model, overlay, throwing. Two of the four (`FlatQuadContext`, `LineContext`)
had **zero consumers inside ber/ at all**. Fixed in #15 by moving to
`client/` root next to GooRenderUtil.

**Lesson:** when adding a primitive used by 2+ slices, default its home to
`client/` root, not the slice that introduced it.

### 4. Don't extract abstract bases (deliberate non-pattern)
Several "shape across siblings" temptations were deliberately *not* unified:

- The 6 ability visualizers' implicit `extract(be, state) + submit(...)` shape
- The 3 HUD renderers' shared lifecycle (becomes scaffold + bindings, not base class)
- The BER/FluidRenderer/RenderState shape across machines

Per `feedback_decompose_dont_abstract`: shared shape becomes data-driven
runners (`SlottedFluidContainer`), shared primitives (`ConeGeometry`), or
scaffolds invoked with bindings (proposed `HudAnimator`). **Never** an
abstract base class.

### 5. Color-packing constants duplication (low leverage polish)
`ALPHA_SHIFT = 24` and `RGB_MASK = 0x00FFFFFF` appear in 5+ ability
visualizers. Vanilla `net.minecraft.util.ARGB` already provides equivalents.
**Pending #18.**

### 6. Tooltip component pairs (medium leverage)
3 record/class pairs in `client.tooltip`: GooValue, VanillaFluid, Container.
First two share an icon-plus-text shape; Container is multi-row outlier.
**Pending #14 — generic `IconValueClientTooltipComponent`.**

## Misplacement findings (resolved)

| Item | From | To | Why |
|---|---|---|---|
| `AuroraFadeWallRenderer` (Glow) | `client.ber/` | `client.ability/GlowFadeVisual` | ability effect, not BER |
| `CrystalFissureRenderer` | `client.ber/` | `client.ability/CrystalCloudVisual` | ability effect |
| `NetherBlackHoleRenderer` | `client.ber/` | `client.ability/NetherSphereVisual` | ability effect |
| `NetherLensEffect`, `NetherLensClientEvents` | `client.lens/` | `client.ability/` | nether ability ecosystem |
| `VatStackAggregator` | `client/` root | `client.machine/` | HUD data prep, machine-flavored |
| `FluidFaceEmitter` | `client.ber/` | `client.model/` | item-form only |
| `CrystalSceneCopy` | `client.ber/` | (deleted) | dead code, no consumers |

## Cross-cluster boundaries to monitor

- `GooTargetHighlighter` (overlay, 883 lines) is the central aim/highlight
  coordinator pulling state from hud, model, throwing, ability, network. Big
  but a meaningful internal split needs an understanding of throw-state
  cross-slice flow first. **Defer.**
- `BlobFlightRenderer` (throwing, 912 lines) holds per-goo-type flight
  variants (default core/shell/tail, Glow beam, Metal dart). The metal dart
  cone math is now shared via ConeGeometry; the Glow/Metal flight variants
  could later move to `client.ability/` if we want to fully separate
  "marker visual" from "flight visual" concerns. **Defer.**

## Implementation queue (ranked by leverage)

1. **#16 HudAnimator scaffold** — biggest line-count win, ~600 lines dedup
2. **#17 OrientedBoomParticleOptions codec** — small change, kills a known
   anti-pattern, well-known vanilla pattern
3. **#14 generic IconValueClientTooltipComponent** — moderate, ~50 line dedup
4. **#18 ARGB constants** — pure polish

## Meta-lessons captured for future sessions

- **Default new primitives to `client/` root**, not whichever slice spawned
  them. Verify the consumer set crosses slices before placing in a slice.
- **Ability effects belong in `client.ability/`**, not `client.ber/`. BERs
  render block entities; ability visuals render *abilities*. The temptation
  to default to `ber/` because that's where similar code lives is wrong.
- **Repeated shape ≠ abstract base.** The fix is always: data-driven runner,
  shared primitive, or scaffold-with-bindings. The user has been consistent
  on this and it should not need re-litigating.
- **The "smuggle through wrong slot" anti-pattern** is the most common smell
  to look for when something feels load-bearing in a weird way. The fix is
  always: typed record + codec.
