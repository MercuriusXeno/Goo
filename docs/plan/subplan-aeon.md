---
id: subplan-aeon
title: "Aeon -- Barrier Field"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Radius-fill of barrier blocks with shared break budget. Explosion-immune, easy to hand-break.
---

# Aeon -- Barrier Field

## Design Source

DESIGN-TYPES: "Creates a barrier on every block in a radius based on the charge. The radius is 5/7/9/11/13 (max of 5 chain) and the number of breaks the barriers collectively withstand is equal to the radius. When a barrier is hit, it becomes visible and pulses temporarily. It breaks like a normal block otherwise, it has no correct tool, drops nothing, and has an unbreakable resistance to explosions, but is otherwise easy to break (is this possible?). It is easily broken by hand. Its main purpose is protection from creepers and endermen."

## Pattern

**Unique block (field fill).** Unlike other effects that place a single node block, aeon fills a spherical shell with individual barrier blocks, all linked to a central controller that tracks the shared break budget.

## Parameters

| Stacks | Radius | Break budget |
|---|---|---|
| 1 | 5 | 5 |
| 2 | 7 | 7 |
| 3 | 9 | 9 |
| 4 | 11 | 11 |
| 5 | 13 | 13 |

Break budget = radius. Every barrier broken anywhere in the field decrements the shared counter. At 0, all remaining barriers shatter.

## Architecture

### New: `block/AeonBarrierBlock.java`

Custom block with:
- `explosionResistance(3600000)` -- immune to explosions.
- `destroySpeed(0.5f)` -- easy to break by hand.
- No correct tool (`requiresCorrectToolForDrops` = false, no loot table).
- `noOcclusion()` -- translucent/see-through when visible.
- Blockstate property `VISIBLE` (boolean) -- defaults to false (invisible). Set to true temporarily when hit.

### New: `block/AeonBarrierBlockEntity.java`

Each barrier BE holds a reference to the controller position (`controllerPos`). On break, notifies the controller to decrement the budget.

### New: `block/AeonControllerBlockEntity.java`

The central node (placed at the blob impact point). Tracks:
- `budget` (int) -- shared break counter.
- `radius` (int) -- for cleanup on budget depletion.
- `stacks` (int) -- for stacking additional blobs.

BER: Slime-core orb, aeon-tinted, breathes, aim-highlight. The controller is the visible node; individual barriers are invisible until hit.

When budget hits 0: iterate radius, remove all `AeonBarrierBlock` instances, remove self.

### Hit pulse

When an `AeonBarrierBlock` is hit (not broken, just attacked): set `VISIBLE = true`, schedule a 20-tick revert to `VISIBLE = false`. This gives the "pulse visible when struck" behavior.

### Placement

On blob impact:
1. Place or stack the controller at the hit position.
2. Fill air blocks in a spherical shell (surface only? or full volume?) with `AeonBarrierBlock`.
3. Each barrier BE records the controller position.

Design says "every block in a radius" -- this is a full volume fill of air blocks, not just a shell.

### Stacking

Additional blobs increase radius and budget. On stack: remove old barriers outside old radius (if radius shrank -- it doesn't, it only grows), place new barriers in the expanded ring, update budget.

## Open Questions

- **Volume or shell?** "Every block in a radius" suggests full volume. But filling a r=13 sphere is ~9,200 blocks. Shell (surface only) is more practical (~2,100). Needs playtesting.
- **Enderman interaction?** Design says "protection from endermen." Endermen can't pick up custom blocks by default, so this should work automatically.

## Testable Seams

- Radius formula `3 + 2 * stacks` in `EffectMath.computeAeonRadius`.
- Budget = radius (trivial).
- Controller/barrier coordination is framework-coupled.

## Verification

- Throw 1 aeon blob -> r=5 barrier sphere appears (invisible). Walk into it -- blocked. Break one by hand -> budget decrements. After 5 breaks, all barriers shatter.
- Creeper explosion -> barriers survive. TNT -> barriers survive. Hand-break -> easy.
- Hit a barrier without breaking -> it pulses visible for 1 second.
