---
id: subplan-typhoon
title: "Typhoon -- Jet Stream Launcher"
type: subplan
parent: plan-world-effects
phase: 3
status: proposed
created: 2026-04-03
updated: 2026-04-03
purpose: Single-use launch pad that propels entities upward. Stacks up to 5 for exponential height.
---

# Typhoon -- Jet Stream Launcher

## Design Source

DESIGN-TYPES: "Creates a jet stream that lasts a single use. Stacks up to 5. Propulsion ~2^n meters (overcomes gravity extra when pointing up)."

## Pattern

**Unique block.** Place a `JetStreamBlock` that launches entities on contact, then is consumed. Stackable for exponential propulsion.

## Parameters

| Field | Value |
|---|---|
| Max stacks | 5 |
| Propulsion | 2^stacks blocks upward (2, 4, 8, 16, 32) |
| Uses | 1 (consumed on first entity contact) |
| BER | Slime-core orb, typhoon-tinted, breathes, aim-highlight |
| Ambient particles | CLOUD (upward drift) |

## Architecture

### New: `block/JetStreamBlock.java` + `block/JetStreamBlockEntity.java`

Persistent until triggered. When any entity enters the block's collision zone (via `entityInside` or BE tick scanning), apply upward velocity and consume the block.

- `initJet(int stacks)` -- sets propulsion power.
- `tryStack()` -- increments stacks up to 5.
- `entityInside()` or `serverTick()` with entity scan -- on first entity contact, apply velocity, remove block.

### Propulsion formula

Upward velocity = `2^stacks * 0.5` blocks/tick (tunable). The design says "2^n meters" which translates to roughly `2^n / 4` blocks/tick initial velocity to reach that apex height against gravity.

Add `EffectMath.computeJetVelocity(int stacks)` -- pure math, testable.

### Single-use semantics

The block removes itself after launching. Unlike pulsing nodes (budget-based) or fields (duration-based), this is trigger-based: one launch, done.

## Testable Seams

- `computeJetVelocity(stacks)` in `EffectMath`.
- Height math: `v^2 / (2*g)` should approximate 2^n blocks.

## Verification

- Throw 1 typhoon blob -> jet pad, step on it -> launched ~2 blocks up, pad vanishes.
- Stack 5 -> launched ~32 blocks up. Works on mobs too.
