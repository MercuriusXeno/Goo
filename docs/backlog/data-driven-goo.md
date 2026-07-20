# Data-driven goo behaviors

Major refactor goal: make goo abilities data-driven with configurable costs and codified behavior patterns. The current system hardcodes each goo type's behavior as a unique `ChainBehavior` implementation with fixed blob costs.

Premise: goo types have inherently unequal value (plenty, effect strength). Homogenizing 1-blob-of-X = 1-blob-of-Y is flawed; abilities should take configurable goo amounts, not fixed blob counts.

## Approach

- Codify behavior patterns (block-break, block-place, field-effect, controller-entity) separately from parameters (radius, duration, damage, cost).
- Block-break: rock (silk) and blaze (smelt) are one pattern with different break modes.
- Block-place: glow and ender place blocks as their ability.
- Field-effect with controller: metal (spike trap) and crystal (shard cloud) use the marker block as a persistent controller.
- Nether (black hole) is a phased state machine.
- Parameters become data-driven (datapack JSON or config), including goo cost per ability.
- The shared `ChainMarkerBlock`/BE system is overloaded and needs cleanup.

## Sequence

Clean up existing architecture first, then add remaining abilities, then brewing. No new hardcoded abilities before the cleanup.

See also: `behavior-pattern-queue.md` for per-pattern branch status.
