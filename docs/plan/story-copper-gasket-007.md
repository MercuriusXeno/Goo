---
id: story-copper-gasket-007
title: Update KB documentation for copper fittings model
type: user-story
status: proposed
author: project-manager
consumers: [pilot]
source: copper-fittings-plan (Phase 7)
depends-on: [story-copper-gasket-001, story-copper-gasket-002, story-copper-gasket-003, story-copper-gasket-004, story-copper-gasket-005, story-copper-gasket-006]
created: 2026-03-30
updated: 2026-03-30
---

# Update KB documentation for copper fittings model

## Problem

Multiple KB documents describe gasket behaviors on Hub, Canister, Tap, and Plexer as though gaskets are universal/default features. After the copper fittings rework, gaskets are opt-in upgrades and copper fittings are the default. Documentation must reflect the new two-tier model: copper fittings (early game, physical) vs choral gaskets (late game, remote).

## Acceptance Criteria

- `plan/DESIGN-MACHINES.md`: All recipe legends match new copper ingredients. Hub/Canister/Tap/Plexer sections describe copper fitting as default. Gasket described as optional upgrade. Choral gasket yield = 1. Canister placement rules documented. Plexer constrained grid documented.
- `docs/gasket-network.md`: `GASKET_BLOCK` registration table updated (add Tap, Plexer). Note that Hub/Canister gasket UUIDs are opt-in (only exist after gasket installation). Document copper fitting vs gasket mutual exclusivity.
- `docs/machines.md`: Per-machine gasket role updated to "copper fitting (default), choral gasket (upgrade)". Tap and plexer sections include gasket upgrade path.
- `plan/DESIGN-CAPABILITIES.md`: Update GASKET_BLOCK table to include TapBE and PlexerBE. Note that copper fittings don't use capabilities (physical adjacency only).
- `plan/DESIGN-RENDERING.md`: Note rendering distinction between copper fitting state and gasket overlay state (if any rendering changes were made).
- No documentation references gasket behaviors as universal/inherent on Hub, Canister, Tap, or Plexer.

## Scope

**In scope:** All KB docs in `$KB/goo/` that reference gaskets on the changed blocks.

**Out of scope:** Code changes (all done in prior stories). In-code Javadoc updates (done inline with each story).

## Notes

Run last, after code stabilizes. Verify against actual implemented behavior, not the plan — stories may have evolved during implementation.
