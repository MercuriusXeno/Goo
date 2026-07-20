---
id: DESIGN-RENDERING
title: BER, particle, HUD, and fluid texture rendering specifications
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
---

# Rendering & Visual Specifications

Implementation-level rendering details. Design intent: `DESIGN.md` and `DESIGN-TYPES.md`.

## Crucible BER

`CrucibleBlockEntityRenderer` renders four elements:

**Fuel platform**: `crucible_fuel_platform` texture. Platform Y is server-side state; BER lerps for sub-tick smoothness only.

**Blaze rod**: `crucible_blaze_rod` texture. Sits on platform, shrinks as fuel depletes.

**Chain crosses**: 4 quarter-scale crosses using `iron_chain` texture, tiled. 0.75px wide, 4px tile height, edges flush with platform corners.

**Liquid surface**: dominant goo type's animated fluid sprite via `entityTranslucent`. Crossfade: two overlapping quads with complementary alpha. Dominant type: 20-tick debounce hysteresis with ordinal tie-breaking. Fill: logarithmic curve (`computeLogFill`).

Render types: `RenderTypes.entitySolid()` / `entityCutoutNoCull()` / `entityTranslucent()`.

## Crucible Particles

`CrucibleParticleHelper` spawns:
- **Sparks**: 8-12 `GOO_SPARK` at rod contact
- **Embers**: 5%/tick, `GOO_SPARK`
- **Bubbles**: 0-1/tick, color-tinted via `ColorParticleOption`
- **Melt smoke**: 3-5 on item absorb
- **Sizzle sounds**: on item absorb

`GooSparkParticle`: gravity (0.6), friction (0.96), block collision, quadratic size fade-out.

Bubble ring buffer (16 positions, `MIN_SPACING_SQ = 0.0156`). Idle boiling continues with fuel + goo (no fuel consumption).

`GooBubbleParticle` 8-frame lifecycle: EXPAND (0-9, 40-100% scaling), LINGER (10-29), DANCE (30-49, gentle drift), POP (50-53, frames 4-7). Early-pops when crucible empties.

## Crucible HUD

`CrucibleHudRenderer`: in-world billboard when crosshair targets basin interior. Shows type icons + reservoir/total volumes + fuel.

Rim positioning: `selectBestRimPoint()` with 8 candidates + look-direction bias. Background: nine-slice `effect_background.png` (3px border). Z-depth: background Z=0, content Z=1 via `RenderTypes.text()`. Animation: exponential smoothing (tau 0.1s). Renders at `AfterEntities` stage.

## Crucible Blockstate

Multipart: base + rotated `crucible_plate.json` overlays for N/S/E/W lever plates. Properties: `POWERED`, `PLATE_NORTH/SOUTH/EAST/WEST`. `getBlockSupportShape()` = `Shapes.block()`.

## Crucible VoxelShape

5-part basin (4 walls Y=10-16 + recessed floor Y=9-10), 4 feet (Y=0-1), 4 legs (Y=1-9). Interlocking wall corners for accurate interior face hit detection (rune ink targeting).

## Depleted Blaze Rod Rendering

Unstackable, `FUEL_REMAINING` data component (int, ticks). Orange durability bar. 8-stage model via `range_dispatch` with `goo:fuel_remaining`.

## Blob Slot Overlay

`BlobVolumeDecorator`: type icon (top-right, `<type>_slot_icon.png`) + compact volume label (bottom-right, half-scale, 3 sig digits, no "B" suffix).

## Fluid Textures

Offline CA generator (`FluidTextureGenerator.java`). Ping-pong frame order (0-31, 30-1). No runtime CA.

### CA Algorithm

Three heat layers: **soupHeat** (visible surface, neighborhood-averaged + pot), **potHeat** (accumulates from flame, feeds soup), **flameHeat** (random ignition, decays).

### Per-Type Parameters

| Parameter | Controls | Range |
|-----------|----------|-------|
| `viscosity` | Neighborhood divisor (thicker = slower) | 0.6 (typhoon) - 2.5 (aeon) |
| `decayRate` | Flame decay speed | 0.015 - 0.12 |
| `ignitionChance` | Random heat injection frequency | 0.002 - 0.025 |
| `ignitionStrength` | Heat burst magnitude | 0.6 - 2.0 |
| `potHeatRate` | Flame-to-pot transfer | 0.003 - 0.05 |
| `potInfluence` | Pot-to-soup transfer | 0.3 - 0.95 |
| `neighborhoodReach` | Convolution radius | 1 (3x3) - 2 (5x5) |
| `frametime` | Ticks per frame | 1 - 5 |

### Color System

Gradient with positioned stops. Heat (0.0-1.0 after normalization) interpolated across stops. Dominant color bias via clustered stops. Two-pass normalization: record min/max across all frames, remap to full range.

### Output

- Fluid: `textures/fluid/<type>_fluid.png` - 16x512 strip (32 frames), mcmeta with interpolate
- Blob base: `textures/item/<type>_blob_base.png` - 16x16 CA snapshot masked to slimeball silhouette
- Item model: `layer0` = generated base, `layer1` = hand-drawn overlay

Tool: `src/main/java/com/mercuriusxeno/goo/tools/FluidTextureGenerator.java`

### Rendering Pipeline

BERs consume fluid textures (not baked block models):
- **Canister BER**: shared `renderCanister()` method, called by hub/plexer BERs with transforms
- **Crucible BER**: fluid quad at variable Y based on fill ratio, tinted by type
- **Vat BER**: large block tank, BER fluid rendering

## Hub Slot Geometry

8 radial canister slots. Detailed Blockbench coordinates in `HubBlockEntityRenderer` Javadoc. Coordinates are pixel units (1/16 block). Gaskets use `#0` (choral_gasket), body uses `#1` (canister_side). BER renders only occupied slots.

## Vat Model & Connected Textures

14x16x14 cuboid: copper cap (top) + copper base (bottom) + glass body.

**Textures**:
- `vat_cap.png` / `vat_base.png`: default top/bottom faces
- `vat_cap_gasket.png` / `vat_base_gasket.png`: gasket-attached variants
- `vat_body.png`: 14x36 pixel graduated cylinder markings for connected UV mapping

**Gasket**: boolean blockstate property `HAS_GASKET`. Player right-clicks vat with gasket item to attach. Model variants swap cap/base textures to `_gasket` suffix.

**Vertical Stacking**: vats that stack vertically merge into a single contiguous unit. Internal faces are culled (bottom vat's cap + top vat's base hidden). Body texture spans the full column via connected UV mapping across the 14x36 graduated cylinder strip.

## Crucible Gasket

`crucible_bottom_gasket.png`: bottom face variant when gasket is attached. Gasket modification region: 4,4-9,9 (6x6 square: darkened surround + gasket texture).

## Crucible Runic Matrix Overlays

Single shared overlay texture, four jigsaw regions placed on the crucible_bottom top face (interior, facing upward). Each matrix level (1-4) adds one region:

| Matrix | Overlay region | Size | Destination on crucible_bottom | Size |
|--------|----------------|------|--------------------------------|------|
| 1 | 14,4 - 18,13 | 5x10 | 8,2 - 12,11 | 5x10 |
| 2 | 19,4 - 28,8 | 10x5 | 2,8 - 11,12 | 10x5 |
| 3 | 19,9 - 28,13 | 10x5 | 2,1 - 11,5 | 10x5 |
| 4 | 14,14 - 18,23 | 5x10 | 1,2 - 5,11 | 5x10 |

## Shader Techniques

See `DESIGN-RENDERING-SHADERS.md`.
