---
id: plan-effects
title: "M14: Custom potion effects — 15 brew effect plans"
type: plan
status: approved
goal: goal-002
created: 2026-03-29
updated: 2026-03-30
---

# M14: Custom Potion Effects — 15 Plans

## Context

All 15 goo potions use vanilla MobEffect placeholders (Strength, Slow Falling, etc). Each needs a custom MobEffect class with the designed brew behavior from DESIGN-TYPES.md.

**Naming:** `effect/GooMobEffects.java` already exists (thrown blob instant effects). New brew effects go in `effect/brew/` package. Registry in `registry/GooBrewEffects.java`.

## Shared Infrastructure (build first)

| ID | System | Needed by | Location |
|---|---|---|---|
| A | `GooBrewEffects` DeferredRegister + `GooBrewEffect` base class | All 15 | `registry/GooBrewEffects.java`, `effect/brew/GooBrewEffect.java` |
| B | Custom Heart Overlay | Leaf, Rock, Blaze | `client/render/CustomHeartOverlay.java` |
| C | Proximity Scanning | Frost, Aeon, Glow | `effect/brew/ProximityScan.java` |
| D | Jump Override (event + packet) | Ender, Typhoon, Shroom | `network/JumpOverridePayload.java`, `effect/brew/JumpOverrideHandler.java` |
| E | Client Block Scanning | Crystal, Shroom | `client/render/BlockScanRenderer.java` |

## Sequencing

1. **Foundation:** A (registry + base class), wire Goo.java, update GooPotions, add `effect.goo.*` lang keys
2. **Simple effects:** Metal, Vital, Nether (no shared infra)
3. **Proximity system + dependents:** C → Glow → Aeon → Frost
4. **Heart overlay + dependents:** B → Rock → Leaf → Blaze
5. **Jump override + dependents:** D → Typhoon → Ender → Shroom
6. **Client rendering:** E → Crystal → Hex
7. **State machine:** Pulse

## Sub-Plans

| # | Effect | File | Complexity |
|---|---|---|---|
| 1 | Metal: Heavy | `plan-effects-1-heavy.md` | Simple |
| 2 | Crystal: Mineral Sense | `plan-effects-2-mineral-sense.md` | Medium |
| 3 | Leaf: Bramble Hearts | `plan-effects-3-bramble-hearts.md` | Medium |
| 4 | Vital: Nourishing | `plan-effects-4-nourishing.md` | Medium |
| 5 | Shroom: Mycelial Network | `plan-effects-5-mycelial-network.md` | Hard |
| 6 | Rock: Stoneskin | `plan-effects-6-stoneskin.md` | Medium |
| 7 | Blaze: Infernal | `plan-effects-7-infernal.md` | Hard |
| 8 | Frost: Endothermic | `plan-effects-8-endothermic.md` | Hard |
| 9 | Typhoon: Air Control | `plan-effects-9-air-control.md` | Medium |
| 10 | Glow: Solar | `plan-effects-10-solar.md` | Simple |
| 11 | Hex: Soul Sight | `plan-effects-11-soul-sight.md` | Medium |
| 12 | Pulse: Extending | `plan-effects-12-extending.md` | Hard |
| 13 | Nether: Wither Inoculation | `plan-effects-13-wither-inoculation.md` | Medium |
| 14 | Ender: Phasing | `plan-effects-14-phasing.md` | Hard |
| 15 | Aeon: Timecurve | `plan-effects-15-timecurve.md` | Medium |

## Verification

- `./gradlew gooTest` — all pure-function seams tested
- `./gradlew build` — compiles without errors
- In-game: brew each potion via brewing stand, drink, verify behavior matches spec
- Check `en_us.json` has all `effect.goo.*` keys
- Verify GooPotions references custom effects, not vanilla placeholders
