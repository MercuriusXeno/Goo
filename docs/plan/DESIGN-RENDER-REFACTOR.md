# Render Pipeline Parameter Compression

## Problem

224 ParameterNumber violations, ~160 in rendering code. Vertex emission methods thread `pose, consumer, light` through every call and pass raw float coordinates/UVs as individual params. Methods hit 8-16 params. The code looks repetitive because it IS repetitive - 4 nearly identical vertex calls per quad, 4-6 nearly identical face calls per box.

## Types

### `RenderCtx`
```java
record RenderCtx(PoseStack.Pose pose, VertexConsumer c, int light) {}
```
Threading context created once per render call. Replaces 3 params on every method in the pipeline.

### `CuboidBounds`
```java
record CuboidBounds(float x0, float x1, float z0, float z1, float yBot, float yTop) {}
```
Already exists as `FluidBounds` - rename. Used for fluid surfaces, gaskets, stream segments, frost fields, any axis-aligned box. Lighter than AABB (floats, no clamping, no unused methods).

### `UvRect`
```java
record UvRect(float u0, float v0, float u1, float v1) {}
```
Already exists in `GooRenderUtil`. Replaces 4 params on every face/surface method.

## Refactored face emission

Current: 4 separate methods (`faceNorth`, `faceSouth`, `faceWest`, `faceEast`) with identical structure, different winding and normal. Called individually.

Target: single `emitFace(RenderCtx, CuboidBounds, UvRect, Direction)` that dispatches winding by direction. Horizontal faces via `emitTop`/`emitBottom` or `emitFace` with `Direction.UP`/`DOWN`.

Box emission becomes:
```java
static void emitBox(RenderCtx ctx, CuboidBounds box, UvRect uv) {
    for (Direction dir : Direction.values()) {
        emitFace(ctx, box, uv, dir);
    }
}
```

Side-only emission (fluid sides):
```java
static void emitSides(RenderCtx ctx, CuboidBounds box, UvRect uv) {
    for (Direction dir : Direction.Plane.HORIZONTAL) {
        emitFace(ctx, box, uv, dir);
    }
}
```

## Signature compression examples

### Before
```java
void renderFluidSides(PoseStack.Pose pose, VertexConsumer c,
        int light, FluidBounds b, TextureAtlasSprite sprite, float fill)
```
6 params, 3 are threading context.

### After
```java
void renderFluidSides(RenderCtx ctx, CuboidBounds box, UvRect uv)
```
3 params. Sprite -> UvRect conversion happens once at the caller.

### Before (lowest level)
```java
GooRenderUtil.vertex(pose, c, light, x, y, z, u, v, nx, ny, nz)
```
11 params.

### After
```java
ctx.vertex(x, y, z, u, v, nx, ny, nz)
// or with further compression:
ctx.vertex(pos, uv, normal)
```

## Migration order

1. Introduce `RenderCtx`, `CuboidBounds` (rename FluidBounds), verify `UvRect` coverage
2. Refactor `GooRenderUtil.vertex`/`vertexColored` to accept `RenderCtx`
3. Collapse `CanisterGeometry.faceNorth/South/West/East` into `emitFace(ctx, bounds, uv, dir)`
4. Update BER renderers: CanisterFluidRenderer, HubFluidRenderer, VatFluidRenderer
5. Update ShellFaceEmitter (same pattern, colored variant)
6. Update GooStreamRenderer, FrostFieldBER, ChainMarkerGeometry
7. Update overlay renderers (SlotOutlineDrawing, VoxelHighlightRenderer, etc.)
8. Update HUD painters (CanisterPanelPainter, CruciblePanelPainter, VatHudPanelPainter)

## Similar patterns elsewhere (audit later)

- `client/overlay/ArcRenderer.java` - dash segment emission, same pose/consumer/light threading
- `client/overlay/GasketMeshEmitter.java` - 9-16 param methods, mesh face emission
- `client/overlay/WireframeRenderer.java` - line emission with same threading
- `client/model/CanisterSpecialRenderer.java` - baked model rendering with UV computation
- `client/model/VatSpecialRenderer.java` - same pattern
- `client/particle/Goo*Particle.java` - particle vertex emission (may need different ctx)

## Not in scope

- `client/radial/GooRadialRenderer.java` - GUI rendering, different pipeline (GuiGraphics not VertexConsumer)
- `client/hud/*` - billboard HUD rendering, uses GuiGraphics, different param shapes
- Non-rendering ParameterNumber violations (block interaction, data pipeline) - separate effort
