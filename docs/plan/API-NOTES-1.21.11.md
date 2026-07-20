---
domain: goo
type: knowledge
verification: pending
purpose: NeoForge 1.21.11 API renames, signature changes, and migration pitfalls — reference when writing new code that touches serialization, entities, effects, rendering, or recipes
tags: [goo, neoforge, 1.21.11, migration, API, rendering, serialization, entity, effects, brewing, recipes, commands]
---

# 1.21.11 API Notes

Pitfalls discovered during the NeoForge 1.21.11 migration. Reference when writing new code that touches these APIs.

## Serialization & Data
- `saveAdditional`/`loadAdditional` use `ValueOutput`/`ValueInput`, not `CompoundTag`
- `Item.use()` returns `InteractionResult` (not `InteractionResultHolder`)
- `ResourceLocation` renamed to `Identifier` (same methods)
- `javax.annotation.Nullable` replaced by `org.jspecify.annotations.Nullable`

## Entity & Item APIs
- `EntityType.Builder.build()` takes `ResourceKey<EntityType<?>>`, not String
- `EntityType.create()` requires `EntitySpawnReason` parameter
- `ItemCooldowns.addCooldown()` takes `ItemStack` as first arg (not Item)
- `Entity.spawnAtLocation()` requires `ServerLevel` as first arg
- `ThrowableItemProjectile` moved to `net.minecraft.world.entity.projectile.throwableitemprojectile`
- `AreaEffectCloud.setCustomParticle()` replaces `setParticle()`

## Effects & Brewing
- MobEffects field renames: `MOVEMENT_SLOWDOWN`->`SLOWNESS`, `DAMAGE_BOOST`->`STRENGTH`, `CONFUSION`->`NAUSEA`, `JUMP`->`JUMP_BOOST`, `DAMAGE_RESISTANCE`->`RESISTANCE`, `HEAL`->`INSTANT_HEALTH`
- `Potion` constructor requires String name as first arg

## Recipes & Ingredients
- `Ingredient.items()` returns `Stream<Holder<Item>>` (not Iterable)
- Recipe results: use `assemble()` API with empty inputs, not reflection (vanilla ignores input and returns result.copy())

## Commands
- `CommandSourceStack.hasPermission(int)` replaced by `permissions().hasPermission(Permission)` with `PermissionLevel` enum

## Rendering
- `RenderType` moved to `net.minecraft.client.renderer.rendertype.RenderType`. Factory methods on `RenderTypes` (plural), not `RenderType`.
- `BlockEntityRenderer<T, S>` requires 2 type params: `T extends BlockEntity, S extends BlockEntityRenderState`. Uses state-based rendering: `createRenderState()`, `extractRenderState()`, `submit(S, PoseStack, SubmitNodeCollector, CameraRenderState)`. No `render()` method.
- `BlockEntityRendererProvider<T, S>` also takes 2 type params matching the renderer.
- `GuiGraphics.blit()` requires `RenderPipeline` as first arg (use `RenderPipelines.GUI_TEXTURED`)
- `GuiGraphics.pose()` returns `Matrix3x2fStack` (2D), not `PoseStack` - use `pushMatrix()`/`popMatrix()` and `scale(float, float)`
- `ClientTooltipComponent.getHeight()` takes `Font` parameter
- `TextureAtlas.LOCATION_BLOCKS` deprecated. Use `Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)` for sprites, inline `Identifier.withDefaultNamespace("textures/atlas/blocks.png")` for GL binding.
- `@OnlyIn(Dist.CLIENT)` deprecated. Use registration-time dist filtering (`@EventBusSubscriber(value = Dist.CLIENT)`).

## Registration Events
- `@EventBusSubscriber` has no `bus` parameter - events route to correct bus automatically
- Register custom range select properties via `RegisterRangeSelectItemModelPropertyEvent` with `MapCodec`
- `RangeSelectItemModelProperty.get()` signature uses `ItemOwner` not `LivingEntity`
- Register item slot decorators via `RegisterItemDecorationsEvent` with `IItemDecorator`

## Assets & Atlases
- Atlas source files MUST be in `assets/minecraft/atlases/` (not `assets/<modid>/atlases/`). The atlas loader resolves `minecraft:atlases/blocks.json` by namespace:path - mod-namespaced files are never loaded. Multiple mods' files are merged via `getResourceStack()`.
- Parchment mappings may show incorrect class names (e.g. `Capabilities.FluidHandler` vs actual `Capabilities.Fluid`). Verify against compiled jar with `javap` when in doubt.

## Block API
- `Block.onRemove()` no longer exists. Use `playerWillDestroy()` for pre-removal drops, or `affectNeighborsAfterRemoval()` for neighbor updates.
- Vanilla chain block renamed to `iron_chain` in 1.21.11 (texture at `textures/block/iron_chain.png`).
