# Canister/fluid render consolidation

Canister-body and goo-fluid rendering is fragmented across hub/canister/tap/reactor BERs. Shared geometry exists (`SlotFluidGeometry`, `SlottedFluidContainer.submitFluids`) but every BER carries its own:

- canister-body submit method, each picking its own render type (some used `entityCutout` even though the canister must be translucent so fluid shows)
- per-machine fluid submit method that re-creates the `RenderContext`, looks up the fluid sprite, and calls into the geometry helpers

Symptom already hit: `HubFluidRenderer.submitBodies` and `TapBlockEntityRenderer.submitBody` used `entityCutout(CANISTER_SIDE)`, hiding fluid, while `ReactorBlockEntityRenderer.submitBody` correctly used `entityTranslucent`. Cutouts were swapped to translucent on `goo-emissive-light`, but the duplication remains and drift (render type, light coords, sprite lookup, tint) keeps accumulating.

## Plan

Focused refactor branch. Pull canister-body submission and goo-fluid submission into a single shared entry point taking (pose, collector, slot data, light coords, fullbright flag). Every host calls the same method; render type, lightmap value, and sprite resolution live in exactly one place. Reactor's output canister (single slot in a hollow of an otherwise opaque block) is the test case; if the shared API hosts it cleanly, hub/canister/tap fall in trivially.
