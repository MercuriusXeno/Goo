---
id: user-stories
title: Backfill of user stories for all shipped features (M1-M12)
type: documentation
status: approved
author: documentarian
consumers: [pm, architect]
created: 2026-03-21
updated: 2026-03-21
---

# User Stories (Backfill)

Exhaustive documentation of shipped features (M1-M12). Grouped by epic. Standard format: "As a player, I can..." with acceptance criteria where design docs provide sufficient detail. Gaps flagged inline.

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Core Model & Units

### US-CORE-1: Goo type system
As a player, I can decompose items into one of 15 distinct goo types, each with unique properties and source items.

**Acceptance criteria:**
- 15 goo types exist: Metal, Crystal, Leaf, Vital, Shroom, Rock, Blaze, Frost, Typhoon, Glow, Hex, Pulse, Nether, Ender, Aeon
- Each type has defined source items (see DESIGN-TYPES.md table)
- Multi-type items (Ancient Debris, Diamond, Shulker Shells, Netherwart, Crimson/Warped, Sculk) decompose into multiple goo types simultaneously
- No goo-to-goo transmutation exists (anti-EMC rule)

### US-CORE-2: Microblob unit system
As a player, all goo quantities are measured in microblobs (mB), where 1 blob = 1,000 mB = 1 NeoForge millibucket.

**Acceptance criteria:**
- All internal values stored as `long` microblobs
- Display: mB if < 1,000; fractional blobs/KB/MB/GB otherwise
- 1 mB = 1 NeoForge millibucket for cross-mod interop

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Storage — Blobs

### US-BLOB-1: Stackable blob items
As a player, I can hold goo as stackable blob items (one per goo type, stack to 64, each = 1,000 mB).

**Acceptance criteria:**
- One blob item per goo type (15 total)
- Stacks to 64 (vanilla default)
- Each blob represents exactly 1,000 mB
- Volume math centralized in `BlobStacks` utility

### US-BLOB-2: Blob throwing with cursor targeting
As a player, I can throw blob items with 100% accuracy toward my crosshair.

**Acceptance criteria:**
- Throwing consumes one blob from the stack
- Blob lands where crosshair aims (cursor targeting)
- Bare-handed throw triggers contact effect on release (goo touches hand before flight)

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Storage — Omniblobs

### US-OMNI-1: Omniblob overflow container
As a player, I receive an omniblob item when goo volume doesn't fit neatly into blob stacks (sub-blob remainders < 1,000 mB or volumes > 64,000 mB).

**Acceptance criteria:**
- One omniblob per goo type (15 total), unstackable, uncapped capacity
- Volume stored via `BLOB_VOLUME` data component
- Machine output rule: if volume % 1000 == 0 AND volume <= 64,000 mB, output blob stack; otherwise output omniblob

### US-OMNI-2: Omniblob display tiers
As a player, I can visually distinguish omniblob size by its item model.

**Acceptance criteria:**
- Display tiers: Microblob (< 1,000 mB), Blob (1,000+), Kiloblob (10M+), Megablob (1B+), Gigablob (1T+), Terrablob (1P+)
- 4 size tiers (tiny/small/base/large) via `range_dispatch` with `goo:blob_size`
- Slot overlay: type icon (top-right) + compact volume label (bottom-right, half-scale)

### US-OMNI-3: Omniblob cursor interactions
As a player, I can extract blobs from omniblobs and absorb blob stacks into them via inventory clicks.

**Acceptance criteria:**
- Right-click empty slot: extract 1 blob (shift: up to 64)
- Blob stack clicked onto omniblob: absorbed into omniblob
- Blob stacks exceeding 64 overflow into omniblob

### US-OMNI-4: Omniblob quickcraft (drag-to-distribute)
As a player, I can drag an omniblob across inventory slots to distribute blobs evenly, following vanilla quickcraft conventions.

**Acceptance criteria:**
- Drag-to-distribute implemented via `OmniblobQuickCraftMixin`
- Preview rendering during drag
- Correct merge behavior when distributing into occupied slots

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Storage — Buckets

### US-BUCKET-1: Bucket of goo
As a player, I can carry mixed-type goo in a bucket item (up to 8,000 mB).

**Acceptance criteria:**
- Multi-type container (`BucketOfGooItem` + `BucketContents`), hard cap 8,000 mB
- Single-type: "{Type} Bucket of Goo"; multi-type: "Slurry"
- Unstackable, craft remainder = vanilla bucket
- Exposes `IFluidHandlerItem` for cross-mod interop

### US-BUCKET-2: Bucket world placement
As a player, I can right-click to place single-type bucket goo as a fluid block, and right-click goo blocks to pick them up.

**Acceptance criteria:**
- Right-click places up to 8 blobs per block (single-type only)
- 8 blobs (8,000 mB) = full source block; 1-7 = partial
- Right-click goo block to pick up into bucket
- Slurry cannot be placed
- Model: `neoforge:fluid_container`

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Goo Fluids

### US-FLUID-1: Non-flowing goo fluids
As a player, goo exists as 15 non-flowing fluid types in the world.

**Acceptance criteria:**
- 15 non-flowing Source/Flowing fluid subclasses
- 8,000 mB = full source block; 1-7 blobs = partial
- Machines do NOT expose `IFluidHandler` (goo-internal transport only)

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Value System

### US-VALUE-1: Base item goo values
As a player, ~200 base items have hand-assigned goo values determining their goo composition.

**Acceptance criteria:**
- ~200 items with hand-assigned values
- Values stored in `GooValueRegistry`
- Server-to-client sync via `GooNetwork`

### US-VALUE-2: Recipe-derived goo values
As a player, items without hand-assigned values derive their goo values from crafting recipes via multi-pass derivation.

**Acceptance criteria:**
- Multi-pass recipe derivation sums ingredient values
- Equivalency rules: cobblestone=stone, storage blocks=9x base, nuggets=1/9 ingot, slabs=1/2 block, ore=ingot

### US-VALUE-3: Goo command
As a player (with permissions), I can use `/goo` commands for lookup, reload, regen, and audit of goo values.

**Acceptance criteria:**
- `/goo lookup` — query item goo values
- `/goo reload` — reload value registry
- `/goo regen` — regenerate derived values
- `/goo audit` — audit value consistency

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Crucible

### US-CRUCIBLE-1: Crucible goo production
As a player, I can place items in a crucible to extract their goo values into a mixed-type reservoir (slurry).

**Acceptance criteria:**
- Per-tick drain model via `PartiallyMeltedItem` (PMI) holding `BucketContents`
- Each tick with fuel, `extractionRate()` mB drains from PMI's largest type into reservoir
- Extraction rate: `remaining ^ (0.25 + matrices * 0.05)` mB/tick, max 5 matrices
- Fuel: depleted blaze rods, 1,200 ticks (60s) per rod
- Blobs bypass pipeline (raw goo bypass) — volume goes straight to reservoir

### US-CRUCIBLE-2: Crucible container evaluation
As a player, I can drop container items (shulker boxes, bundles) into the crucible, which recursively evaluates contents.

**Acceptance criteria:**
- Recursively walks container contents
- Items with goo values: summed
- Items without goo values: ejected as item entities above basin
- Nested containers recursed
- Container shell's own goo value added last

### US-CRUCIBLE-3: Crucible auto-push
As a player, the crucible automatically pushes reservoir contents to a gasket-linked destination every 20 ticks (1 second).

**Acceptance criteria:**
- Guard: reservoir non-empty and `gasketPartner` set
- Checks destination chunk loaded (skip if unloaded)
- Dispatches to CanisterBE (`insertGoo`), HubBE (`routeGoo`/`insertGoo`), VatBE (`insertGoo`)
- Unaccepted goo remains in reservoir
- Syncs to clients on reservoir change
- Push math extracted to `CruciblePushMath.computePush()` for testability

### US-CRUCIBLE-4: Crucible rune ink upgrades
As a player, I can apply rune ink matrices to the crucible interior to increase extraction rate.

**Acceptance criteria:**
- Max 5 matrices
- First (bottom) matrix enables gasket installation
- Each matrix increases the exponent in extraction formula by 0.05
- Runic matrix overlays render on crucible bottom face (4 jigsaw regions)

### US-CRUCIBLE-5: Crucible gasket slot
As a player, I can install a choral gasket on the first (bottom) runic matrix to enable auto-push networking.

**Acceptance criteria:**
- Gasket installs on bottom of any inscribed interior side
- Shift-right-click removes gasket and breaks any pairing

### US-CRUCIBLE-6: Crucible visual feedback
As a player, I see animated visuals when the crucible is operating.

**Acceptance criteria:**
- Fuel platform with lerped Y position
- Blaze rod that shrinks as fuel depletes
- Chain crosses (4 quarter-scale, `iron_chain` texture)
- Liquid surface: dominant goo type's animated fluid sprite, crossfade with 20-tick debounce
- Logarithmic fill curve (`computeLogFill`)
- Particles: sparks (8-12 at rod contact), embers (5%/tick), bubbles (color-tinted), melt smoke (3-5 on item absorb)
- Crucible HUD: in-world billboard showing type icons + volumes + fuel when crosshair targets basin

### US-CRUCIBLE-7: Crucible dropped PMI re-absorption
As a player, partially melted items (PMIs) that fall back into the crucible basin are re-absorbed.

**Acceptance criteria:**
- Dropped PMIs falling into basin re-absorbed
- Break drops: PMI, fuel rod, reservoir as blobs

### US-CRUCIBLE-8: Crucible blockstate and voxel shape
As a player, the crucible has a detailed basin shape with lever plates.

**Acceptance criteria:**
- Multipart blockstate: base + rotated plate overlays for N/S/E/W
- Properties: `POWERED`, `PLATE_NORTH/SOUTH/EAST/WEST`
- 5-part basin (4 walls Y=10-16 + floor Y=9-10), 4 feet, 4 legs
- Interlocking wall corners for interior face hit detection (rune ink targeting)

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Canister

### US-CAN-1: Canister goo storage
As a player, I can store typed goo in canister items that are type-locked on first insert and unlock when fully drained.

**Acceptance criteria:**
- Base capacity: 2^16 mB (~65K)
- Each runic matrix: 2^n multiplier, max 5 matrices (65K -> 131K -> 262K -> 524K -> 1M -> 2M)
- Type-locked on first insert; unlocks when fully drained
- Unstackable

### US-CAN-2: Canister 3x3 block placement
As a player, I can place canisters as blocks in a 3x3 grid with sub-shape gasket regions.

**Acceptance criteria:**
- 3x3 multi-canister block grid with sub-shape gasket regions (top/bottom)
- Owner UUID tracks who placed
- `CanisterSlotLayout.placementSlot(face, px, pz)` projects hit point to slot
- If occupied: `adjacentByCursorLean` picks neighbor cursor leans toward
- If still occupied: `findFirstEmpty` scans slots 0-8
- Surface-below targeting: click block underneath to insert into block above
- Green wireframe preview via `CanisterPlacementOverlay`

### US-CAN-3: Canister punch removal (hold-to-break)
As a player, I can hold left-click for 10 ticks on a canister to remove it from its slot as an item entity.

**Acceptance criteria:**
- `CanisterPunchListener` tracks 10-tick hold with aim verification
- Sends `CanisterPunchPayload(pos, slot)` to server on completion
- Server validates range and cooldown, removes canister, drops as item entity, plays dislodge sound
- If no canisters remain, removes the block
- Red wireframe progress via `SlotOutlineRenderer.renderPunchProgress()`

### US-CAN-4: Canister labeling
As a player, I can name canisters using the Choral Tuner to differentiate or designate their purpose.

**Acceptance criteria:**
- Non-unique name assignment via Choral Tuner
- Name displays on canister

### US-CAN-5: Canister gasket linking
As a player, I can link canisters to the gasket network by holding them and right-clicking input/output ports.

**Acceptance criteria:**
- Hold canister, right-click input port to send; output port to receive
- Shift-right-click (hold) to clear gasket pairing

### US-CAN-6: Canister inventory click interactions
As a player, I can interact with canisters in my inventory using blobs and buckets.

**Acceptance criteria:**
- Blob onto canister: entire volume transfers (up to capacity), remainder stays
- Right-click canister (empty cursor): drain up to 64,000 mB as blob item
- Empty bucket on canister: drain up to 8,000 mB into new bucket
- Partial bucket on canister: drain up to remaining bucket capacity

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Vat

### US-VAT-1: Vat bulk goo storage
As a player, I can store large volumes of single-type goo in a vat block.

**Acceptance criteria:**
- Base capacity: 2^20 mB (~1M)
- Same 2^n matrix formula, max 6 matrices (1M -> 2M -> 4M -> 8M -> 16M -> 32M -> 64M)
- Type-locked on first insert, unlocks when fully drained
- Right-click with blob to insert; empty hand to extract

### US-VAT-2: Vat visual model and connected textures
As a player, vats that stack vertically merge into a contiguous visual column.

**Acceptance criteria:**
- 14x16x14 cuboid: copper cap (top) + copper base (bottom) + glass body
- Gasket variant textures (`vat_cap_gasket.png` / `vat_base_gasket.png`) via `HAS_GASKET` blockstate
- Vertical stacking: internal faces culled, body texture spans full column via connected UV mapping

### US-VAT-3: Vat inventory click interactions
As a player, I can interact with vats using blobs and buckets.

**Acceptance criteria:**
- Blob onto vat: entire volume transfers (up to capacity)
- Right-click vat (empty cursor): drain up to 64,000 mB as blob item

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Hub

### US-HUB-1: Hub goo routing
As a player, I can use a hub block to hold up to 8 canisters and automatically route incoming goo to matching canisters.

**Acceptance criteria:**
- 8 radial canister slots (spokes/output pipes)
- Central input on top of device
- Auto-routes goo to any canister with matching type and available space
- Canisters freely replaceable

### US-HUB-2: Hub rendering
As a player, I see hub canister slots rendered with correct geometry.

**Acceptance criteria:**
- 8 radial slots with Blockbench coordinates (pixel units 1/16 block)
- Gaskets use `#0` (choral_gasket), body uses `#1` (canister_side)
- BER renders only occupied slots

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Plexer

### US-PLEXER-1: Plexer item reconstitution
As a player, I can use a plexer to reconstitute items from goo stored in attached canisters.

**Acceptance criteria:**
- Base: 2 canister slots
- Each runic matrix adds 1 slot, max 5 matrices = 7 slots total
- Reconstitutes items from canister goo contents

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Tap

### US-TAP-1: Tap goo extraction
As a player, I can attach a tap to a canister to drip blobs at varying speeds.

**Acceptance criteria:**
- Faucet connecting to a canister
- Drips blobs at varying speeds

> **GAP:** Drip rate mechanics referenced as "see ROADMAP.md M19" — detailed drip rate formula not specified in current design docs.

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Gasket Network

### US-GASKET-1: Choral gasket crafting and pairing
As a player, I can craft choral gaskets (yields 2) and pair them to create directional goo transport links.

**Acceptance criteria:**
- Recipe: Popped Chorus Fruit + Ender Pearls = 2 gaskets
- Gaskets connect two points, exchanging fluid obeying gravity (bottom sends, top receives)
- Shift-right-click removes gasket from machine and breaks pairing

### US-GASKET-2: Choral tuner
As a player, I can craft a choral tuner to detect gasket connections or override them.

**Acceptance criteria:**
- Recipe: Popped Chorus Fruit + Ender Pearls (different arrangement from gasket)
- Can detect connection of a gasket
- Can override/reassign gasket connections
- Also used to name canisters

### US-GASKET-3: Gasket network destinations
As a player, a crucible gasket can link to various destination types.

**Acceptance criteria:**
- Hub's intake gasket
- Specific hub canister slot (1-8)
- Specific block-canister slot (1-9)
- Canister in player inventory (retains link when picked up)

### US-GASKET-4: Gasket registry persistence
As a system, the gasket network is persisted as a directed graph with cycle detection.

**Acceptance criteria:**
- `GasketRegistry` (SavedData) stores world-level gasket graph
- Persisted in `goo_gasket_registry.dat` in overworld
- Directed graph with cycle detection

### US-GASKET-5: Gasket chunk loading
As a player, carrying a connected canister in my inventory keeps the destination chunk loaded, cascading upstream through the gasket network.

**Acceptance criteria:**
- Player carrying connected canister: destination chunk kept loaded
- Loading cascades upstream: any machine feeding the loaded destination also has its chunk loaded
- Recursion continues until full upstream graph exhausted
- Production chain runs while player walks away, as long as endpoint canister is carried

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Capabilities & Interop

### US-CAP-1: Block fluid capabilities
As a mod developer, vats and hubs expose standard NeoForge `Capabilities.Fluid.BLOCK` for pipe/conduit interop.

**Acceptance criteria:**
- Vat: fluid cap on UP/DOWN/null directions
- Hub: fluid cap on UP/null directions
- Canister shelf is a chest (item container), not a tank — no block fluid cap

### US-CAP-2: Custom gasket capabilities
As a system, gasket transport uses custom `GASKET_BLOCK` and `GASKET_ENTITY` capabilities with UUID context.

**Acceptance criteria:**
- `GASKET_BLOCK`: CanisterBE scans 9 slots, HubBE checks intake then 8 slots, VatBE checks cap/base
- `GASKET_ENTITY`: Player scans inventory for canister with matching UUID
- Context UUID is receiving-end gasket ID
- Crucible uses `BlockCapabilityCache` for block targets; live lookup for entity targets

### US-CAP-3: Item fluid capabilities
As a mod developer, bucket and canister items expose `Capabilities.Fluid.ITEM` for standard fluid handler interop.

**Acceptance criteria:**
- Bucket of Goo: `BucketGooFluidHandler`
- Canister: 15-tank `CanisterFluidHandler`

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Intermediate Materials

### US-MAT-1: Ash of Ages
As a player, I can craft Ash of Ages as a foundational intermediate material.

**Acceptance criteria:**
- Recipe: Blaze Powder + Gunpowder + Redstone Dust + Ancient Debris = 4 Ash of Ages

### US-MAT-2: Echo Crystal
As a player, I can craft Echo Crystals for soulbound smithing upgrades.

**Acceptance criteria:**
- Recipe: Diamond + Echo Shard + Ash of Ages = Echo Crystal
- Soulbound smithing upgrade for netherite equipment
- Requires Echo upgrade templates (ancient cities)

### US-MAT-3: Exorite
As a player, I can craft Exorite as endgame material.

**Acceptance criteria:**
- Recipe: Netherite Ingot + Echo Crystal x3 = 3 Exorite
- Exorite gear never fully breaks when durability depletes — regenerates by absorbing certain goo

### US-MAT-4: Rune Ink
As a player, I can craft Rune Ink (3 uses) to upgrade machines.

**Acceptance criteria:**
- Recipe: Ash of Ages + Glow Squid Ink + Feather + Glass Bottle = 1 Rune Ink (3 uses)
- Applies runic matrices to Crucible, Canister, Vat, Plexer

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Equipment

### US-EQUIP-1: Goo Glove
As a player, I can craft a Goo Glove to handle goo safely and select blob types via radial menu.

**Acceptance criteria:**
- Recipe: Leather + Nether Brick (shaped)
- Durability: 400
- Blocks contact effects while held
- Opens radial menu for blob type selection
- Throwing aeon goo repairs it

### US-EQUIP-2: Jeweled Glove
As a player, I can upgrade to a Jeweled Glove with higher durability.

**Acceptance criteria:**
- Recipe: Goo Glove + Diamond + Crystal Goo
- Durability: 1,600
- Same operation as Goo Glove
- Throwing aeon or crystal goo repairs it

### US-EQUIP-3: Goo Gauntlet
As a player, I can upgrade to a Goo Gauntlet (endgame tier) via smithing table.

**Acceptance criteria:**
- Jeweled Glove + Netherite Ingot + Netherite Upgrade template (smithing table)
- Fire immune
- Durability: 6,400
- Throwing aeon, nether, or metal goo repairs it

### US-EQUIP-4: Depleted Blaze Rod (fuel item)
As a player, I use depleted blaze rods as crucible fuel, with visual wear indication.

**Acceptance criteria:**
- Unstackable, `FUEL_REMAINING` data component (int, ticks)
- Orange durability bar
- 8-stage model via `range_dispatch` with `goo:fuel_remaining`
- 1,200 ticks (60s) per rod

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Goo Effects — World

### US-FX-WORLD-1: Metal world effect (Urchin Spines)
As a player, I can throw metal goo at blocks to create a multi-hit damage zone with urchin spines.

### US-FX-WORLD-2: Crystal world effect (Glass Shards)
As a player, I can throw crystal goo at blocks to create suspended glass shards that shred anything moving through without sneaking. Fades after fixed total damage.

### US-FX-WORLD-3: Leaf world effect (Growth Pulse)
As a player, I can throw leaf goo at blocks to trigger growth pulses every 16s. More effective on hydrated farmland. Stacks: each blob adds a pulse and reduces interval (diminishing).

### US-FX-WORLD-4: Vital world effect (Living Blob)
As a player, I can throw vital goo at blocks to create a living blob (stationary loyal slime) of the block's largest goo type. Quantity = combined value.

### US-FX-WORLD-5: Shroom world effect (Spore Area)
As a player, I can throw shroom goo at blocks to create a spore area that spawns mushrooms when mobs die nearby.

### US-FX-WORLD-6: Rock world effect (Implosion)
As a player, I can throw rock goo at blocks to trigger an implosion breaking pure rock in a 3x3x(n^2) area after delay. Depth stacks exponentially, max 5 (25 deep).

### US-FX-WORLD-7: Blaze world effect (Explosion)
As a player, I can throw blaze goo at blocks to trigger an explosion in 3/5/7/9 m^3 area, max 4 chain.

### US-FX-WORLD-8: Frost world effect (Freeze)
As a player, I can throw frost goo to freeze lava to obsidian and water to ice. Persists until destroyed. Chain max 3, radius 5/7/9. Extinguishes fires.

### US-FX-WORLD-9: Typhoon world effect (Jet Stream)
As a player, I can throw typhoon goo to create a persistent jet stream, orientable sideways. Stacks up to 5. Propulsion ~2^n meters.

### US-FX-WORLD-10: Glow world effect (Light Dome)
As a player, I can throw glow goo to crystallize into a permanent luminescent dome light source after short delay.

### US-FX-WORLD-11: Hex world effect (Monster Spawner)
As a player, I can throw hex goo to create an ensorcelled area that spawns monsters ignoring light. Duration reduced by successful spawns.

### US-FX-WORLD-12: Pulse world effect (Signal Pulse)
As a player, I can throw pulse goo to send a pulse to nearby signal receivers after delay. Chain refreshes delay + adds pulse. Max 16 chains.

### US-FX-WORLD-13: Nether world effect (Block Conversion)
As a player, I can throw nether goo to convert blocks in area to goo blobs (item form). Converts living goo to normal blobs. Chain max 4, radius 3/5/7/9 m^3.

### US-FX-WORLD-14: Ender world effect (Teleporter Node)
As a player, I can throw ender goo to create a stable teleporter node. Warp by looking + shift. Persists until destroyed.

### US-FX-WORLD-15: Aeon world effect (Barrier Block)
As a player, I can throw aeon goo to create a barrier-infused block that resists tools and explosions (not indestructible).

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Goo Effects — Mob

### US-FX-MOB-1: Metal mob effect (Javelin)
As a player, I can throw metal goo at mobs for single-target javelin damage.

### US-FX-MOB-2: Crystal mob effect (Flechettes)
As a player, I can throw crystal goo at mobs to shatter into flechettes (AoE behind and around target).

### US-FX-MOB-3: Leaf mob effect (Entangle)
As a player, I can throw leaf goo at mobs to entangle with vine pulling to origin. Breaking free deals thorns = vine count.

### US-FX-MOB-4: Vital mob effect (Clone)
As a player, I can throw vital goo at mobs for a clone chance = 100/h^0.6 (h = max health).

### US-FX-MOB-5: Shroom mob effect (Debilitate)
As a player, I can throw shroom goo at mobs for a blend of slow, weak, poison. Bosses immune, players not.

### US-FX-MOB-6: Rock mob effect (Petrify)
As a player, I can throw rock goo at mobs to petrify them. Crushing petrified mobs drops cobblestone. Formula: n^0.5.

### US-FX-MOB-7: Blaze mob effect (Ignite)
As a player, I can throw blaze goo at mobs to ignite in 2.5m radius. Fire spreads to anything entering radius. Respects fire immunity.

### US-FX-MOB-8: Frost mob effect (Cold Snap)
As a player, I can throw frost goo at mobs for cold snap + freeze buildup (25%/n^0.2, n=health). 100% = fully frozen. Bosses immune to buildup.

### US-FX-MOB-9: Typhoon mob effect (Levitation)
As a player, I can throw typhoon goo at mobs to cause levitation.

### US-FX-MOB-10: Glow mob effect (Smite)
As a player, I can throw glow goo at mobs for solar damage to undead (harmless to others). Inflicts glow on survivors.

### US-FX-MOB-11: Hex mob effect (Charm)
As a player, I can throw hex goo at mobs to charm them (passive to player, hostile to player's enemies). Duration: 60/N^0.4 seconds (N=current health).

### US-FX-MOB-12: Pulse mob effect (Short Circuit)
As a player, I can throw pulse goo at mobs to short circuit them (mob stops all behavior until expiry).

### US-FX-MOB-13: Nether mob effect (Wither)
As a player, I can throw nether goo at mobs for wither + halve current health (if not wither-immune).

### US-FX-MOB-14: Ender mob effect (Teleport)
As a player, I can throw ender goo at mobs to teleport far mobs near (within 8 blocks) and near mobs far (beyond 8, up to 32).

### US-FX-MOB-15: Aeon mob effect (Time Stop)
As a player, I can throw aeon goo at mobs to stop time + progress time-reversal ritual (100/x^0.6 % per application, x=max health). At 100% = egg. Mob invincible during stasis.

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Goo Effects — Brew (Potions)

### US-FX-BREW-1: Metal brew (Heavy)
As a player, I can brew metal goo into a potion: bonus fall damage + heavy metal poisoning.

### US-FX-BREW-2: Crystal brew (Mineral Sense)
As a player, I can brew crystal goo into a potion: detect ores through walls via unique particles. Potency increases range.

### US-FX-BREW-3: Leaf brew (Bramble Hearts)
As a player, I can brew leaf goo into a potion: empty hearts become bramble hearts dealing thorns (P * N). Take 2x fire damage.

### US-FX-BREW-4: Vital brew (Nourishing)
As a player, I can brew vital goo into a potion: satiety regen + immediate heal (LVL). On expiry: metabolism surge depletes 50% * LVL more per action.

### US-FX-BREW-5: Shroom brew (Mycelial Warp)
As a player, I can brew shroom goo into a potion: see mycelial nodes around shroom blocks (16m * LVL, through walls). Shift+look to warp. Each jump halves duration.

### US-FX-BREW-6: Rock brew (Stoneskin)
As a player, I can brew rock goo into a potion: empty hearts become stone hearts blocking hits. Explosions break all.

### US-FX-BREW-7: Blaze brew (Infernal)
As a player, I can brew blaze goo into a potion: health converts to blaze hearts. Ash over 2n seconds (n=potency). Ash hearts take 2x damage. Water/snow/ice/frost goo = instant ash. Fire damage heals and reignites.

### US-FX-BREW-8: Frost brew (Endothermic)
As a player, I can brew frost goo into a potion: drain heat per step. Extinguish projectiles/fires. Damage blazes/magma cubes within 8 blocks (proximity scaling). Buff snow golems inversely. Heat absorption causes fire damage at threshold.

### US-FX-BREW-9: Typhoon brew (Air Control)
As a player, I can brew typhoon goo into a potion: hold jump for lift. Duration drains rapidly while gaining altitude.

### US-FX-BREW-10: Glow brew (Solar)
As a player, I can brew glow goo into a potion: nearby undead treat you as sunlight. Highlighted, aggro from further.

### US-FX-BREW-11: Hex brew (Soul Sight)
As a player, I can brew hex goo into a potion: sense mob auras through walls. Red/crackling = hostile, blue/wavy = peaceful. Angers endermen.

### US-FX-BREW-12: Pulse brew (Extending)
As a player, I can brew pulse goo into a potion: next potion in 5s steals its duration. Miss window = extends lowest active effect. On expiry: potion tolerance diminishes next potion.

### US-FX-BREW-13: Nether brew (Wither Inoculation)
As a player, I can brew nether goo into a potion: halve current health, gain common negative effect immunity for duration. No natural regen. Configurable effect list.

### US-FX-BREW-14: Ender brew (Phasing)
As a player, I can brew ender goo into a potion: blink toward cursor instead of jumping. Each jump halves duration. Range: 16 blocks * potion strength.

### US-FX-BREW-15: Aeon brew (Timecurve)
As a player, I can brew aeon goo into a potion: slow mobs relative to proximity. Effect starts at ~8 blocks, stronger approaching 0.

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Goo Effects — Contact

### US-FX-CONTACT-1: Metal contact (Heavy Metal Poisoning)
Bare-handed throw: scaling/lethal damage that scales with duration and exposure.

### US-FX-CONTACT-2: Crystal contact (Glass Cactus)
Bare-handed throw: constant damage.

### US-FX-CONTACT-3: Leaf contact (Poison)
Bare-handed throw: basic poison, accumulates with exposure.

### US-FX-CONTACT-4: Vital contact (Harmless)
Bare-handed throw: no effect.

### US-FX-CONTACT-5: Shroom contact (Fungal Spore Infection)
Bare-handed throw: fungal spore infection.

### US-FX-CONTACT-6: Rock contact (Petrification)
Bare-handed throw: slow petrification. All hearts stone = death.

### US-FX-CONTACT-7: Blaze contact (Fire)
Bare-handed throw: fire damage.

### US-FX-CONTACT-8: Frost contact (Powdered Snow)
Bare-handed throw: powdered snow submersion.

### US-FX-CONTACT-9: Typhoon contact (Levitation)
Bare-handed throw: levitation accumulates (shulker-like).

### US-FX-CONTACT-10: Glow contact (Highlight)
Bare-handed throw: highlighted, increased monster aggro range.

### US-FX-CONTACT-11: Hex contact (Curse)
Bare-handed throw: curses unenchanted held gear. No gear = curse affects player (mob spawn after opaque delay).

### US-FX-CONTACT-12: Pulse contact (Signal Shock)
Bare-handed throw: damage from powered/signal-emitting objects.

### US-FX-CONTACT-13: Nether contact (Wither)
Bare-handed throw: wither, duration accumulates.

### US-FX-CONTACT-14: Ender contact (Teleportitis)
Bare-handed throw: random teleports (enderman in rain).

### US-FX-CONTACT-15: Aeon contact (Time Stop)
Bare-handed throw: time stop for fixed duration. Invincible, no damage, no healing, no entity updates.

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Rendering & Visuals

### US-RENDER-1: Animated fluid textures
As a player, goo fluids have animated textures generated by an offline cellular automaton.

**Acceptance criteria:**
- Offline CA generator (`FluidTextureGenerator.java`)
- Three heat layers: soupHeat, potHeat, flameHeat
- Per-type parameters: viscosity, decayRate, ignitionChance, ignitionStrength, potHeatRate, potInfluence, neighborhoodReach, frametime
- Gradient color system with positioned stops
- Output: 16x512 strip (32 frames) with mcmeta interpolation
- Ping-pong frame order (0-31, 30-1)
- Blob base: 16x16 CA snapshot masked to slimeball silhouette

### US-RENDER-2: Blob slot overlay
As a player, I see type icons and compact volume labels on omniblob items in inventory slots.

**Acceptance criteria:**
- `BlobVolumeDecorator`: type icon (top-right, `<type>_slot_icon.png`)
- Compact volume label (bottom-right, half-scale, 3 sig digits, no "B" suffix)

### US-RENDER-3: Canister BER
As a player, canisters render their fluid contents with correct geometry in hubs, plexers, and standalone.

**Acceptance criteria:**
- Shared `renderCanister()` method called by hub/plexer BERs with transforms
- BER renders only occupied slots

### US-RENDER-4: Vat BER with connected textures
As a player, vat fluid rendering shows contents within the graduated cylinder body.

**Acceptance criteria:**
- Large block tank with BER fluid rendering
- Connected UV mapping for vertically stacked vats

### US-RENDER-5: Bubble particle lifecycle
As a player, I see bubbles in the crucible with a multi-phase lifecycle.

**Acceptance criteria:**
- `GooBubbleParticle` 8-frame lifecycle: EXPAND (0-9), LINGER (10-29), DANCE (30-49), POP (50-53)
- Ring buffer (16 positions, MIN_SPACING_SQ = 0.0156)
- Idle boiling continues with fuel + goo (no fuel consumption)
- Early-pops when crucible empties

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Brewing Integration

### US-BREW-1: Goo brewing via vanilla stand
As a player, I can brew 15 goo potions using a vanilla brewing stand.

**Acceptance criteria:**
- 15 potions (one per goo type) via `GooBrewingRecipes`
- Standard vanilla brewing stand workflow
- Each potion applies the brew effect described in DESIGN-TYPES.md

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Thrown Blob Entity

### US-THROW-1: ThrownBlobEntity projectile
As a player, thrown blobs are cursor-targeting projectiles that trigger world or mob effects on impact.

**Acceptance criteria:**
- `ThrownBlobEntity` projectile class
- 100% accuracy cursor targeting
- World effect on block hit; mob effect on entity hit
- Contact effect on player at throw time (bare-handed only)
- Gloves block contact effects

last-accessed: 2026-03-26T04:18:02Z
---

## Epic: Cross-Mod Interop

### US-INTEROP-1: Pipe mod compatibility
As a player using pipe mods, I can extract/insert goo from vats and hubs via standard NeoForge fluid capabilities.

**Acceptance criteria:**
- Pipe mods use `Capabilities.Fluid.BLOCK` on vats (up/down) and hubs (up)
- Canister items expose `Capabilities.Fluid.ITEM` for hopper/pipe interop

last-accessed: 2026-03-26T04:18:02Z
---

## Flagged Gaps

### GAP-1: Tap drip rate mechanics
DESIGN-MACHINES.md references "see ROADMAP.md M19" for tap drip rate mechanics. No formula or detailed behavior spec exists in the current design docs.

### GAP-2: Plexer reconstitution logic
The plexer's reconstitution mechanics (recipe matching, goo consumption, output behavior) are not detailed in design docs. Only slot count and upgrade path are specified.

### GAP-3: Shader implementations
DESIGN-RENDERING-SHADERS.md describes planned shader effects (glowing thrown blobs, emissive goo fluid, color-pulsing crucible, soft particles, dissolve, fluid surface animation) but does not specify which are implemented vs planned.

### GAP-4: Living Blob entity behavior
Vital world effect creates "living blobs" (stationary loyal slimes) but detailed AI, combat behavior, expiry mechanics, and spawn rules are not specified beyond the glossary entry.

### GAP-5: Equipment radial menu
Glove/Gauntlet opens a "radial menu for blob type selection" but no UX spec (controls, layout, interaction) exists in design docs.

### GAP-6: Goo cauldron interactions
`GooCauldronInteractions` is referenced in the source structure but not documented in any design doc.

### GAP-7: Effect stacking and duration formulas
Many effects mention "stacks" or "accumulates" without specifying exact stacking formulas, caps, or duration calculations. The design docs provide some formulas (e.g., frost mob: 25%/n^0.2) but others use qualitative descriptions ("accumulates with exposure").

### GAP-8: Hub blob-onto-hub interaction
DESIGN-MACHINES.md lists "Blob onto hub: deferred (nested bundle behavior, TBD)" — this interaction is explicitly unresolved.
