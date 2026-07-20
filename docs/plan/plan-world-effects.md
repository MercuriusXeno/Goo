---
id: plan-world-effects
title: "M15: World Effect Polish — 15 effect designs"
type: plan
status: approved
created: 2026-04-02
updated: 2026-04-02
purpose: All 15 world effects exist as placeholders in `effect/WorldEffects.java`. Each is a one-shot static method triggered ...
---

# M15: World Effect Polish — Unified Plan

## Context

All 15 world effects exist as placeholders in `effect/WorldEffects.java`. Each is a one-shot static method triggered when a thrown goo blob hits a block (`BlobThrowHandler` → `WorldEffects.apply(level, pos, gooType)`). The design specs (DESIGN-TYPES.md) call for most to be **persistent** — entities or blocks that remain in the world with ongoing behavior, stacking when additional blobs land at the same position.

**Design authority:** DESIGN-TYPES.md is canonical. The old goal-003 conflicted on VITAL (said "Living Blob" — resolved: life pulsing node) and HEX (said "mob spawning" — resolved: exp absorber). goal-003 deleted; this plan replaces it.

---

## Shared Infrastructure

### A. `GooWorldEffect` base entity
Extends `Entity` (not `LivingEntity`). Shared by all persistent effect entities (~10 of 15).

- `BlockPos anchorPos` — block the effect is attached to
- `int stackCount` / `int maxStacks` — stacking protocol
- `GooType gooType`
- `tryStack()` — increment stack, return success/failure (capped at maxStacks)
- `saveAdditionalData` / `readAdditionalData` for persistence
- No rendering in v1 (invisible marker; particles added per-effect)

### B. `EffectStackFinder` utility
Discovers existing effect entities at a `BlockPos`. Called before creating a new entity — if one exists, stack instead.

```
static <T extends GooWorldEffect> Optional<T> findAt(Level, BlockPos, Class<T>)
```

### C. `PulsingNodeEffect` shared base
Extends `GooWorldEffect`. Shared by Leaf and Vital (identical stacking/depletion/pulse-interval mechanics, different pulse behavior).

- `int blobCount` — depletes by 1 per pulse, entity removed at 0
- Pulse interval: `320 / 2^(min(stackCount, 4) - 1)` ticks (16s → 8s → 4s → 2s)
- Additional blobs: increment blobCount + grant one free immediate pulse

### D. Expand `WorldEffects.apply` signature
Add `Direction targetFace` parameter. Thread from `BlobThrowPayload` through `BlobThrowHandler.PendingEffect`. Needed by Glow (face placement), Typhoon (orientation).

### E. `ChainMarkerEffect` shared base
Extends `GooWorldEffect`. Short-lived fuse entity (~10 ticks) for Blaze, Frost, Nether. Additional blobs within the fuse window increment stack. On fuse expiry, executes the one-shot effect at the stacked power level, then self-removes.

---

## Per-Effect Design Notes

### 1. METAL — Urchin (persistent damage zone)
| | |
|---|---|
| **Spec** | Metal spike. Lasts until destroyed. Multi-hit. |
| **Current** | AreaEffectCloud (10s) + instant AoE damage |
| **Target** | `MetalUrchinEffect` entity. Tick: damage non-sneaking entities in 1.5-block radius every 10 ticks (3.0f magic). Permanent until killed. |
| **Stacking** | None specified |
| **New class** | `effect/MetalUrchinEffect.java` |
| **Assets** | Entity model (iron spike/urchin cluster, ~8×8×8 px), entity texture (`metal_urchin.png`), CRIT-style particle or custom iron shard particle |
| **Testable seams** | Damage rate-limiting logic |
| **Open question** | How is it "destroyed"? Player hits it? Takes damage? Finite HP? |

### 2. CRYSTAL — Shards (damage-budget zone)
| | |
|---|---|
| **Spec** | Suspended glass shards shred anything moving through without sneaking. Fades over time AND damage dealt. |
| **Current** | Shrinking AreaEffectCloud |
| **Target** | `CrystalShardsEffect` entity. Dual depletion: time budget (~30s) AND damage budget (~20 HP dealt). Sneaking entities immune. Whichever runs out first kills the entity. |
| **Stacking** | None specified |
| **New class** | `effect/CrystalShardsEffect.java` |
| **Assets** | Entity model (suspended glass shard cluster, translucent), entity texture (`crystal_shards.png`), particle (glass shard fragments, could reuse DAMAGE_INDICATOR or custom) |
| **Testable seams** | `isDepleted(remainingBudget, remainingTicks)`, sneaking check |

### 3. LEAF — Growth Pulsing Node
| | |
|---|---|
| **Spec** | Growth pulsing node pulsing every 16s. Stacking halves delay (up to 3x = 2s min). Depletes 1 blob/pulse. 5×5 area; 9×9 if underwater. |
| **Current** | One-shot random ticks in 5×5 |
| **Target** | `LeafGrowthNodeEffect extends PulsingNodeEffect`. Pulse: apply 3 random ticks to growable blocks in radius. Water detection for 9×9 extension. |
| **Stacking** | Via PulsingNodeEffect (blobCount + interval halving) |
| **New class** | `effect/LeafGrowthNodeEffect.java` |
| **Assets** | Entity model (leafy node/sprout, small ground-level), entity texture (`leaf_growth_node.png`), pulse particle (reuse HAPPY_VILLAGER or custom green burst) |
| **Testable seams** | `computePulseInterval(stackCount)`, `computeGrowthRadius(submerged)` |

### 4. VITAL — Life Pulsing Node
| | |
|---|---|
| **Spec** | Life pulsing node, same stacking as Leaf. Pulses cause animals in 5×5 to have mating chance = 100/h^0.8. |
| **Current** | Spawns vanilla Slime |
| **Target** | `VitalPulsingNodeEffect extends PulsingNodeEffect`. Pulse: find animals in 5×5, roll mating chance per animal. |
| **Stacking** | Via PulsingNodeEffect |
| **New class** | `effect/VitalPulsingNodeEffect.java` |
| **Assets** | Entity model (pulsing pink/red life orb, ground-level), entity texture (`vital_pulse_node.png`), pulse particle (heart particles or custom pink burst) |
| **Testable seams** | `computeMatingChance(maxHealth)` = `100.0 / pow(h, 0.8)` |

### 5. SHROOM — Spore Area
| | |
|---|---|
| **Spec** | Spawns mushrooms when mobs die nearby. Lasts until it disperses spores. |
| **Current** | One-shot mycelium/mushroom placement |
| **Target** | `ShroomSporeEffect` entity. Listens for `LivingDeathEvent` within radius. On mob death: place mushroom at death pos, decrement spore budget. At 0 spores, self-remove. |
| **Stacking** | Additional blobs add spore budget |
| **New class** | `effect/ShroomSporeEffect.java` |
| **Assets** | Entity model (fungal spore cloud/puffball, translucent), entity texture (`shroom_spore.png`), ambient particle (reuse SPORE_BLOSSOM_AIR or custom spore drift) |
| **Testable seams** | `shouldSpawnShroom(deathPos, anchorPos, radius)`, shroom type selection |
| **Integration** | Needs NeoForge event bus listener for `LivingDeathEvent` |

### 6. ROCK — Implosion
| | |
|---|---|
| **Spec** | Breaks pure rock in 3×3×n² area after delay. Depth stacks exponentially, max 5 (25 deep). |
| **Current** | 3×3×3 instant break |
| **Target** | `RockImplosionEffect` entity. 20-tick delay, then break `isRockType` blocks in 3×3×depth column downward. Depth = stackCount². |
| **Stacking** | Max 5. Depth: 1, 4, 9, 16, 25 |
| **New class** | `effect/RockImplosionEffect.java` |
| **Assets** | No entity model (invisible marker). Particle: rock debris/dust cloud on detonation (reuse vanilla block break particles or custom). Sound: rumble/crunch (reuse GENERIC_EXPLODE or custom). |
| **Testable seams** | `computeDepth(stackCount)` = n². `isRockType(BlockState)` already exists. |

### 7. BLAZE — Explosion Chain
| | |
|---|---|
| **Spec** | Explosion in 3/5/7/9 m³ area, max 4 chain. |
| **Current** | Single 3.0f explosion |
| **Target** | `BlazeChainMarker extends ChainMarkerEffect`. 10-tick fuse. On expiry: explode with radius = `1 + 2 * stackCount`. |
| **Stacking** | Max 4. Radius: 3, 5, 7, 9 |
| **New class** | `effect/BlazeChainMarker.java` |
| **Assets** | No entity model (invisible fuse marker). Vanilla explosion handles all visuals/sounds. Optional: pre-detonation glow particle during fuse window. |
| **Testable seams** | `computeExplosionRadius(stackCount)` |

### 8. FROST — Freeze Chain
| | |
|---|---|
| **Spec** | Freeze water→ice, lava→obsidian. Chain max 3, radius 5/7/9. Extinguish fires. |
| **Current** | Single r=5 freeze |
| **Target** | `FrostChainMarker extends ChainMarkerEffect`. 10-tick fuse. On expiry: freeze in radius = `3 + 2 * stackCount`. |
| **Stacking** | Max 3. Radius: 5, 7, 9 |
| **New class** | `effect/FrostChainMarker.java` |
| **Assets** | No entity model (invisible fuse marker). Particle: snowflake burst on detonation (reuse SNOWFLAKE). Sound: ice crack (reuse GLASS_BREAK). Optional: frost creep particle during fuse. |
| **Testable seams** | `computeFreezeRadius(stackCount)`, `convertBlock(BlockState)` |

### 9. TYPHOON — Jet Stream
| | |
|---|---|
| **Spec** | Persistent jet stream, orientable sideways. Stacks up to 5. Propulsion ~2^n meters. |
| **Current** | One-time upward push |
| **Target** | `TyphoonJetEffect` entity. Orientation from `targetFace`. Tick: push entities along orientation. Force = 2^stackCount. |
| **Stacking** | Max 5. Propulsion: 2, 4, 8, 16, 32 |
| **New class** | `effect/TyphoonJetEffect.java` |
| **Assets** | Entity model (wind column/vortex, elongated along orientation axis), entity texture (`typhoon_jet.png`, translucent streaks), particle (reuse CLOUD or custom wind swirl), sound (wind rush loop) |
| **Testable seams** | `computePropulsion(stackCount)`, `computePushVector(direction, force)` |
| **Depends on** | Infrastructure D (targetFace parameter) |

### 10. GLOW — Luminescent Crystal
| | |
|---|---|
| **Spec** | Crystallizes into light source on the face hit. Lasts until destroyed. |
| **Current** | Places vanilla glowstone above |
| **Target** | Custom `GlowCrystalBlock` — directional (attaches to hit face, like amethyst cluster), light level 15, breakable. |
| **Stacking** | None |
| **New classes** | `block/GlowCrystalBlock.java` |
| **Registration** | GooBlocks, GooItems (block item for pickup/re-placement) |
| **Assets** | Block model (small crystal cluster, directional — 6 rotations like amethyst cluster), block texture (`glow_crystal.png`, emissive/bright), block item model, loot table |
| **Depends on** | Infrastructure D (targetFace for face placement) |
| **Testable seams** | `canPlaceOnFace(level, pos, direction)` |

### 11. HEX — Exp Absorber
| | |
|---|---|
| **Spec** | Absorbs nearby exp. Right-click to reclaim exp. Lasts until destroyed. |
| **Current** | Empty AreaEffectCloud |
| **Target** | `HexAbsorberEffect` entity. Tick: vacuum `ExperienceOrb` entities within radius, accumulate stored XP. Player right-click interaction: award all stored XP, self-remove. |
| **Stacking** | Additional blobs increase absorption radius? Or just add to budget? (TBD) |
| **New class** | `effect/HexAbsorberEffect.java` |
| **Assets** | Entity model (dark swirling orb/vortex), entity texture (`hex_absorber.png`, dark purple/black), particle (XP orb vacuum trail — green wisps being pulled inward), ambient particle (WITCH or custom dark swirl) |
| **Testable seams** | XP accumulation math |

### 12. PULSE — Repeating Signal
| | |
|---|---|
| **Spec** | Pulse to adjacent blocks. 16-tick default delay. Chain up to 3 for 4-tick delay. Lasts until destroyed. |
| **Current** | One-time neighbor update |
| **Target** | `PulseSignalEffect` entity. Tick: every N ticks, fire `neighborChanged` on all 6 adjacent blocks. Interval decreases with stacking. |
| **Stacking** | Max 3. Delay: 16 → ~10 → 4 ticks (exact curve TBD) |
| **New class** | `effect/PulseSignalEffect.java` |
| **Assets** | Entity model (redstone emitter node, small cube/prism), entity texture (`pulse_signal.png`, redstone-red with glow), particle (redstone dust burst on each pulse tick), sound (NOTE_BLOCK_BELL or custom click per pulse) |
| **Testable seams** | `computePulseDelay(stackCount)` |

### 13. NETHER — Conversion
| | |
|---|---|
| **Spec** | Converts blocks to goo blobs (item form). Converts living goo to normal blobs. Chain max 4, radius 3/5/7/9. |
| **Current** | Destroys blocks, drops vanilla items |
| **Target** | `NetherChainMarker extends ChainMarkerEffect`. 10-tick fuse. On expiry: for each block in radius, look up goo value → spawn corresponding goo blob items. Also converts any `GooWorldEffect` entities in radius to blob items. |
| **Stacking** | Max 4. Radius: 3, 5, 7, 9 |
| **New class** | `effect/NetherChainMarker.java` |
| **Assets** | No entity model (invisible fuse marker). Particle: SOUL burst on detonation (already used). Optional: dark energy swirl during fuse window. |
| **Testable seams** | `computeNetherRadius(stackCount)`, `blockToGooBlobs(gooValue)` |
| **Integration** | Needs `GooValueRegistry` for block→goo mapping |

### 14. ENDER — Teleporter Node
| | |
|---|---|
| **Spec** | Stable teleporter node. Range = 16 × 2^n (n = blobs, max 4). Look + shift to warp. Lasts until destroyed. |
| **Current** | Places END_ROD |
| **Target** | Custom `EnderNodeBlock` + `EnderNodeBlockEntity`. Stores stack count (range tier). Player interaction: shift + look at node → scan for other ender nodes within range → teleport to nearest in look direction. |
| **Stacking** | Max 4. Range: 16, 32, 64, 128, 256 blocks |
| **New classes** | `block/EnderNodeBlock.java`, `block/EnderNodeBlockEntity.java` |
| **Registration** | GooBlocks, GooBlockEntities, GooItems |
| **Assets** | Block model (ender node — end rod variant or custom obelisk/pillar), block texture (`ender_node.png`, ender purple with particle shimmer), block item model, particle (ender pearl trail between nodes on warp), sound (ENDERMAN_TELEPORT on warp), loot table |
| **Testable seams** | `computeRange(stackCount)` = `16 * 2^n`, `findTargetNode(origin, lookDir, range, nodes)` |
| **Complexity** | Highest — needs node discovery, raycast matching, teleportation |

### 15. AEON — Barrier Infusion
| | |
|---|---|
| **Spec** | Barrier-infusion on a block resists tools and explosions, but not indestructible. |
| **Current** | Places obsidian |
| **Target** | Custom `AeonBarrierBlock` with very high `destroyTime` (~200) and `explosionResistance` (~6000). Mineable but extremely slow. |
| **Stacking** | None |
| **New class** | `block/AeonBarrierBlock.java` |
| **Registration** | GooBlocks |
| **Assets** | Block model (reinforced/infused block — golden/amber overlay on stone-like base), block texture (`aeon_barrier.png`), loot table. If infusion approach: block entity + dynamic texture overlay to mimic original block with aeon shimmer. |
| **Open question** | Does it replace the target block (infusion) or place a new block above? "Barrier-infusion on a block" implies replacement. Could store original blockstate in block entity for visual mimicry. |

---

## Asset Summary

| Effect | Entity model | Entity texture | Block model | Block texture | Custom particle | Sound |
|--------|:---:|:---:|:---:|:---:|:---:|:---:|
| Metal | Yes (spike) | `metal_urchin.png` | — | — | Optional | — |
| Crystal | Yes (shards) | `crystal_shards.png` | — | — | Optional | — |
| Leaf | Yes (sprout) | `leaf_growth_node.png` | — | — | Optional (reuse) | — |
| Vital | Yes (life orb) | `vital_pulse_node.png` | — | — | Optional (reuse) | — |
| Shroom | Yes (puffball) | `shroom_spore.png` | — | — | Optional (reuse) | — |
| Rock | — | — | — | — | Optional (reuse) | Reuse |
| Blaze | — | — | — | — | Optional | — |
| Frost | — | — | — | — | Reuse SNOWFLAKE | Reuse |
| Typhoon | Yes (vortex) | `typhoon_jet.png` | — | — | Reuse CLOUD | Wind loop |
| Glow | — | — | Yes (crystal, 6-dir) | `glow_crystal.png` | — | — |
| Hex | Yes (dark orb) | `hex_absorber.png` | — | — | XP vacuum trail | — |
| Pulse | Yes (prism) | `pulse_signal.png` | — | — | Redstone burst | Reuse |
| Nether | — | — | — | — | Reuse SOUL | — |
| Ender | — | — | Yes (obelisk) | `ender_node.png` | Warp trail | Reuse |
| Aeon | — | — | Yes (infused) | `aeon_barrier.png` | — | — |

**Totals:** 8 entity models/textures, 3 block models/textures, ~3 custom particles, ~1 custom sound

## Suggested Sequencing

| Phase | Effects | Why first |
|---|---|---|
| **1. Infra** | GooWorldEffect base, EffectStackFinder, ChainMarkerEffect, PulsingNodeEffect, targetFace threading | Foundation for everything |
| **2. Chain effects** | Blaze, Frost, Nether, Rock | Simplest — short-lived fuse entities, mostly math |
| **3. Pulse nodes** | Leaf, Vital | Share PulsingNodeEffect base, moderate complexity |
| **4. Simple persistent** | Metal, Crystal, Pulse | Permanent entities, straightforward tick logic |
| **5. Event-driven** | Shroom, Hex | Need event bus integration (death events, XP orbs) |
| **6. Directional** | Typhoon, Glow | Need targetFace, directional placement/orientation |
| **7. Complex** | Ender, Aeon | Custom blocks, highest complexity |

Each effect gets its own sub-plan before implementation.

---

## Files Overview

**New files (~19):**
- `effect/GooWorldEffect.java`, `effect/EffectStackFinder.java`, `effect/ChainMarkerEffect.java`, `effect/PulsingNodeEffect.java`
- Per-effect: 11 entity classes in `effect/`
- Per-block: `block/GlowCrystalBlock.java`, `block/EnderNodeBlock.java`, `block/EnderNodeBlockEntity.java`, `block/AeonBarrierBlock.java`

**Modified files:**
- `effect/WorldEffects.java` — rewrite to factory pattern (find-or-create)
- `network/BlobThrowHandler.java` — add targetFace to PendingEffect
- `registry/GooEntities.java` — register ~11 entity types
- `registry/GooBlocks.java` — register 3 blocks
- `registry/GooBlockEntities.java` — register EnderNode BE
- `registry/GooItems.java` — register block items
- `en_us.json` — entity/block display names

## Verification

Per-effect verification happens in each sub-plan. Overall:
- `./gradlew build` compiles
- `./gradlew gooTest` passes (pure-function seams for all math)
- In-game: throw each blob type at blocks, verify persistent behavior, test stacking by rapid throws
