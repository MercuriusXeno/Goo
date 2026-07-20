---
id: DESIGN-TYPES
title: Goo types, source items, and effect specifications
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
purpose: 15 types of goo. Every block and item is composed of one or more types. Each type has 4 effect categories:
---

# Goo Types

15 types of goo. Every block and item is composed of one or more types. Each type has 4 effect categories:
- **World:** thrown at blocks. Some stack on short delay.
- **Mob:** thrown at entities. Severely negative.
- **Brew:** potions via vanilla stand. Positive effects.

## Type Source Items

| Type | Source Items |
|------|-------------|
| Metal | Ore, Iron, Copper, Gold, Ancient Debris |
| Crystal | Diamond, Lapis, Emerald, Quartz |
| Leaf | Saplings, Wood, Grass, Wheat |
| Vital | Bread, Potato, Beetroot, Egg, Raw Beef, Raw Pork, Raw Cod, Bone, Bonemeal |
| Shroom | Brown/Red Mushroom, Crimson, Warped, Netherwart, Mycelium, Sculk |
| Rock | Stone, Dirt |
| Blaze | Blaze Rods, Blaze Powder, Magma Cream, Lava |
| Frost | Ice, Snow |
| Typhoon | Breeze Rod, Wind Charge, Feather, Shulker Shells, Phantom Membrane |
| Glow | Glowstone, Glow Berries, Glow Ink Sac, Shroomlight |
| Hex | Soul Sand, Sculk, Netherwart |
| Pulse | Redstone Dust, Redstone Blocks, Sculk Sensor |
| Nether | Netherrack, Crimson, Warped, Rotten Flesh, Wither Skulls |
| Ender | Ender Pearls, Endstone, Chorus Fruit, Purpur, Shulker Shells |
| Aeon | Diamond, Ancient Debris, Nautilus Shells |

## Multi-Type Items

| Item | Types |
|------|-------|
| Ancient Debris | Metal, Aeon |
| Diamond | Crystal, Aeon |
| Shulker Shells | Typhoon, Ender |
| Netherwart | Shroom, Hex |
| Crimson/Warped | Shroom, Nether |
| Sculk | Shroom, Hex |

Base values hand-assigned (~200 items). Others derived from crafting recipes by summing ingredient values.

last-accessed: 2026-03-26T04:16:05Z
---

"Collection": shift right click collects the blobs if you interact before the fuse ignites.

## Metal
- **World:** A spike which retracts until something enters the blob range. The blob shoots a spike in any direction it would impale a mob. Impales players unless they're sneaking. Flat mode to disable. Lasts a number of hits equal to its blobs
- **Mob:** Blob transmutes to a javelin mid-flight, high single-target damage.
- **Brew:** Ironskin. Iron hearts fill empty hearts, which halve damage they receive, but you move slower and sink in water.

## Crystal
- **World:** Create a cloud of suspended crystal slivers that damage anything that eviscerates anything that moves in it. Sneaking negates damage. Running aggravates it. Can flat mode to deactivate. Deals an *amount of damage* before deteriorating.
- **Mob:** Shatters into flechettes, AoE behind and around target.
- **Brew:** Mineral Sense - detect ores through walls via unique particles. Potency increases range.

## Leaf
- **World:** Creates a pulsing node which pulses every 5 seconds until depleted. Each pulse has a chance to grow nearby crops. Being waterlogged and hydrating farmland increases the reach from 5x5x1 (around) centered to 9x9x3(centered, strictly above).
- **Mob:** Entangle with vine pulling to origin. Stacks: breaking free deals thorns = vine count before break.
- **Brew:** Barkskin. Bramble hearts fill empty hearts, deal thorns (P * N). Take 2x fire damage.

## Vital
- **World:** Creates a life pulsing node, pulsing every 5 seconds. Each blob gives an additional pulse. Pulses cause animals in a 5x5 square centered around it to have a chance to mate. The mating chance is 100/h^0.8 where h = max health. Can flat mode.
- **Mob:** Clone chance = 100/h^0.6 where h = max health.
- **Brew:** Nourishing - satiety regens constantly under the effect and cures nausea and "inediate". Causes "inediate" debuff on expiration, with which you cannot eat food. Lasts 5 minutes.

## Shroom
- **World:** Tunneler, converts the affected area into some mushroom block or variant, or grows mushrooms. This can result in oddities. can flat mode.
- **Mob:** Blend of slow, weak, poison. Bosses immune, players not. Turns cows into mooshrooms.
- **Brew:** See mycelial nodes around shroom blocks (16m x LVL, through walls). Shift+look to warp (similar to ender node). Each jump halves duration.

## Rock
- **World:** Crushes a tunnel in one direction as if with silk touch. Can flat mode (crushes a big circle).
- **Mob:** Petrifies. Crushes petrified mobs (drop cobblestone). Formula: n^0.5.
- **Brew:** Stoneskin - empty hearts become stone hearts blocking hits. Explosions break all.

## Blaze
- **World:** Tunneler that smelts blocks as if broken with fortune III. Can flat mode (smelts a big circle).
- **Mob:** Ignite in 2.5m radius, fire spreads to anything entering radius. Stronger than normal fire. Respects fire immunity.
- **Brew:** Infernal - health converts to blaze hearts. Ash over 2n seconds (n=potency). Ash hearts take 2x damage. Water/snow/ice/frost goo = instant ash. Fire damage heals and reignites.
(Synergizes with bramble hearts)

## Frost
- **World:** Freezes in a spheroid shape where thrown, fuse counts down and does a cold snap explosion. The cold snap freezes water blocks and lava in the radius permanently. Can flat mode (freezes a large radial field).
- **Mob:** Cold snap + freeze buildup (25%/n^0.2 where n=health). 100% = fully frozen. Bosses immune to buildup.
- **Brew:** Endothermic - drain heat per step (weakest frost world-effect). Extinguish projectiles/fires. Damage blazes/magma cubes within 12 blocks (proximity scaling), buff snow golems inversely. Heat absorption causes fire damage at threshold. Tolerance proportional to current health, cooldown proportional to missing health.

## Typhoon
- **World:** Create a propulsion jet at the location, pushing entities in a direction with varying force. Can flat mode (pushes radially in a big circle, outward). Lasts until collected.
- **Mob:** Levitation (float). If already levitating: *push*.
- **Brew:** Air control - hold jump for lift. Duration drains rapidly while gaining altitude.

## Glow
- **World:** Crystallizes into tiny glowing cuboid, non-colliding light source on the face hit. Lasts until destroyed. Can flat mode (creates a flatter light). Intensity of the light and size of the crystal increase with blobs.
- **Mob:** Smite (solar damage to undead, mild sunburn (1 magic damage) to others). Inflicts glow on survivors.
- **Brew:** Solar - can we make the player emit actual minecraft light?? Does the 26.1 lighting engine let us do this yet??

## Hex
- **World:** Transmutes blocks in the hit zone into similar blocks of the same value. May have weird effects on blocks that don't have similarities (config).
- **Mob:** Transmutes mobs into similar mobs with near health and other properties. May have weird effects on mobs that don't have similarities. Mobs are temporarily friendly to you, even if normally hostile.
- **Brew:** Soul sight - sense mob auras through walls. Red/crackling = hostile, blue/wavy = peaceful. Angers endermen.

## Pulse
- **World:** Permanent redstone clock pulses every 128 ticks. Each stack of pulse goo halves the delay, minimum of 1 tick pulses.
- **Mob:** Short circuit - mob stops all behavior until expiry.
- **Brew:** Extender - the next potion you drink steals extender's duration. When extender expires (or is consumed), you are debuffed with tolerance: all durations reduced 30%, lasting half the duration that was stolen.

## Nether
- **World:** Cuboid, axis aligned negative energy field. Can flat mode. Can *tunnel mode*. Can cube mode (cycles). Anything in the field is converted to goo blobs directly.
- **Mob:** Wither + halve current health (if not wither-immune).
- **Brew:** Wither Inoculation - halve current health, gain common negative effect immunity for duration. Disable natural regen for duration. Configurable effect list.

## Ender
- **World:** Create a stable teleporter node you can teleport to by looking at while holding shift. Can flat mode. Standing on an ender pad (flat mode) makes look-teleports automatic and instant.
- **Mob:** Teleport NON-BOSS far mobs near (within 8 blocks), near mobs far (beyond 8, up to 32), in the direction you're facing. Inflicts non-boss targets with spatial instability.
If it hits a target with spatial instability, they gain teleportitis, teleporting uncontrollable in the area. They will not be able to fight in this state. If it hits a target with teleportitis, they are banished to an immaterial plane. You do not receive drops for doing this.
- **Brew:** Phasing - when you jump, your jump is consumed and you blink in the direction of your cursor instead. Each jump halves duration. Range: 16 blocks * potion strength.

## Aeon
- **World:** Create a field that disintegrates all hostile non-boss mobs that pass through it. Can flat mode.
- **Mob:** Stasis + time-reversal ritual (100/x^0.6 % per application, x=max health). At 100% = egg. The mob reverse-ages when struck with time goo, until egged.
- **Brew:** Timecurve - slow mobs relative to proximity. Effect starts at ~10 blocks, stronger approaching 0. NOT stasis.


## Unstable
- **World:** Primes for explosion after a delay. ANY jarring causes the explosion to happen immediately.
- **Mob:** Primes for explosion after a delay. ANY jarring causes the explosion to happen immediately.
- **Brew:** Explodes. Do not brew unstable goo, it explodes.