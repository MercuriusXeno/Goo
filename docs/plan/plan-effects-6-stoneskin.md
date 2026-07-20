---
id: plan-effects-6-stoneskin
title: "Effect 6: Rock — Stoneskin"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 6: Rock — Stoneskin

**Class:** `effect/brew/StoneskinEffect.java` | **Category:** BENEFICIAL | **Color:** `0x808080`

## Design Spec

> "Stoneskin - empty hearts become stone hearts blocking hits. Explosions break all."
> — DESIGN-TYPES.md, Rock brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Empty hearts" — gap between current and max HP? Or all hearts become stone? | Gap: `maxHP - currentHP` granted as absorption. Drinking at low HP = more stone hearts. Drinking at full HP = 0. Incentivizes strategic timing. |
| 2 | "Blocking hits" — all damage types? | Yes, all damage types. Stone hearts are mechanically vanilla absorption with custom rendering. |
| 3 | "Explosions break all" — does explosion damage also apply after stripping? | Yes. Explosion zeroes absorption first, then remaining damage hits real HP. Explosions are the hard counter. |
| 4 | Can stone hearts regenerate during the effect? | No. One-time grant. If consumed, gone. |
| 5 | What if the player heals while Stoneskin is active — stone hearts persist? | Yes. Stone hearts (absorption) sit above real HP. Healing real HP doesn't affect them. |

## Prerequisites

- Infra A (foundation)
- Infra B (Custom Heart Overlay)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/StoneskinEffect.java` | Absorption grant + explosion vulnerability |
| `test/.../effect/brew/StoneskinEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add STONESKIN holder |
| `registry/GooPotions.java` | ROCK case: use custom effect |
| `en_us.json` | Add `"effect.goo.stoneskin": "Stoneskin"` |

### StoneskinEffect

**MobEffect overrides:**
- `onEffectAdded()`: `entity.setAbsorptionAmount(entity.getAbsorptionAmount() + stoneHeartAmount(maxHP, currentHP))`
- `onMobHurt()`: if `DamageSource` is explosion, set absorption to 0 before damage resolves
- `onMobRemoved()`: strip remaining absorption granted by this effect
- `shouldApplyEffectTickThisTick()`: return false (event-driven only)

**Explosion detection:** Check `source.is(DamageTypeTags.IS_EXPLOSION)`.

**Tracking granted amount:** Store the absorption amount granted in a per-entity WeakHashMap or data attachment, so `onMobRemoved()` only strips what Stoneskin added (not absorption from other sources like golden apples).

### Testable Seams

```java
static float stoneHeartAmount(float maxHealth, float currentHealth) {
    return Math.max(0, maxHealth - currentHealth);
}

static boolean isExplosionDamage(DamageSource source) {
    return source.is(DamageTypeTags.IS_EXPLOSION);
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `stoneHearts_fullHealth` | `stoneHeartAmount(20, 20)` == 0 |
| `stoneHearts_halfHealth` | `stoneHeartAmount(20, 10)` == 10 |
| `stoneHearts_oneHP` | `stoneHeartAmount(20, 1)` == 19 |
| `stoneHearts_noNegative` | `stoneHeartAmount(20, 25)` == 0 (capped at 0, can't go negative) |

## Rendering

- Stone heart sprites at absorption heart positions via Infra B
- Texture: `assets/goo/textures/gui/stone_heart.png` (gray, rocky heart)
- On explosion: stone-crumble particle burst

## Complexity

Medium — straightforward absorption + single event hook. Rendering depends on Infra B.
