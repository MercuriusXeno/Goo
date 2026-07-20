---
id: DESIGN-TESTING
title: Two-phase seam extraction strategy for unit-testing block entities
type: documentation
status: approved
author: architect
consumers: [tech-lead, dev, pm]
created: 2026-03-18
updated: 2026-03-21
---

# Testing Design

## Two-phase seam extraction

Minecraft's API is concrete classes all the way down. `Level`, `BlockEntity`, `BlockState` -
none of them are interfaces. Any class that calls `this.getLevel()` directly can't be
instantiated in a unit test without bootstrapping the entire Minecraft registry.

### Phase 1: SHELTERED_LOGIC - extract decisions

A method mixes framework calls with business decisions. Extract the decision into a pure
function that takes the framework state as parameters.

**Before** (decision sheltered in framework):
```java
void markDirtyAndSync() {
    setChanged();
    Level level = getLevel();
    if (level != null && !level.isClientSide()) {
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }
}
```

**After step 1** (decision extracted, takes suppliers):
```java
void markDirtyAndSync() {
    doSync(this::getLevel, this::getBlockPos, this::getBlockState);
}

static void doSync(Supplier<Level> levelSupplier,
        Supplier<BlockPos> posSupplier, Supplier<BlockState> stateSupplier) {
    Level level = levelSupplier.get();
    if (level != null && !level.isClientSide()) {
        level.sendBlockUpdated(posSupplier.get(), stateSupplier.get(),
            stateSupplier.get(), Block.UPDATE_CLIENTS);
    }
}
```

Now `doSync` is testable - hand in lambdas returning test values.

**Detection:** JaCoCo + Checkstyle intersection. High instruction count, low coverage,
high tool warnings. The class has logic that can't be reached by tests.

### Phase 2: MOCK_BLOCK - name the shared contract

After phase 1 extractions across multiple classes, a pattern emerges:
`CrucibleBlockEntity.doSync()`, `VatBlockEntity.doSync()`, `CanisterBlockEntity.doSync()`
all take `Supplier<Level>`, `Supplier<BlockPos>`, `Supplier<BlockState>`.

Those methods don't know they share a contract, but they do. Name it.

**The pattern:**
```java
// CrucibleBlockEntity
static void doSync(Supplier<Level> level, Supplier<BlockPos> pos, Supplier<BlockState> state) { ... }

// VatBlockEntity
static void doSync(Supplier<Level> level, Supplier<BlockPos> pos, Supplier<BlockState> state) { ... }

// The shared shape becomes:
interface ISyncContext {
    Level level();
    BlockPos pos();
    BlockState state();
}

// Both become:
static void doSync(ISyncContext ctx) { ... }
```

Every BlockEntity that needs sync implements `ISyncContext`. Tests provide a trivial impl.
The interface isn't born from one class's convenience - it's born from the dependency shape
repeating across classes.

**Detection:** Cross=Y. After SHELTERED_LOGIC extractions, compare method signatures across
classes. Methods that take the same supplier shapes share an unnamed contract.

### Common framework dependencies

These repeat across block entities and are candidates for shared interfaces:

- `Level` access (`getLevel()`, `isClientSide()`)
- Block state (`getBlockState()`)
- Position (`getBlockPos()`)
- Dirty/sync (`setChanged()`, `sendBlockUpdated()`)
- Neighbor queries (`getBlockEntity(pos)`)
- Scheduling (`scheduleTick()`)

### Progression

1. **Phase 1** (done): extract logic seams - math, predicates, dispatch (SHELTERED_LOGIC)
2. **Phase 2** (next): extract dependency seams - wrap framework accessors as suppliers,
   then group into named interfaces when patterns emerge (MOCK_BLOCK)
3. **Result**: framework subclasses become thin wiring. Logic lives in testable collaborators
   backed by contracts that the framework implements.
