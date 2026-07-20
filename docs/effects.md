---
id: effects
title: Effect System
type: documentation
status: draft
author: documentarian
consumers: [dev, architect, pm]
tracks: [src/main/java/com/mercuriusxeno/goo/effect/WorldEffects.java, src/main/java/com/mercuriusxeno/goo/effect/GooMobEffects.java, src/main/java/com/mercuriusxeno/goo/effect/ContactEffects.java, src/main/java/com/mercuriusxeno/goo/effect/GooBrewingRecipes.java, src/main/java/com/mercuriusxeno/goo/entity/ThrownBlobEntity.java]
created: 2026-03-22
updated: 2026-03-22
---

# Effect System

## Trigger Flow

1. Player right-clicks blob/omniblob -> `GooBlobItem.use` / `GooOmniblobItem.use`.
2. Spawns `ThrownBlobEntity` with goo type and optional cursor-target.
3. On hit:
   - **Entity hit** (`onHitEntity`): `GooMobEffects.apply(level, living, type, thrower)`.
   - **Block hit** (`onHitBlock`): `WorldEffects.apply(level, pos, type)`.
4. **Contact effects**: Applied to the thrower on bare-handed throw (currently wired but `ContactEffectHandler` is a stub -- contact effects are in `ContactEffects.apply`).

## ThrownBlobEntity

Extends `ThrowableItemProjectile`. Synched `DATA_GOO_TYPE` string for client rendering.

**Cursor-targeting**: On `tick()`, if `cursorTarget` is alive, steers velocity toward target center. Homing speed = max of current speed or 0.8 blocks/tick.

## WorldEffects (Block Impact)

`WorldEffects.apply(level, pos, type)` -- switch on all 15 types:

| Type | Effect | Duration/Range |
|------|--------|----------------|
| METAL | Urchin damage cloud + immediate AoE (3 dmg) | 10s, r=1.5 |
| CRYSTAL | Shrinking damage cloud + AoE (4 dmg) | 15s, r=2.0 |
| LEAF | Growth pulse: 3x randomTick in 5x5 area | Instant |
| VITAL | Spawns a small slime | Persistent |
| SHROOM | Converts grass/dirt to mycelium, places mushrooms | 5x5 area |
| ROCK | Breaks rock-type blocks in 3x3x3 column below | Instant |
| BLAZE | TNT-strength explosion (3.0) | Instant |
| FROST | Freezes water->ice, lava->obsidian, removes fire | r=5 sphere |
| TYPHOON | Launches entities upward (1.5 velocity) | Column r=1 h=5 |
| GLOW | Places glowstone block | 1 block |
| HEX | Witch particle cloud | 30s, r=4 |
| PULSE | Block update pulse to neighbors | Immediate |
| NETHER | Dissolves soft blocks (hardness <= 3), drops items | r=3 sphere |
| ENDER | Places end rod (TODO: full teleporter) | 1 block |
| AEON | Places obsidian (TODO: custom barrier) | 1 block |

## GooMobEffects (Entity Impact)

`GooMobEffects.apply(level, target, type, thrower)` -- server-side only:

| Type | Effect |
|------|--------|
| METAL | 8 magic damage |
| CRYSTAL | 4 dmg to target + 2 AoE (r=3) |
| LEAF | Slowness II (5s) + Poison (3s) |
| VITAL | Clone chance = 100/health^0.6 (Mob only) |
| SHROOM | Slowness I + Weakness I + Poison (10s). Bosses immune |
| ROCK | Slowness 127 (immobility). If already max slow -> instant kill + cobblestone drop |
| BLAZE | 10s fire to target + 5s fire AoE (r=2.5) |
| FROST | 4 freeze damage + freeze buildup + Slowness III. Bosses immune |
| TYPHOON | Levitation I (5s) |
| GLOW | 12 damage to undead, Glowing to survivors |
| HEX | Weakness IV + Glowing, duration = 60/health^0.4 seconds (Mob only) |
| PULSE | NoAI + Slowness 127 (stun, Mob only) |
| NETHER | Halve current health + Wither I (10s). Wither immune |
| ENDER | Random teleport +/-16 blocks |
| AEON | NoAI + Invulnerable + Glowing (Mob only, TODO: egg ritual) |

## ContactEffects (Bare-Hand Throw)

`ContactEffects.apply(player, type)` -- applied to thrower. Each type has a unique penalty (poison, fire, freeze, teleport, etc.). Currently wired through `ThrownBlobEntity` flow. `ContactEffectHandler` is a retained stub.

## Brewing

`GooBrewingRecipes.onRegisterBrewingRecipes` -- registers 15 brewing recipes: blob item + awkward potion -> goo potion. One per type. Potions registered in `GooPotions.GOO_POTIONS` map.
