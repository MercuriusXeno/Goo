---
id: plan-effects-3-bramble-hearts
title: "Effect 3: Leaf — Bramble Hearts"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 3: Leaf — Bramble Hearts

**Class:** `effect/brew/BrambleHeartsEffect.java` | **Category:** BENEFICIAL | **Color:** `0x7EC850`

## Design Spec

> "Bramble hearts fill empty hearts, deal thorns (P * N). Take 2x fire damage."
> — DESIGN-TYPES.md, Leaf brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Fill empty hearts" — absorption or healing? | Absorption. Grants extra HP above current, rendered as bramble hearts. Incentivizes drinking at full HP to stack on top. |
| 2 | "P * N" — P=potency, N=? | P = LVL (amplifier+1), N = remaining bramble hearts (absorptionHP / 2). Thorns weakens as hearts are consumed. |
| 3 | "Thorns" — auto-retaliation or player-dealt? | Auto-retaliation. When player is hit, attacker takes P*N damage. Like Thorns enchantment but scaling. |
| 4 | Do bramble hearts regenerate during the effect? | No. Granted once on application. Consumed = gone. |
| 5 | "Take 2x fire damage" — all fire sources? | Yes. All DamageSource with `is(DamageTypeTags.IS_FIRE)`. The bramble hearts are dry and flammable. |

## Prerequisites

- Infra A (foundation)
- Infra B (Custom Heart Overlay)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/BrambleHeartsEffect.java` | Absorption grant + thorns + fire vulnerability |
| `test/.../effect/brew/BrambleHeartsEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add BRAMBLE_HEARTS holder |
| `registry/GooPotions.java` | LEAF case: use custom effect |
| `en_us.json` | Add `"effect.goo.bramble_hearts": "Bramble Hearts"` |

### BrambleHeartsEffect

**MobEffect overrides:**
- `onEffectAdded()`: grant `absorptionAmount(amplifier)` absorption HP
- `onMobHurt(ServerLevel, LivingEntity, int, DamageSource, float)`:
  - If source is fire: double the damage (modify amount)
  - If attacker is LivingEntity: deal `thornsDamage(amplifier, entity.getAbsorptionAmount())` to attacker
- `onMobRemoved()`: strip remaining absorption (set to 0 if granted by this effect)
- `shouldApplyEffectTickThisTick()`: return false (event-driven only)

**Fire vulnerability:** Subscribe to `LivingIncomingDamageEvent` and check `source.is(DamageTypeTags.IS_FIRE)`. Double the amount. Alternative: use `onMobHurt()` if it gives access to modifiable damage.

### Testable Seams

```java
/** Absorption HP granted on application. */
static float absorptionAmount(int amplifier) {
    return 2.0f * level(amplifier);
}

/** Thorns damage dealt to attacker. P=level, N=remaining bramble hearts. */
static float thornsDamage(int amplifier, float absorptionHP) {
    int brambleHearts = (int) (absorptionHP / 2.0f);
    return level(amplifier) * brambleHearts;
}

/** Fire damage multiplier while effect is active. */
static float fireMultiplier() {
    return 2.0f;
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `absorptionAmount_level1` | `absorptionAmount(0)` == 2.0 |
| `absorptionAmount_level3` | `absorptionAmount(2)` == 6.0 |
| `thornsDamage_level1_2hearts` | `thornsDamage(0, 4.0)` == 2.0 |
| `thornsDamage_level2_3hearts` | `thornsDamage(1, 6.0)` == 6.0 |
| `thornsDamage_noAbsorption` | `thornsDamage(0, 0.0)` == 0.0 |
| `fireMultiplier` | `fireMultiplier()` == 2.0 |

## Rendering

- Bramble heart sprites replacing absorption heart positions via Infra B
- Texture: `assets/goo/textures/gui/bramble_heart.png` (green thorny heart)
- When thorns triggers: brief green particle burst on attacker

## Complexity

Medium — straightforward event-driven logic, rendering depends on Infra B.
