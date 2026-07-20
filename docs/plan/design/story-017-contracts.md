---
id: story-017-contracts
type: design
status: draft
author: tech-lead
consumers: [dev-agent, critic, test-architect]
created: 2026-03-22
updated: 2026-03-22
---

# Story-017 Contracts: HUD Anti-Clip Panel Repositioning

## Shared Types

No new shared types. The anti-clip logic uses existing types:
- `BlockPos`, `Direction`, `Level` (Minecraft)
- `AABB` (`net.minecraft.world.phys.AABB`)
- `VoxelShape` / `BlockState.getCollisionShape()` (Minecraft)

The `CanisterHudRenderer.Target` record gains no new fields. The `lift` field is adjusted in-place before the record is constructed — the anti-clip output is a modified `lift` value, not a separate structure.

## Contract: Panel Height Computation

### `InWorldHud.panelWorldHeight(int rowCount)`

```java
/**
 * Computes the world-space height of a HUD panel given its row count.
 * This is the panel's vertical extent in blocks, used for anti-clip testing.
 *
 * @param rowCount total rows (header rows + goo type rows)
 * @return height in blocks (world units)
 */
public static double panelWorldHeight(int rowCount) {
    float pixelHeight = BORDER * 2 + rowCount * ROW_HEIGHT;
    return pixelHeight * PIXEL_SCALE;
}
```

**Inputs:** `rowCount` — total number of content rows (label + gasket + upgrade + goo types). Same computation currently in `renderContent()` at line 320.

**Outputs:** World-space height in blocks (double). E.g., 3 goo types with label = 4 rows -> `(6 + 44) / 64 = 0.78125` blocks.

**Invariants:**
- Always positive (rowCount >= 1 for any visible panel).
- Deterministic: pure arithmetic, no state.

### `InWorldHud.panelRowCount(SlotData)` — NOT a shared method

Row counting depends on `SlotData` fields that are `CanisterHudRenderer`-specific (label, matrices, partners). This stays in `CanisterHudRenderer` as a private helper. Only `panelWorldHeight` is shared in `InWorldHud`.

## Contract: Anti-Clip Vertical Adjustment

### `InWorldHud.antiClipLift(Level level, BlockPos pos, double anchorLift, double panelWorldHeight)`

```java
/**
 * Adjusts the anchor lift (Y offset within the block) to prevent the panel
 * from clipping into the collision shape of the block above.
 *
 * <p>Tests whether the panel's top edge (anchorLift + panelWorldHeight)
 * would intersect the collision AABB of the block at pos.above(). If so,
 * lowers the anchor by the overlap depth.
 *
 * @param level      the client level (for neighbor block state queries)
 * @param pos        the block position the panel is anchored to
 * @param anchorLift Y offset of the panel anchor within the block (0.0 = bottom, 1.0 = top)
 * @param panelWorldHeight panel height in blocks, from {@link #panelWorldHeight(int)}
 * @return adjusted lift value, always <= anchorLift. Returns anchorLift unchanged if no clip.
 */
public static double antiClipLift(Level level, BlockPos pos,
        double anchorLift, double panelWorldHeight)
```

**Inputs:**
| Parameter | Type | Source |
|-----------|------|--------|
| `level` | `Level` | `Minecraft.getInstance().level` |
| `pos` | `BlockPos` | Target block position |
| `anchorLift` | `double` | `BODY_HEIGHT` (0.75) for UP face, `MID_BODY` (0.375) for side face |
| `panelWorldHeight` | `double` | From `panelWorldHeight(rowCount)` |

**Outputs:** `double` — adjusted lift, where `adjustedLift <= anchorLift`.

**Algorithm:**
```
BlockPos above = pos.above();
BlockState aboveState = level.getBlockState(above);
VoxelShape shape = aboveState.getCollisionShape(level, above);
if (shape.isEmpty()) return anchorLift;  // fast-path: air or non-solid

AABB bounds = shape.bounds();  // relative to the above block (0,0,0 to 1,1,1)
double neighborFloorY = 1.0 + bounds.minY;  // in block-local Y (1.0 = bottom of block above)
double panelTopY = anchorLift + panelWorldHeight;

if (panelTopY <= neighborFloorY) return anchorLift;  // no overlap

double overlap = panelTopY - neighborFloorY;
double adjusted = anchorLift - overlap;

// Clamp: don't push below block origin. Worst case, the panel sits at Y=0 of the block.
return Math.max(0.0, adjusted);
```

**Errors:** If `level.getBlockState()` returns air for an unloaded chunk, `getCollisionShape()` returns empty — no adjustment, safe fallback.

**Invariants:**
- Output is always `<= anchorLift` (never pushes panel upward).
- Output is always `>= 0.0` (never pushes below block origin).
- Deterministic: same level state + inputs = same output.
- No allocations beyond the `BlockPos.above()` and the `AABB bounds()` getter (both lightweight).

## Contract: Row Count Computation (CanisterHudRenderer-internal)

### `CanisterHudRenderer.computeRowCount(SlotData data)`

```java
/**
 * Computes the total row count for a slot's HUD panel.
 * Extracted from renderContent() to enable pre-render height calculation.
 *
 * @param data slot data (contents, label, matrices, partners)
 * @return total row count (header rows + goo type rows)
 */
private static int computeRowCount(SlotData data)
```

**Logic:** Mirrors the existing computation at lines 315-318 of `CanisterHudRenderer`:
```
boolean hasLabel = data.label != null && !data.label.isEmpty();
boolean hasUpgrade = data.matrices > 0;
boolean bothPartners = data.topPartner != null && data.bottomPartner != null;
int gasketRows = bothPartners ? 2 : ((hasFrom ? 1 : 0) + (hasTo ? 1 : 0));
int headerRows = (hasLabel ? 1 : 0) + gasketRows + (hasUpgrade ? 1 : 0);
return headerRows + data.contents.typeCount();
```

Note: `hasFrom`/`hasTo` in the full renderer also depend on `TunerAwaitState` and `formatPartnerRow` returning non-null. For row count purposes, this is approximated by checking `data.topPartner != null` and `data.bottomPartner != null` directly — the await state adds at most one row, and a slight height overestimate (pulling back slightly more than necessary) is visually harmless. If exact parity is needed, the partner-row-null check can be replicated, but this is a dev-agent decision.
