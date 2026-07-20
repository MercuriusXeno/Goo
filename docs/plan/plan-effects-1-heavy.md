---
id: plan-effects-1-heavy
title: "Effect 1: Metal — Heavy"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 1: Metal — Heavy

**Class:** `effect/brew/HeavyEffect.java` | **Category:** BENEFICIAL | **Color:** `0xC0C0C0`

## Design Spec

> "Heavy - bonus fall damage, heavy metal poisoning (scaling damage over time)"
> — DESIGN-TYPES.md, Metal brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Bonus fall damage" — no multiplier. To whom? Player takes more, or deals more to mobs landed on? | Player deals +50%*LVL bonus fall damage to entities they land on. Player takes normal fall damage. |
| 2 | "Heavy metal poisoning" — same wording as Contact effect. What's the brew version's damage rate? | 1 HP every `60/LVL` ticks. LVL 1 = 3s, LVL 2 = 1.5s. This is the tradeoff for the combat utility. |
| 3 | Listed as BENEFICIAL but has inherent downside (poisoning). Intentional? | Yes. Like Turtle Master (Resistance + Slowness), the downside is the price of entry. |

## Prerequisites

Build Foundation (Infra A) first:
1. `effect/brew/GooBrewEffect.java` — abstract base class extending `MobEffect`
2. `registry/GooBrewEffects.java` — DeferredRegister for `MOB_EFFECT`
3. Wire `GooBrewEffects.MOB_EFFECTS.register(modEventBus)` in `Goo.java`
4. Add `effect.goo.heavy` key to `en_us.json`
5. Update `GooPotions.createPotion(METAL)` to use the custom effect

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/GooBrewEffect.java` | Abstract base: constructor(category, color), configurable tick interval |
| `effect/brew/HeavyEffect.java` | Concrete effect: poison tick + gravity modifier |
| `registry/GooBrewEffects.java` | DeferredRegister + DeferredHolder for HEAVY |
| `test/.../effect/brew/HeavyEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `Goo.java` | Add `GooBrewEffects.MOB_EFFECTS.register(modEventBus)` |
| `registry/GooPotions.java` | METAL case: use `GooBrewEffects.HEAVY` instead of vanilla effects |
| `en_us.json` | Add `"effect.goo.heavy": "Heavy"` |

### GooBrewEffect Base Class

```java
public abstract class GooBrewEffect extends MobEffect {
    protected GooBrewEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    /** Override to change tick interval (default: every 20 ticks). */
    protected int tickInterval() { return 20; }

    /** Converts amplifier to human-readable level (amplifier 0 = LVL 1). */
    protected static int level(int amplifier) { return amplifier + 1; }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = tickInterval();
        return interval <= 1 || duration % interval == 0;
    }
}
```

### HeavyEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: return true every `poisonInterval(amplifier)` ticks
- `applyEffectTick(ServerLevel, LivingEntity, int)`: deal 1 HP magic damage (the poisoning)
- Constructor: add attribute modifier for `Attributes.GRAVITY` at +20% per level

**Event subscriber:**
- `LivingFallEvent`: if falling entity has Heavy effect, increase damage dealt to entities at landing position by `fallDamageMultiplier(amplifier)`
- Scan for entities at landing BlockPos using `level.getEntitiesOfClass()` with inflated AABB
- Apply bonus damage via `entity.hurt(damageSource, bonusDamage)`

### Testable Seams (all pure, no Minecraft deps)

```java
/** Ticks between each poison damage tick. */
static int poisonInterval(int amplifier) {
    return Math.max(1, 60 / level(amplifier));
}

/** Fall damage multiplier applied to entities landed on. */
static float fallDamageMultiplier(int amplifier) {
    return 1.0f + 0.5f * level(amplifier);
}

/** Bonus fall damage dealt to a nearby entity. */
static float bonusFallDamage(float baseFallDamage, int amplifier) {
    return baseFallDamage * (fallDamageMultiplier(amplifier) - 1.0f);
}

/** Gravity attribute modifier amount per level. */
static double gravityBonus(int amplifier) {
    return 0.2 * level(amplifier);
}
```

### Registration

```java
// GooBrewEffects.java
public static final DeferredHolder<MobEffect, HeavyEffect> HEAVY =
    MOB_EFFECTS.register("heavy", () -> new HeavyEffect());

// GooPotions.java — METAL case becomes:
case METAL -> new Potion(name,
    new MobEffectInstance(GooBrewEffects.HEAVY, 1200, 0));
```

## Test Plan

| Test | Assertion |
|---|---|
| `poisonInterval_level1` | `poisonInterval(0)` == 60 |
| `poisonInterval_level2` | `poisonInterval(1)` == 30 |
| `poisonInterval_level5` | `poisonInterval(4)` == 12 |
| `fallDamageMultiplier_level1` | `fallDamageMultiplier(0)` == 1.5 |
| `fallDamageMultiplier_level3` | `fallDamageMultiplier(2)` == 2.5 |
| `bonusFallDamage_10base_level1` | `bonusFallDamage(10, 0)` == 5.0 |
| `gravityBonus_level1` | `gravityBonus(0)` == 0.2 |
| `gravityBonus_level3` | `gravityBonus(2)` == 0.6 |

## Rendering

Standard effect icon only. No heart overlay, no custom particles (optional iron-drip particles can be added later).

## Complexity

Simple — no shared infrastructure dependencies beyond the foundation (Infra A), which must be built for all 15 effects anyway.
