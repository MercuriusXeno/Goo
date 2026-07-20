---
id: plan-effects-5-mycelial-network
title: "Effect 5: Shroom — Mycelial Network"
type: plan
status: approved
parent: plan-effects
created: 2026-03-30
updated: 2026-03-30
---

# Effect 5: Shroom — Mycelial Network

**Class:** `effect/brew/MycelialNetworkEffect.java` | **Category:** BENEFICIAL | **Color:** `0x8E44AD`

## Design Spec

> "See mycelial nodes around shroom blocks (16m * LVL, through walls). Shift+look to warp. Each jump halves duration."
> — DESIGN-TYPES.md, Shroom brew

## Inconsistencies

| # | Issue | Resolution |
|---|---|---|
| 1 | "Mycelial nodes" — which blocks count? | Block tag `goo:mycelial_network_nodes`: mycelium, red/brown mushroom blocks, crimson/warped stems, nether wart blocks, shroomlight, huge mushroom blocks. |
| 2 | "Shift+look to warp" — range limit? | Warp range = detection range (16*LVL). Must be looking at a detected node. |
| 3 | Warp cooldown? | 40 ticks (2 seconds). Prevents spam-teleporting. |
| 4 | Through walls for warp? | Yes. Through-wall detection is the point. Warp follows the same principle. |
| 5 | "Each jump halves duration" — normal jumps only, or warps too? | Normal jumps only. Warps have their own cost (cooldown + requiring a valid node). |
| 6 | What if the destination node has no free space above it? | Search upward from the node's top surface for the first non-solid block. Fail if no space within 3 blocks. |

## Prerequisites

- Infra A (foundation)
- Infra D (Jump Override: `LivingEvent.Jump` handler)
- Infra E (Block Scanning, adapted for block tags)

## Implementation

### Files to Create

| File | Purpose |
|---|---|
| `effect/brew/MycelialNetworkEffect.java` | Server: scan nodes, handle warp, halve duration on jump |
| `network/WarpRequestPayload.java` | Client→server packet: "warp to this BlockPos" |
| `test/.../effect/brew/MycelialNetworkEffectTest.java` | Pure-function tests |

### Files to Modify

| File | Change |
|---|---|
| `registry/GooBrewEffects.java` | Add MYCELIAL_NETWORK holder |
| `registry/GooPotions.java` | SHROOM case: use custom effect |
| `en_us.json` | Add `"effect.goo.mycelial_network": "Mycelial Network"` |
| `client/render/BlockScanRenderer.java` | Add node-scanning mode (reuse Infra E with different tag) |
| `effect/brew/JumpOverrideHandler.java` | Add case for Mycelial Network: halve duration |

### Server-Side Logic

**Node scanning:** Every 20 ticks, scan sphere of radius `scanRadius(amplifier)` for tagged blocks. Store found positions in a synced data structure so the client can render them.

**Warp handling:**
1. Client detects: player sneaking + looking at a rendered node → sends `WarpRequestPayload(targetPos)`
2. Server validates: effect active, target is a valid node, within range, cooldown expired
3. Server teleports: player to top of target block + 1, plays ender teleport sound
4. Server records: last warp tick for cooldown

**Jump halving:** In `JumpOverrideHandler`, if entity has Mycelial Network, get the MobEffectInstance and set duration to `halveDuration(current)`. Do NOT cancel the jump — the player still jumps, but loses half the remaining effect time.

### Client-Side Logic

- Reuse `BlockScanRenderer` with tag = `goo:mycelial_network_nodes`
- Render purple spore particles at detected node positions (through walls)
- When sneaking and looking at a node within range: render a "warp target" indicator (brighter particles, pulsing)
- On sneak+look confirmation (e.g. right-click while sneaking, or just sneak+look for 0.5s): send WarpRequestPayload

### Testable Seams

```java
static int scanRadius(int amplifier) { return 16 * level(amplifier); }
static int halveDuration(int currentDuration) { return currentDuration / 2; }
static boolean warpCooldownExpired(long lastWarpTick, long currentTick) {
    return (currentTick - lastWarpTick) >= 40;
}
```

## Test Plan

| Test | Assertion |
|---|---|
| `scanRadius_level1` | `scanRadius(0)` == 16 |
| `scanRadius_level3` | `scanRadius(2)` == 48 |
| `halveDuration_1200` | `halveDuration(1200)` == 600 |
| `halveDuration_1` | `halveDuration(1)` == 0 |
| `warpCooldown_expired` | `warpCooldownExpired(0, 40)` == true |
| `warpCooldown_notExpired` | `warpCooldownExpired(0, 39)` == false |

## Rendering

- Purple spore particles at node positions (through walls)
- Warp target indicator: brighter/pulsing particles when sneaking + looking at a node
- Teleport particles + sound on warp

## Complexity

Hard — client-server sync for node positions, custom packet for warp requests, jump duration manipulation, block tag scanning.
