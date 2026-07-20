---
id: plan-effects-13-wither-inoculation
title: "Effect 13: Nether — Wither Inoculation"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 13: Nether — Wither Inoculation

**Class:** `effect/brew/WitherInoculationEffect.java` | **Category:** BENEFICIAL | **Color:** `0x8B0000`

## Design Spec

> "Wither Inoculation - halve current health, gain common negative effect immunity for duration. No natural regen. Configurable effect list."
> — DESIGN-TYPES.md, Nether brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Halve current health" — on application only, or maintained? | One-time on application. Player pays half their current HP as the entry cost. |
| 2 | "Common negative effect immunity" — which effects? | Default immune list: Poison, Wither, Weakness, Slowness, Mining Fatigue, Nausea, Blindness, Hunger. |
| 3 | "No natural regen" — all healing or just hunger-based? | Only natural regen (food-based vanilla healing). Potions of Healing, golden apples, and regeneration from other goo effects still work. |
| 4 | "Configurable effect list" — server config or datapack? | Server config. `List<String>` of effect registry IDs in `GooConfig`. Modpack authors can customize. |
| 5 | Does immunity prevent effects already applied before drinking? | No. Immunity only blocks NEW applications during the effect. Existing debuffs persist until they expire. |

## Prerequisites

- Infra A (foundation)
- Server config addition (`GooConfig`)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/WitherInoculationEffect.java` | HP halve + immunity + regen suppression |
| `test/.../effect/brew/WitherInoculationEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add WITHER_INOCULATION holder |
| `registry/GooPotions.java` | NETHER case: use custom effect |
| `en_us.json` | Add `"effect.goo.wither_inoculation": "Wither Inoculation"` |
| Config file (create if needed) | Add immune effect list |

### WitherInoculationEffect

**MobEffect overrides:**
- `onEffectAdded()`: `entity.setHealth(entity.getHealth() / 2.0f)`
- `shouldApplyEffectTickThisTick()`: return false (event-driven only)

**Event subscribers:**
- `MobEffectEvent.Applicable`: if entity has Wither Inoculation and incoming effect is in the immune list → deny the event. Pure predicate check against config list.
- `LivingHealEvent`: if entity has Wither Inoculation and heal source is natural regen → cancel. Detect natural regen by checking if `event.getAmount()` matches the vanilla regen amount and `player.getFoodData().getFoodLevel() >= 18`.

**Natural regen detection:** Vanilla regen happens when `foodLevel >= 18` and `saturationLevel > 0` or `foodLevel >= 20`. The heal event fires with amount 1.0. We can heuristically detect this vs potion healing (which fires with different amounts or alongside a MobEffectInstance check).

Alternative simpler approach: suppress ALL healing per tick by overriding `applyEffectTick()` to remove the REGENERATION effect if it was naturally applied. Or: just subscribe to LivingHealEvent and cancel if the entity doesn't have an active Regeneration MobEffect (natural regen doesn't come from a MobEffect).

### Testable Seams

```java
static float halvedHealth(float currentHealth) {
    return currentHealth / 2.0f;
}
static boolean isImmuneEffect(ResourceLocation effectId, List<ResourceLocation> immuneList) {
    return immuneList.contains(effectId);
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `halvedHealth_20` | `halvedHealth(20)` == 10.0 |
| `halvedHealth_1` | `halvedHealth(1)` == 0.5 |
| `isImmune_poison` | `isImmuneEffect(POISON_ID, defaultList)` == true |
| `isImmune_strength` | `isImmuneEffect(STRENGTH_ID, defaultList)` == false |
| `isImmune_emptyList` | `isImmuneEffect(POISON_ID, emptyList)` == false |

## Rendering

- Standard effect icon with dark red tint
- Dark particle aura (wither-style)

## Complexity

Medium — event interception is straightforward. Config list adds minor complexity. Natural regen detection needs careful heuristic.
