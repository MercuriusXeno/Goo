# Reactor gasket consistency

Queued for a dedicated branch. The reactor's output canister has gasket parity gaps versus other canister-host blocks (it was added later and inherited partial canister/gasket plumbing):

- Push-through: the output canister does not push fluid through its gaskets like hub/canister/vat slots do.
- Choral gasket rendering: choral gaskets on the reactor's output slot are not rendered by `ReactorBlockEntityRenderer` (only copper endcaps appear).
- Right-click transmission: choral gaskets on the reactor do not transmit right-click interactions.

## Scope

Gasket parity only:

1. Wire choral gasket rendering and right-click transmission via the existing `IGasketHolder` paths the canister uses.
2. Hook the reactor output slot into the gasket pusher pipeline.

Fluid rendering is unrelated (see `canister-render-consolidation.md`).
