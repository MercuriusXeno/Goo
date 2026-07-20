---
id: story-017-anticlip
type: design
status: draft
author: tech-lead
consumers: [dev-agent, critic, test-architect]
created: 2026-03-22
updated: 2026-03-22
---

# Component Design: HUD Anti-Clip Panel Repositioning

Ref: `story-017-contracts.md` for method signatures and shared types.

## Overview

Two changes:
1. Extract panel height computation so it runs before `renderPanel()`.
2. Add `InWorldHud.antiClipLift()` to adjust anchor Y when the panel would clip into the block above.

No new classes. No new interfaces. Two new static methods (one shared, one private).

## Integration Points in CanisterHudRenderer

### Call site: `onAfterEntities()` (line 86)

Current flow:
```
getTarget() -> updateState() -> lookupSlotData() -> renderPanel()
```

New flow:
```
getTarget() -> updateState() -> lookupSlotData() -> computeRowCount() -> antiClipLift() -> renderPanel()
```

The anti-clip adjustment happens between data lookup and rendering. The adjusted lift replaces `trackedLift` for the current frame only — it does NOT mutate the tracked state (the animation state machine must track the raw anchor for smooth transitions when the target changes).

### Concrete change in `onAfterEntities()`

After the `lookupSlotData()` call and before `renderPanel()`, insert:

```java
int rowCount = computeRowCount(data);
double panelHeight = InWorldHud.panelWorldHeight(rowCount);
double adjustedLift = InWorldHud.antiClipLift(
    Minecraft.getInstance().level, trackedPos, trackedLift, panelHeight);
```

Then pass `adjustedLift` to `renderPanel()` instead of reading `trackedLift` there.

### Change in `renderPanel()` signature

Current:
```java
private static void renderPanel(PoseStack poseStack, Camera camera,
        SlotData data, BlockPos pos, int slot)
```

Add `double lift` parameter:
```java
private static void renderPanel(PoseStack poseStack, Camera camera,
        SlotData data, BlockPos pos, int slot, double lift)
```

Inside `renderPanel()`, replace `trackedLift` with the `lift` parameter in the `ry` computation (line 257):
```java
double ry = pos.getY() + lift - cam.y;  // was: trackedLift
```

All other uses of `trackedCx`, `trackedCz`, `trackedFace`, `trackedBlockAbove` remain as static field reads — they are not affected by anti-clip.

## Panel Height Extraction

### What moves

The row-counting logic at lines 315-318 of `renderContent()` is duplicated into `computeRowCount(SlotData)`. The existing code in `renderContent()` can either:
- (a) Call `computeRowCount()` itself to avoid duplication, or
- (b) Keep its inline version (since it also computes `hasLabel`, `hasUpgrade`, etc. for rendering purposes).

**Recommendation: option (b).** The inline code in `renderContent()` is tightly coupled with the rendering decisions that follow it. Extracting it to call `computeRowCount()` would require either re-deriving the booleans or passing them around. Duplication of 5 lines of trivial arithmetic is cheaper than coupling.

### `computeRowCount` approximation note

The full row count in `renderContent()` depends on `formatPartnerRow()` which queries `TunerAwaitState`. The `computeRowCount()` helper approximates this: if `topPartner` or `bottomPartner` is non-null, count the row. If both are null but an await state exists, the row count may be off by 1. This is acceptable — the panel would pull back by ~0.17 blocks less than ideal, which is invisible. If exact parity matters, the dev agent can pass the await-state info into `computeRowCount()`.

## InWorldHud Changes

### New method: `panelWorldHeight(int rowCount)`

Pure arithmetic. See contracts doc for signature. Place after `computeMaxRowWidth()` (line 299) to keep sizing utilities grouped.

### New method: `antiClipLift(Level, BlockPos, double, double)`

Static utility. See contracts doc for full algorithm. Place after `panelWorldHeight()`.

Import additions to `InWorldHud`:
```java
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
```

(Some of these may already be imported — check before adding.)

## What Does NOT Change

- `Target` record: no new fields. The lift is adjusted after target computation.
- `updateState()`: tracks raw lift from `getTarget()`. Anti-clip is a per-frame visual correction, not state.
- `hasFullBlockAbove()`: remains as the face-selection heuristic. Anti-clip is complementary.
- `applyRotation()`: unchanged. Anchor adjustment is before rotation per ADR-3.
- Hub targeting (`hubTarget`, `hubFrameTarget`): out of scope per story.
- `renderContent()`: unchanged. It receives the panel at the adjusted position; its internal layout is unaffected.

## Test Strategy Sketch

### Unit-testable

`InWorldHud.panelWorldHeight(int)` — pure arithmetic, trivially testable:
- 1 row -> `(6 + 11) / 64.0 = 0.265625`
- 5 rows -> `(6 + 55) / 64.0 = 0.953125`

`InWorldHud.antiClipLift(...)` — requires a `Level` mock or a `ServerLevel` in a test harness. The logic is simple enough that a focused integration test is more practical:
- **Full block above:** anchor at 0.75, panel height 0.27 -> overlap = (0.75 + 0.27) - 1.0 = 0.02 -> adjusted = 0.73.
- **Slab above (half block):** `bounds().minY = 0.0`, so neighborFloorY = 1.0. Same as full block.
- **Upper slab above:** `bounds().minY = 0.5`, so neighborFloorY = 1.5. Panel at 0.75 + 0.27 = 1.02 < 1.5 — no adjustment.
- **Air above:** shape is empty -> no adjustment (fast path).
- **Tall panel (5 types):** anchor 0.75, height 0.95 -> overlap = 0.70 -> adjusted = 0.05.

### Visual UAT

- Place canister with solid block above. Panel should not clip into block.
- Place canister with slab above. Panel should adjust less (or not at all for upper slab).
- Place canister in open air. No visible change from current behavior.
- Canister with 5+ goo types (tall panel) next to solid block. Panel should compress downward visibly.
- Side-face panels near ceiling. Panel should pull down to avoid clipping.

## Decision Log

### ADR-D1: Per-frame adjustment, not state mutation

**Context:** Should `antiClipLift` modify `trackedLift` or return a transient value?

**Decision:** Transient value. `trackedLift` stores the raw anchor from `getTarget()`. The anti-clip adjustment is computed fresh each frame and passed to `renderPanel()`.

**Alternatives considered:** Mutating `trackedLift` would eliminate the parameter change to `renderPanel()`, but would corrupt the animation state — if the player moves and the target changes, the smoothing in `updateState()` would start from the adjusted position, not the raw anchor, causing visible jumps.

**Consequences:** One extra parameter on `renderPanel()`. Trivial cost for correct animation behavior.

### ADR-D2: Duplicate row-count logic rather than extract shared helper

**Context:** `computeRowCount()` and `renderContent()` both derive row counts from the same inputs.

**Decision:** Duplicate the 5 lines. `renderContent()` keeps its inline version because it needs the intermediate booleans (`hasLabel`, `hasUpgrade`, etc.) for rendering.

**Alternatives considered:** Extract a shared `RowLayout` record containing all booleans + row count, pass to both callers. Adds a class for what is currently 5 lines of `int` arithmetic. Not worth it for one consumer.

**Consequences:** If row layout changes, two sites need updating. Acceptable — they're in the same file, 50 lines apart.
