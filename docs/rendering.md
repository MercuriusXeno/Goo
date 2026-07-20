---
id: rendering
title: Client-Side Rendering
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/client/GooClientSetup.java, src/main/java/com/mercuriusxeno/goo/client/CrucibleBlockEntityRenderer.java, src/main/java/com/mercuriusxeno/goo/client/CanisterBlockEntityRenderer.java, src/main/java/com/mercuriusxeno/goo/client/HubBlockEntityRenderer.java, src/main/java/com/mercuriusxeno/goo/client/VatBlockEntityRenderer.java, src/main/java/com/mercuriusxeno/goo/client/particle/, src/main/java/com/mercuriusxeno/goo/client/InWorldHud.java, src/main/java/com/mercuriusxeno/goo/tools/FluidTextureGenerator.java]
created: 2026-03-22
updated: 2026-03-22
---

# Client-Side Rendering

## Entry Point

`GooClientSetup` (client-only `@EventBusSubscriber`) registers everything:
- `registerEntityRenderers` -- BERs for crucible, hub, canister, vat; `ThrownItemRenderer` for blob projectile.
- `registerItemDecorations` -- `BlobVolumeDecorator` on all blob/omniblob items.
- `registerRangeSelectProperties` -- `BlobSizeProperty` and `FuelRemainingProperty` for item model range_dispatch.
- `registerParticleProviders` -- `GooBubbleParticle.Provider` and `GooSparkParticle.Provider`.
- `registerStandaloneModels` -- `CanisterBodyModels` for matrix variant meshes.
- `registerClientExtensions` -- fluid rendering extensions (still/flowing textures per goo type).
- `onClientDisconnect` -- clears `GOO_VALUES` and `TunerAwaitState`.

Static initializer sets `ISidedProxy.INSTANCE[0] = new ClientProxy()`.

## Block Entity Renderers (BERs)

### CrucibleBlockEntityRenderer

Renders internal mechanisms: fuel platform, 4 corner chains, blaze rod, and liquid surface.

**Render state** (`CrucibleRenderState`): `platformY`, `prevPlatformY`, `partialTick`, `hasFuelRod`, `fuelFraction`, `poolVolume`, `reservoirVolume`, `dominantType`, `outgoingType`, `crossfadeAlpha`.

**Platform**: 4x1x4 pixel box at interpolated Y. Custom texture `crucible_fuel_platform.png`. UV rects for 6 faces.

**Chains**: 4 corner crosses (two perpendicular quads each) between platform and basin. Uses vanilla `iron_chain.png`. Tiled vertically with platform-anchored UVs; topmost tile clips from top.

**Blaze rod**: 2x2 pixel cuboid on platform. Height = `fuelFraction * ROD_FULL_HEIGHT`. Side UVs recede from top as rod shortens.

**Liquid surface**: Logarithmic fill curve (`computeLogFill`). Front-loaded: small volumes fill quickly. During crossfade between dominant types, renders two overlapping quads with complementary alpha. Uses `GooRenderUtil.lookupFluidSprite` for goo type textures.

### CanisterBlockEntityRenderer

Renders per-slot canister bodies. Uses standalone baked models from `CanisterBodyModels` (per-matrix variant). Render state via `CanisterRenderState`. Slot-aware positioning from `CanisterGeometry`.

### HubBlockEntityRenderer

Renders hub frame and radial canister slots. Render state via `HubRenderState`.

### VatBlockEntityRenderer

Renders vat contents as fluid level. Render state via `VatRenderState`. `VatStackAggregator` aggregates stacked vats for visual continuity.

## HUD Renderers

- **`CrucibleHudRenderer`** -- in-world billboard showing melt progress, reservoir contents.
- **`CanisterHudRenderer`** -- shows canister slot contents and gasket status.
- **`TunerHudRenderer`** -- shows tuner link state and partner info.
- **`VatHudRenderer`** -- shows vat capacity and fill level.
- **`InWorldHud`** -- orchestrates HUD rendering, determines which renderer to activate based on player look target.

## Overlays and Decorators

- **`CanisterPlacementOverlay`** -- shows placement preview when holding a canister near a canister block.
- **`GasketPartnerDisplay`** -- renders partner connection indicators.
- **`SlotOutlineRenderer`** -- highlights targeted canister/hub slots.
- **`BlobVolumeDecorator`** -- item decoration showing volume text on blob/omniblob inventory icons.
- **`GooTooltipHandler`** -- renders icon+volume tooltip lines for all goo containers.

## Particles

- **`GooBubbleParticle`** -- colored bubbles rising from crucible basin. Registered sprite set: `GOO_BUBBLE`.
- **`GooSparkParticle`** -- ember particles from crucible rod contact. Registered sprite set: `GOO_SPARK`.

Server-side spawning via `CrucibleParticleHelper`: `spawnEmbers`, `spawnGooBubbles`, `spawnIgnitionSparks`.

## Render Utilities

- **`GooRenderUtil`** -- shared vertex/face emission helpers. `faceX`/`faceY`/`faceZ` for box faces, `liquidSurface` for fluid quads, `vertex` for raw vertex emission, `lookupFluidSprite` for texture atlas lookup.
- **`UvRect`** -- record for UV rectangle bounds.

## Texture Generation

`FluidTextureGenerator` (in `tools/`) -- offline CA-based cellular automaton that generates fluid textures. Not used at runtime.
