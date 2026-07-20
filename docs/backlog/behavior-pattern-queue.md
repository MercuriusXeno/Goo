# Behavior pattern queue

Per-pattern branches, decided 2026-05-02. Patterns are not symmetric; each gets its own branch, named after the pattern, shipping only that pattern.

## Shipped

- block-break (Rock + Blaze): PR #27, branch `block-break-decomposition`. `BlockEffect` / `LayerVisuals` / `LayerAudio` registries.
- block-place (Glow + Ender): PR #29, branch `block-place-decomposition`. `BlockPlacer` + `BlockPlaceBehavior`.

## Remaining

### field-effect-with-controller (Metal + Crystal)

- Metal: `MetalBehavior` (spike trap, persistent controller until charges depleted).
- Crystal: `CrystalBehavior` (shard cloud, persistent controller until DoT budget depleted).
- The marker block IS the controller in both. Decompose: spawn pattern (radial spikes vs suspended slivers), trigger condition (entity intersects volume), effect-per-trigger (impale damage vs bleed tick), charge/budget bookkeeping, depletion teardown.
- `allowsTopOff()` already lives on `ChainBehavior`; verify the seam covers what the data shape needs.

### phased-state-machine (Nether)

- `NetherBehavior` is the only occupant (black hole). Suspected two phases: implode-pull then dissolve.
- Lowest priority. One occupant + unique mechanics = highest premature-parameterization risk. Re-evaluate whether decomposition is warranted, or leave it a single hand-written `ChainBehavior` and document the pattern name for any future second occupant.

## Sequencing

field-effect-with-controller first (more state than shipped patterns); phased-state-machine last and possibly skipped.

## Constraints

- Never extract abstract bases (see Hard Rules in CLAUDE.md). Mirror the data-driven block-break shape for whichever axes apply.
