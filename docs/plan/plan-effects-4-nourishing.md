---
id: plan-effects-4-nourishing
title: "Effect 4: Vital — Nourishing"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 4: Vital — Nourishing

**Class:** `effect/brew/NourishingEffect.java` | **Category:** BENEFICIAL | **Color:** `0xE74C3C`

## Design Spec

> "Nourishing - satiety regen + immediate heal (LVL). On expiry: metabolism surge depletes 50% * LVL more per action."
> — DESIGN-TYPES.md, Vital brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Immediate heal (LVL)" — LVL hearts (2*LVL HP) or LVL HP? | 2*LVL HP (LVL hearts). Consistent with vanilla where 1 heart = 2 HP. |
| 2 | "Satiety regen" — rate not specified. | 0.5 saturation per second per LVL. LVL 1 = 0.5/s, LVL 2 = 1.0/s. |
| 3 | "50% * LVL more per action" — what's "per action"? | All exhaustion-causing actions (vanilla exhaustion system). Multiplier = `1 + 0.5*LVL`. |
| 4 | Metabolism surge duration not specified. | Half the original potion duration. 1200-tick potion → 600-tick surge. |
| 5 | Does surge apply if effect is removed early (milk, command)? | No. Only on natural expiry (RemovalReason.EXPIRED). Milk is an intentional cleanse. |

## Prerequisites

- Infra A (foundation)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/NourishingEffect.java` | Heal on apply, saturation tick, surge on expiry |
| `effect/brew/MetabolismSurgeEffect.java` | Secondary HARMFUL effect: exhaustion multiplier |
| `test/.../effect/brew/NourishingEffectTest.java` | Pure-function tests |
| `test/.../effect/brew/MetabolismSurgeEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add NOURISHING + METABOLISM_SURGE holders |
| `registry/GooPotions.java` | VITAL case: use custom effect |
| `en_us.json` | Add `"effect.goo.nourishing": "Nourishing"`, `"effect.goo.metabolism_surge": "Metabolism Surge"` |

### NourishingEffect

**MobEffect overrides:**
- `onEffectAdded()`: `entity.heal(healAmount(amplifier))`
- `shouldApplyEffectTickThisTick()`: every 10 ticks
- `applyEffectTick()`: if entity is Player, add saturation via `player.getFoodData().eat(0, saturationPerTick(amplifier))`
- `onMobRemoved(EXPIRED)`: apply MetabolismSurgeEffect for `surgeDuration(originalDuration)`

**Duration tracking:** Need to store original duration on application. Options:
- Read from the MobEffectInstance in `onMobRemoved()` — but duration is 0 at expiry
- Store in a data attachment on `onEffectAdded()`
- Use a fixed surge duration (simpler, less design-faithful)

Proposed: store original duration in a transient per-entity map (WeakHashMap<LivingEntity, Integer>) set in `onEffectAdded()`.

### MetabolismSurgeEffect

**Event subscriber:**
- Subscribe to NeoForge `LivingExhaustionEvent` (if it exists) or find the exhaustion hook
- If no exhaustion event exists: override `applyEffectTick()` every tick to add extra exhaustion via `player.getFoodData().addExhaustion()`
- Simpler approach: add attribute modifier for `Attributes.HUNGER_DRAIN_RATE` or equivalent if available in 1.21.11

### Testable Seams

```java
static float healAmount(int amplifier) { return 2.0f * level(amplifier); }
static float saturationPerTick(int amplifier) { return 0.5f * level(amplifier); }
static float exhaustionMultiplier(int amplifier) { return 1.0f + 0.5f * level(amplifier); }
static int surgeDuration(int originalDuration) { return originalDuration / 2; }
```

## Test Plan

| Test | Assertion |
|---|---|
| `healAmount_level1` | `healAmount(0)` == 2.0 |
| `healAmount_level3` | `healAmount(2)` == 6.0 |
| `saturationPerTick_level1` | `saturationPerTick(0)` == 0.5 |
| `saturationPerTick_level2` | `saturationPerTick(1)` == 1.0 |
| `exhaustionMultiplier_level1` | `exhaustionMultiplier(0)` == 1.5 |
| `exhaustionMultiplier_level2` | `exhaustionMultiplier(1)` == 2.0 |
| `surgeDuration_1200ticks` | `surgeDuration(1200)` == 600 |

## Rendering

- Standard effect icon for Nourishing
- Separate "depleted/hungry" icon for Metabolism Surge
- No custom heart rendering

## Complexity

Medium — two-phase effect with secondary MobEffect class. Food system interaction needs API exploration.
