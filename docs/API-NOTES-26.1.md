---
domain: goo
type: knowledge
verification: pending
purpose: NeoForge 26.1 migration primer — breaking changes, API renames, and new systems. Reference when planning or executing the 1.21.11 → 26.1 port.
tags: [goo, neoforge, 26.1, migration, API, rendering, serialization, recipes, saveddata, fluids, models, java-25]
sources:
  - https://neoforged.net/news/26.1snapshots/ (Technici4n, 2025-12-26)
  - https://neoforged.net/news/26.1release/ (Technici4n, 2026-03-24)
  - https://github.com/ChampionAsh5357/neoforged-github/blob/update/26.1/primers/26.1/index.md (CC BY 4.0)
---

# 26.1 Migration Primer (Goo-Focused)

Conversion reference for porting Goo from NeoForge 1.21.11 to 26.1. Organized by impact to Goo systems. Full upstream primer: ChampionAsh5357 link in frontmatter.

---

## 1. Toolchain & Environment

| Item | 1.21.11 | 26.1 |
|------|---------|------|
| JDK | 21 | **25** (MS OpenJDK) |
| Gradle | 8.14.1 | **9.1.0+** |
| NeoGradle | 7.1.20 | **7.1.21** |
| MDG | — | **2.0.141** |
| Parchment | Required | **Drop it** — Mojang names now deobfuscated |
| Versioning | `21.11.x` | **Four-component:** `26.1.0.x-beta` |

**IntelliJ:** Requires 2025.2+ for Java 25 support.

JEP 447: statements before `super()` in constructors are now legal and used by vanilla.

---

## 2. SavedData Overhaul

**Goo impact: GasketRegistry (extends SavedData), any other SavedData subclasses.**

- `DimensionDataStorage` → `SavedDataStorage`
- `SavedDataType` now takes `Identifier` instead of `String` — enables subdirectories
- `MinecraftServer` now has its own `getDataStorage()` for global data (not just overworld level)
- `computeIfAbsent` pattern unchanged but types differ

```java
// New pattern
public static final SavedDataType<GasketData> TYPE = new SavedDataType<>(
    Identifier.fromNamespaceAndPath("goo", "gasket_network"),
    GasketData::new,
    MapCodec.unitCodec(GasketData::new),  // or real codec
    null  // data fixer type
);

// Query
GasketData data = server.getDataStorage().computeIfAbsent(GasketData.TYPE);
// or per-level:
GasketData data = level.getDataStorage().computeIfAbsent(GasketData.TYPE);
```

### Migrated vanilla data (now SavedData)

Game rules, weather, wandering trader, ender dragon fight, world gen settings, custom boss events, timer queues. Many `ServerLevelData` / `WorldData` getters moved to respective SavedData classes or `MinecraftServer`.

Key renames:
- `LevelData#isThundering/isRaining/setRaining` → `WeatherData`
- `ServerLevelData#getGameRules` → `GameRuleMap` (extends SavedData)
- `WorldData#worldGenOptions` → `WorldGenSettings` (extends SavedData)
- `LevelSettings` is now a record

---

## 3. Rendering — Complete Rewrite

**Goo impact: ALL BERs, particles, HUD billboards, fluid rendering, goo tinting.**

### 3a. Block Models

Old `BlockRenderDispatcher` and `ItemRenderer` are **removed**. Replaced by:
- `BlockModel` / `BlockModel$Unbaked` — per-blockstate model with feature submission pipeline
- `BlockModelResolver#update` sets up `BlockModelRenderState`, then `submit` renders
- `BlockStateModelWrapper`, `SelectBlockModel`, `ConditionalBlockModel`, `CompositeBlockModel`, `SpecialBlockModelWrapper` — six vanilla implementations
- `SpecialBlockModelRenderer` unified between item and block models
- `BlockModel` (old class) renamed `CuboidModel`; `BlockModelWrapper` → `CuboidItemModelWrapper`
- `BlockModelPart` → `BlockStateModelPart`; `BlockModelDefinition` → `BlockStateModelDispatcher`

### 3b. BER Pattern (New)

```java
public class ExampleRenderState extends BlockEntityRenderState {
    public final BlockModelRenderState exampleBlock = new BlockModelRenderState();
}

public class ExampleRenderer implements BlockEntityRenderer<ExampleBE, ExampleRenderState> {
    public static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    private final BlockModelResolver blockResolver;

    public ExampleRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blockResolver = ctx.blockModelResolver();
    }

    @Override
    public void extractRenderState(ExampleBE be, ExampleRenderState state, float pt,
            Vec3 cam, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        super.extractRenderState(be, state, pt, cam, breakProgress);
        this.blockResolver.update(state.exampleBlock, blockState, CONTEXT);
    }

    @Override
    public void submit(ExampleRenderState state, PoseStack pose,
            SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, pose, collector, camera);
        state.exampleBlock.submit(pose, collector, state.lightCoords,
            OverlayTexture.NO_OVERLAY, 0);
    }
}
```

### 3c. Block Tint Sources

`BlockColor` → `BlockTintSource`. Three contexts:
- `color(BlockState)` — general (used by BlockModels)
- `colorInWorld(BlockState, BlockAndTintGetter, BlockPos)` — world context
- `colorAsTerrainParticle(BlockState)` — particle context

Registration still via `BlockColors#register`, but takes `List<BlockTintSource>` indexed by `tintindex`.

`relevantProperties` determines if state change requires model re-render.

### 3d. Materials & Dynamic Layer Selection

- `ItemBlockRenderTypes` **removed**. Render layer computed from texture transparency at model load time.
- Textures → `Material` (model JSON reference) with optional `force_translucent`
- Old `Material` (atlas sprite ID) → `SpriteId`; `MaterialSet` → `SpriteGetter`; `SpriteGetter` → `MaterialBaker`
- Render order: solid → cutout → translucent (sorted by camera distance)

### 3e. Quad Particle Layers

`SingleQuadParticle$Layer` split into `OPAQUE_*` and `TRANSLUCENT_*`. Use `Layer.bySprite(sprite)` to auto-detect.

```java
public class GooParticle extends SingleQuadParticle {
    private final SingleQuadParticle.Layer layer;
    public GooParticle(..., TextureAtlasSprite sprite) {
        super(...);
        this.layer = SingleQuadParticle.Layer.bySprite(sprite);
    }
    @Override protected SingleQuadParticle.Layer getLayer() { return this.layer; }
}
```

### 3f. Fluid Models

New `FluidModel` record, baked separately from block models via `FluidStateModelSet#bake`.

```java
FluidModel.Unbaked gooFluid = new FluidModel.Unbaked(
    new Material(Identifier.fromNamespaceAndPath("goo", "block/goo_still"), true),
    new Material(Identifier.fromNamespaceAndPath("goo", "block/goo_flowing")),
    null,   // overlay (optional)
    null    // tint source (optional)
);
```

Tint via `BlockTintSource#colorInWorld`. Layer auto-determined from texture transparency.

### 3g. GUI

- `GuiGraphics` → `GuiGraphicsExtractor`
- `draw*`/`render*` → `extract*`, potentially `*RenderState` suffix
- `*String*` → `*Text*`
- `AbstractContainerScreen#render` now calls `renderTooltip` automatically — don't override `render` in subtypes
- `imageWidth`/`imageHeight` now final, set in constructor

### 3h. Pipeline Depth & Color

- `DepthTestFunction` → `CompareOp` (ALWAYS_PASS, LESS_THAN, LESS_THAN_OR_EQUAL, etc.)
- `DepthStencilState` and `ColorTargetState` consolidate pipeline state
- `LogicOp` removed entirely

### 3i. Feature Rendering Split

`render` → `renderSolid` + `renderTranslucent` (two passes).

### 3j. Camera

`Camera` extracted to `CameraRenderState` during `GameRenderer#renderLevel`. `FogRenderer#setupFog` returns `FogData`.

### 3k. QuadInstance

Brightness/tint/lightmap/overlay consolidated into `QuadInstance`. `putBulkData` split into `putBlockBakedQuad` (block models) and `putBakedQuad` (all else). `BlockQuadOutput` for section renderer.

### 3l. BlockAndTintGetter

Now client-only, attached to `ClientLevel`. Previous uses replaced by `BlockAndLightGetter`.

---

## 4. Recipe System

**Goo impact: Any custom recipes, recipe serializers, recipe builders.**

- `Recipe#assemble` **no longer takes `HolderLookup$Provider`**
- `RecipeSerializer` is now a **record** taking `MapCodec` + `StreamCodec`
- Inner `$Serializer` classes removed; replaced with static `MAP_CODEC`/`STREAM_CODEC`/`SERIALIZER` fields
- Recipe metadata: `Recipe$CommonInfo`, `Recipe$BookInfo`, `CraftingRecipe$CraftingBookInfo`, `AbstractCookingRecipe$CookingBookInfo`
- `RecipeBuilder#defaultId` returns `ResourceKey<Recipe<?>>` (not item-based)
- `RecipeSerializers` — all vanilla serializers moved here
- `ClickType` → `ContainerInput`

---

## 5. ItemStackTemplate & ItemInstance

**Goo impact: Anywhere recipes, advancements, or displays use ItemStack immutably.**

- `ItemStackTemplate` — immutable record (item holder, count, component patch)
- `ItemInstance` interface — shared by both `ItemStack` (mutable) and `ItemStackTemplate` (immutable)
- Recipes and advancements use `ItemStackTemplate` where previously `ItemStack`
- `DisplayInfo#getIcon` returns `ItemStackTemplate`
- `FluidStackTemplate` added by NeoForge

---

## 6. Loot Type Unrolling

- `*Type` wrapper classes for loot entries/functions/conditions/providers **removed**
- Registries take `MapCodec` directly
- `getType()` → `codec()`

---

## 7. Data Component Changes

### Initializers
- `DataComponentInitializers` — lazily bind components to `Holder` during resource reload
- `Item$Properties#delayedComponent` / `delayedHolderComponent` — lazy component setting
- `EitherHolder` **removed** — replaced by direct `Holder<T>` wrapping

### New Components
- `DYE` — replaces `DyeItem#getDyeColor` hardcoding
- `additional_trade_cost`
- Sound variant components (pig/cow/chicken/cat)

### Holder Changes
- `Holder#components`, `areComponentsBound`, `$Direct` takes `DataComponentMap`
- `ItemStack#getItemHolder` → `typeHolder`; `getTags` → `tags`

---

## 8. Level & Entity API

- `Level#random` field **protected** → use `getRandom()`
- `Entity#interactAt` **removed** → merged into `Entity#interact(Player, InteractionHand, Vec3)`
- `ChunkPos` now a record: `ChunkPos(BlockPos)` → `containing`, `ChunkPos(long)` → `unpack`, `toLong/asLong` → `pack`
- `TypedInstance<T>` interface on Entity, ItemStack, BlockEntity, BlockState, FluidState — unified `is()` checks
- `Entity#updateInWaterStateAndDoFluidPushing` → `updateFluidInteraction` via new `EntityFluidInteraction`
- `AbstractContainerScreen`: `imageWidth`/`imageHeight` now final, set via constructor params
- `AbstractContainerMenu#clicked` takes `ContainerInput` instead of `ClickType`
- `ItemStack#validateComponents` now private

---

## 9. Validation Overhaul

- New `Validatable` interface with `validate(ValidationContext)`
- Replaces `CriterionValidator` and ad-hoc validation
- `ProblemReporter` collects issues hierarchically

---

## 10. Cauldron Interactions

`CauldronInteraction$InteractionMap` → `$Dispatcher`. Supports tag-based and item-based registration (tags checked first). Registration moved to `CauldronInteractions`.

---

## 11. Tag Renames (Goo-Relevant Subset)

| Old | New |
|-----|-----|
| `minecraft:item/dyeable` | Split: `dyes`, `loom_dyes`, `loom_patterns`, etc. |
| `bamboo_plantable_on` | `supports_bamboo` |
| `mushroom_grow_block` | `overrides_mushroom_light_requirement` |
| Various `*_placeable` | `supports_*` pattern |

---

## 12. Minor but Notable

- `FarmBlock` → `FarmlandBlock`
- `WaterlilyBlock` → `LilyPadBlock`
- `EndDragonFight` → `EnderDragonFight`
- `DragonRespawnAnimation` → `DragonRespawnStage`
- `LevelResource` is now a record
- Entity texture paths reorganized into subdirectories
- Adult/baby animal models split into separate classes
- Sound variant registries for chicken/pig/cow/cat
- `Brain` serialization: `codec/serializeStart` → `pack/Brain$Packed`
- `LivingEntity#brainProvider` removed — store as static constant, construct via `makeBrain(Brain$Packed)`
- Activities: `ActivityData` record replaces inline registration
- Tripwire render pipeline removed (uses cutout now)
- `RandomPatchFeature` removed (use placements)
- File Fixer Upper system (world folder migration between versions)

---

## 13. NeoForge-Specific Additions

- `MutableQuad` helper for quad manipulation
- `FluidStackTemplate` — immutable fluid stack equivalent
- NeoForm versions: `<mc version>-<neoform build>` (no obfuscation step)
