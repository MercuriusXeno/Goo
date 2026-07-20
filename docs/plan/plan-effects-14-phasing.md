---
id: plan-effects-14-phasing
title: "Effect 14: Ender — Phasing"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 14: Ender — Phasing

**Class:** `effect/brew/PhasingEffect.java` | **Category:** BENEFICIAL | **Color:** `0x00CED1`

## Design Spec

> "Phasing - blink toward cursor instead of jumping. Each jump halves duration. Range: 16 blocks * potion strength."
> — DESIGN-TYPES.md, Ender brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Instead of jumping" — full override? No vertical at all? | Full override. Jump key triggers blink, not a vertical jump. Player can still fall and take fall damage normally. |
| 2 | Can you blink through walls? | Yes. That's "Phasing". The core fantasy is passing through solid matter. |
| 3 | Target inside solid block — what happens? | Search backward along the ray for the last valid (non-suffocating) position. If the entire ray is solid, blink fails — no teleport, no duration cost. |
| 4 | "Potion strength" = amplifier + 1? | Yes. Range = `16 * (amplifier + 1)`. LVL 1 = 16, LVL 2 = 32, LVL 3 = 48. |
| 5 | Does blink work vertically (looking up/down)? | Yes. Blink follows the look vector in 3D. Looking straight up = blink upward. Looking at an angle = diagonal blink. |
| 6 | Can you blink into the void? | No. Minimum Y = world min build height. Blink clamped. |
| 7 | Does fall damage apply after blinking to a high position then falling? | Yes. Blink doesn't grant fall immunity. The player arrives with zero velocity and falls normally from there. |

## Prerequisites

- Infra A (foundation)
- Infra D (Jump Override: `LivingEvent.Jump` interception)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/PhasingEffect.java` | Jump interception + ray march + teleport |
| `effect/brew/BlinkMath.java` | Pure ray-march and destination-finding logic |
| `test/.../effect/brew/BlinkMathTest.java` | Ray march tests with mock BlockGetter |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add PHASING holder |
| `registry/GooPotions.java` | ENDER case: use custom effect |
| `en_us.json` | Add `"effect.goo.phasing": "Phasing"` |
| `effect/brew/JumpOverrideHandler.java` | Add Phasing case: cancel jump, perform blink |

### Jump Override Flow

1. `LivingEvent.Jump` fires
2. `JumpOverrideHandler` checks: does entity have Phasing?
3. If yes: cancel the jump event (`event.setCanceled(true)`)
4. Call `PhasingEffect.performBlink(entity, amplifier)`
5. Blink: ray march → find destination → teleport → halve duration

### BlinkMath (pure, testable)

```java
/** Finds the blink destination along a ray. */
static BlockPos findLandingPosition(Vec3 start, Vec3 direction, double range, BlockGetter level) {
    // March along the ray in 0.5-block steps
    // Find the furthest position that is:
    //   1. Within range
    //   2. Not suffocating (has space for the player)
    // If the ray exits solid into air, that's the landing spot
    // If the ray is entirely air, land at max range
    // If the ray is entirely solid, return null (blink fails)
}

static boolean isValidDestination(BlockPos pos, BlockGetter level) {
    // Check 2-block-tall space is non-suffocating
    BlockState feet = level.getBlockState(pos);
    BlockState head = level.getBlockState(pos.above());
    return !feet.isSuffocating(level, pos) && !head.isSuffocating(level, pos.above());
}

static double blinkRange(int amplifier) {
    return 16.0 * level(amplifier);
}

static int halveDuration(int currentDuration) {
    return currentDuration / 2;
}
```

### PhasingEffect

**MobEffect overrides:**
- `shouldApplyEffectTickThisTick()`: return false (event-driven via jump)
- No tick logic needed — all behavior is in the jump override handler

**Blink execution (in JumpOverrideHandler):**
```java
static void performBlink(LivingEntity entity, int amplifier) {
    Vec3 start = entity.getEyePosition();
    Vec3 direction = entity.getLookAngle();
    double range = BlinkMath.blinkRange(amplifier);
    BlockPos destination = BlinkMath.findLandingPosition(start, direction, range, entity.level());
    if (destination == null) return; // blink fails
    entity.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
    entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
        SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    // Halve duration
    MobEffectInstance instance = entity.getEffect(GooBrewEffects.PHASING);
    if (instance != null) {
        // Manipulate remaining duration
    }
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `blinkRange_level1` | `blinkRange(0)` == 16.0 |
| `blinkRange_level2` | `blinkRange(1)` == 32.0 |
| `halveDuration_1200` | `halveDuration(1200)` == 600 |
| `halveDuration_1` | `halveDuration(1)` == 0 |
| `isValidDest_airAir` | Two air blocks = true |
| `isValidDest_solidAir` | Solid + air = false (feet in solid) |
| `isValidDest_airSolid` | Air + solid = false (head in solid) |
| `findLanding_allAir` | Returns position at max range |
| `findLanding_allSolid` | Returns null (blink fails) |
| `findLanding_exitSolid` | Returns first air position after solid segment |

## Rendering

- Ender particle trail along the blink path (spawn particles at intervals along the ray)
- Teleport sound (vanilla enderman teleport)
- Brief screen-flash or FOV shift on blink (client-side)

## Complexity

Hard — jump override, 3D ray marching through blocks, teleportation safety validation, duration manipulation. The ray march is the core challenge but is fully testable as pure geometry.
