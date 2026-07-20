---
id: story-017
title: HUD anti-clip — ray-tested panel repositioning
type: user-story
status: draft
author: story-skill
consumers: [pilot]
created: 2026-03-22T15:00:00
updated: 2026-03-22T15:00:00
---

# HUD anti-clip — ray-tested panel repositioning

## Problem
The in-world canister/hub HUD panel can clip through adjacent block geometry. The current fix (bug-002) redirects to a side face when a solid block is above, but partial clipping still occurs because the panel has physical extent — its top and bottom edges can intersect neighboring voxels even when the anchor point is in open air. There is no general-purpose mechanism to detect or prevent this.

## Acceptance Criteria
- HUD panel renders in a position where neither its top nor bottom edge intersects solid block geometry, for all rotation modes (billboard, flat, face-anchored)
- When the default position would clip, the panel moves closer to the player along the view axis until it clears
- Deterministic: same block layout + player position = same panel position (no jitter, no per-frame searching)
- No visible rendering regression for the common case (canister in open air, no adjacent blocks)
- Frame budget impact is negligible (sub-0.1ms per HUD panel on commodity hardware)

## Scope
**In scope:**
- Ray/voxel intersection test for the panel's top and bottom edges against collision shapes
- Pull-toward-player repositioning when intersection is detected
- Applies to CanisterHudRenderer and any shared InWorldHud positioning
- Evaluate temporary VoxelShape caching for repeated queries against the same block neighborhood (architect should assess whether this is net-positive or premature)

**Out of scope:**
- Changing which face the HUD anchors to (bug-002 already handles face selection)
- HUD content layout or sizing changes
- Hub-specific targeting logic (hub slots don't have block-above issues currently)
- Occlusion culling (hiding the HUD entirely when not visible)

## Notes
- The pilot suggests testing top/bottom panel edges as rays for voxel intersection. Architect should evaluate whether `VoxelShape.clip()` or `BlockState.getCollisionShape().bounds()` is the right primitive.
- Shape caching: querying `getCollisionShape()` per-frame for 2-6 neighbor blocks may or may not warrant a cache. Architect should benchmark before committing to a cache strategy — it may be unnecessary overhead if the shape lookups are already fast (they're cached by Minecraft internally per-blockstate).
- The current `hasFullBlockAbove()` check (simple boolean) could be subsumed by this general mechanism, but consider keeping it as a fast-path to avoid ray tests when not needed.
- Related: `InWorldHud.applyFaceRotation()`, `InWorldHud.applyFlatRotation()`, `InWorldHud.applyBillboardRotation()` — the repositioning must happen before rotation is applied.
