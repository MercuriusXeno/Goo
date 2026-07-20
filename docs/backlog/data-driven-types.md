# Data-driven goo types

Convert the `GooType` enum to a data-driven registry with per-type properties. Planned, deferred. Triggered by an artist request for per-type light emission on canisters.

`GooType` is the last hardcoded piece; abilities and values are already data-driven. Adding properties like `light_level` currently requires enum code changes.

## Plan

- Branch from trunk when this starts.
- Static constants stay for internal use (like `Blocks.STONE`).
- `EnumMap` becomes `Map` everywhere.
- First new field: `light_level` (int 0-15).
- Goal file: `$KB/goo/work/goal-data-driven-goo-types.md`.
