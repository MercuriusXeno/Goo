---
id: DESIGN-RENDERING-SHADERS
title: Shader technique decision framework and planned implementations
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
---

# Shader Uses for Goo

Parent spec: `DESIGN-RENDERING.md`

## Decision Framework

| Effect | Shader Needed? | Complexity | Notes |
|--------|---------------|------------|-------|
| Emissive/fullbright | No | Trivial | Light UVs to 240,240 or no-lightmap RenderType |
| Additive glow | Barely | Low | Custom RenderType with additive blend |
| Color pulse/shift | Yes | Low | `GameTime` uniform + sin/cos in fragment |
| Animated UV scrolling | Maybe | Low | `TextureTransform` may suffice |
| Soft particles | Yes | Medium | Depth buffer copy + reconstruction |
| Dissolve/melt transition | Yes | Medium | Noise texture + threshold + edge glow |
| Fluid surface undulation | Yes | Medium | Vertex displacement (BERs only) |
| Metallic reflections | Yes | Medium | Environment map sampling |
| Heat haze / distortion | Yes | High | Post-processing pass required |
| Blur / bloom | Yes | High | Multi-pass post-processing |

## Planned Goo Shader Uses

### Easy Wins
- **Glowing thrown blobs:** additive blend particle RenderType. Type-colored trails.
- **Emissive goo fluid:** no-lightmap RenderType. Goo glows in the dark.
- **Color-pulsing crucible:** time-based brightness oscillation on BER when extracting.

### Medium Effort
- **Soft goo particles:** depth-buffer fade (EmbersRekindled technique) for smooth surface blending.
- **Crucible dissolve:** noise threshold + edge glow matching goo type color. Visualizes PMI pipeline.
- **Fluid surface animation:** vertex displacement for vat/crucible BERs.

### Deferred (high complexity / compatibility risk)
- **Heat haze:** post-processing, shader mod compatibility concerns.
- **Bloom:** additive blend achieves 80% of this at 10% effort.

## References
- [NeoForge 1.21.5 primer](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md) (RenderPipeline)
- [NeoForge 1.21.11 primer](https://github.com/neoforged/.github/blob/main/primers/1.21.11/index.md) (RenderSetup, GpuSampler)
- [EmbersRekindled](https://github.com/RCXcrafter/EmbersRekindled) (depth-buffer soft particles, additive glow, luminance-to-alpha)
