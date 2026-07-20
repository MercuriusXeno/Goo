---
id: plan-effects-12-extending
title: "Effect 12: Pulse — Extending"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 12: Pulse — Extending

**Class:** `effect/brew/ExtendingEffect.java` | **Category:** BENEFICIAL | **Color:** `0xCC0000`

## Design Spec

> "Extending - next potion in 5s steals its duration. Miss window = extends lowest active effect. On expiry: potion tolerance (half original duration) diminishes next potion."
> — DESIGN-TYPES.md, Pulse brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Steals its duration" — new potion cancelled, or applied normally with duration also added to Extending? | New potion is applied normally. Its duration is ALSO added to Extending's remaining duration. "Steal" = Extending grows longer by parasitizing the new potion. |
| 2 | "Miss window" — extends lowest by how much? | Extend lowest active effect by 30*LVL seconds (600*LVL ticks). |
| 3 | "Half original duration diminishes next potion" — halves duration? Fixed reduction? | Apply `PotionToleranceEffect` lasting half the original Extending duration. While active, all new potion durations reduced by 25%. |
| 4 | One-shot or repeating 5s windows? | Repeating. Every 5 seconds, the window resets. Each cycle either steals or extends. |
| 5 | Does stealing from another Extending potion create infinite loops? | No. Extending is excluded from the steal target list. Can't steal from itself or another Extending. |
| 6 | What's "lowest active effect"? By remaining duration, or by total duration? | By remaining duration. The effect closest to expiring gets extended. |

## Prerequisites

- Infra A (foundation)
- Custom data attachment for window tracking

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/ExtendingEffect.java` | 5s window state machine + steal/extend |
| `effect/brew/PotionToleranceEffect.java` | Secondary HARMFUL: reduces incoming durations |
| `effect/brew/ExtendingWindowData.java` | Tracks window start tick + original duration |
| `test/.../effect/brew/ExtendingEffectTest.java` | Pure-function + state machine tests |
| `test/.../effect/brew/PotionToleranceEffectTest.java` | Duration reduction tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add EXTENDING + POTION_TOLERANCE holders |
| `registry/GooPotions.java` | PULSE case: use custom effect |
| `en_us.json` | Add `"effect.goo.extending": "Extending"`, `"effect.goo.potion_tolerance": "Potion Tolerance"` |

### ExtendingEffect — State Machine

**Window cycle (every 100 ticks = 5 seconds):**
1. Window opens. Listen for new potion applications.
2. If new potion applied within window → **steal**: add its duration to Extending's duration.
3. If window closes with no new potion → **extend**: find lowest-duration active effect, extend by `extensionAmount(amplifier)`.
4. Reset window for next cycle.

**Event subscriber:** `MobEffectEvent.Added` — fires when any MobEffect is applied to any entity.
- If the entity has Extending active and is in a steal window:
  - If the new effect is NOT Extending and NOT PotionTolerance:
    - Add the new effect's duration to Extending's remaining duration
    - Mark window as consumed

**On expiry:** Apply PotionToleranceEffect for `surgeDuration(originalDuration)`.

### PotionToleranceEffect

**Event subscriber:** `MobEffectEvent.Added` — when a new effect is added while Tolerance is active:
- Reduce the new effect's duration by 25%: `instance.update(new MobEffectInstance(effect, duration * 3/4, ...))`
- This makes subsequent potions weaker, preventing infinite extension chains.

### Testable Seams

```java
static boolean isInStealWindow(long currentTick, long windowStartTick) {
    return (currentTick - windowStartTick) < 100; // 5 seconds
}
static MobEffectInstance findLowestDurationEffect(Collection<MobEffectInstance> effects) {
    // Return the effect with the smallest remaining duration, excluding Extending/Tolerance
}
static int extensionAmount(int amplifier) {
    return 600 * level(amplifier); // 30*LVL seconds in ticks
}
static int toleranceDuration(int originalDuration) {
    return originalDuration / 2;
}
static int reducedDuration(int originalDuration) {
    return originalDuration * 3 / 4; // 25% reduction
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `isInWindow_start` | `isInStealWindow(0, 0)` == true |
| `isInWindow_4s` | `isInStealWindow(80, 0)` == true |
| `isInWindow_5s` | `isInStealWindow(100, 0)` == false |
| `extensionAmount_level1` | `extensionAmount(0)` == 600 |
| `extensionAmount_level2` | `extensionAmount(1)` == 1200 |
| `toleranceDuration_1200` | `toleranceDuration(1200)` == 600 |
| `reducedDuration_1200` | `reducedDuration(1200)` == 900 |
| `findLowest_multipleEffects` | Returns effect with smallest duration |
| `findLowest_excludesSelf` | Skips Extending and Tolerance effects |

## Rendering

- Timer HUD showing 5s window countdown (small bar or number near effect icon)
- Particle burst when a duration is stolen (red pulse)
- Particle burst when an effect is extended (white pulse)

## Complexity

Hard — state machine with timing windows, event interception of other potions, secondary effect, duration manipulation on both self and other effects.
