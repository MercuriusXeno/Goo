---
id: plan-effects-9-air-control
title: "Effect 9: Typhoon — Air Control"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 9: Typhoon — Air Control

**Class:** `effect/brew/AirControlEffect.java` | **Category:** BENEFICIAL | **Color:** `0xD5F5E3`

## Design Spec

> "Air control - hold jump for lift. Duration drains rapidly while gaining altitude."
> — DESIGN-TYPES.md, Typhoon brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | Lift rate per tick not specified. | `0.04 + 0.04*LVL` blocks/tick upward velocity. LVL 1 ≈ creative fly speed. LVL 2 = faster. |
| 2 | "Rapidly" — drain multiplier not quantified. | 3x normal drain while ascending (holding jump + moving upward). 1x when not ascending. |
| 3 | Max altitude cap? | No hard cap. Duration is the limiter. At 3x drain, LVL 1 (1200 ticks) lasts ~400 ticks ascending = ~20s of flight. |
| 4 | What happens when duration expires mid-air? | Grant 5 seconds (100 ticks) of Slow Falling. Safety valve. FUN > Balance. |
| 5 | Does horizontal movement work while ascending? | Yes. Player retains normal horizontal movement. Only vertical is affected by the lift. |

## Prerequisites

- Infra A (foundation)
- Infra D (Jump Override: client key detection + packet)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/AirControlEffect.java` | Per-tick lift + duration drain |
| `test/.../effect/brew/AirControlEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add AIR_CONTROL holder |
| `registry/GooPotions.java` | TYPHOON case: use custom effect |
| `en_us.json` | Add `"effect.goo.air_control": "Air Control"` |
| `network/JumpOverridePayload.java` | Client→server: "jump key is held" (Infra D, shared) |

### AirControlEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: every tick
- `applyEffectTick()`:
  - Check if player is holding jump (via server-side state from packet)
  - If holding: add `liftPerTick(amplifier)` to entity's Y velocity via `entity.setDeltaMovement()`
  - If ascending: accelerate duration drain (reduce remaining duration by extra 2 ticks per tick = 3x total)
- `onMobRemoved()`:
  - If entity is not on ground: apply Slow Falling for 100 ticks

**Duration drain acceleration:** Manipulate the MobEffectInstance's duration directly. On each tick while ascending, subtract 2 extra ticks. This means ascending costs 3 ticks of duration per real tick.

**Client packet:** Infra D sends a boolean "jump held" state. The server reads this to decide whether to apply lift. This is shared with Ender (Phasing) which also needs jump key state.

### Testable Seams

```java
static double liftPerTick(int amplifier) {
    return 0.04 + 0.04 * level(amplifier);
}
static int drainMultiplier(boolean isAscending) {
    return isAscending ? 3 : 1;
}
static int extraDrainPerTick(boolean isAscending) {
    return isAscending ? 2 : 0; // 2 extra + 1 natural = 3x
}
static boolean shouldGrantSlowFall(boolean isOnGround) {
    return !isOnGround;
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `lift_level1` | `liftPerTick(0)` == 0.08 |
| `lift_level2` | `liftPerTick(1)` == 0.12 |
| `lift_level3` | `liftPerTick(2)` == 0.16 |
| `drainMultiplier_ascending` | `drainMultiplier(true)` == 3 |
| `drainMultiplier_idle` | `drainMultiplier(false)` == 1 |
| `extraDrain_ascending` | `extraDrainPerTick(true)` == 2 |
| `extraDrain_idle` | `extraDrainPerTick(false)` == 0 |
| `slowFall_airborne` | `shouldGrantSlowFall(false)` == true |
| `slowFall_grounded` | `shouldGrantSlowFall(true)` == false |

## Rendering

- Wind/cloud particles spiraling around the player while ascending
- Subtle updraft particles below feet

## Complexity

Medium — per-tick velocity manipulation with client-server key sync. Duration manipulation is the tricky part.
