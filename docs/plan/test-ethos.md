---
id: test-ethos
title: Goo testing philosophy and verification toolchain
type: test-ethos
status: approved
author: test-architect
consumers: [dev, qa]
created: 2026-03-18
updated: 2026-03-21
---

# Test Ethos

## Framework

JUnit 5 (JUnit Platform) via Gradle. JDK 21. Mockito for mocks, Instancio for test data generation, ArchUnit for convention enforcement.

## Command

```
./gradlew gooTest
```

Custom Gradle task that runs pure unit tests without bootstrapping a Minecraft server. The standard `test` task also works but `gooTest` is preferred - it's explicit about what it does.

## Verification Toolchain

```
./gradlew gooTest              # Unit tests + ArchUnit conventions
./gradlew checkstyleMain       # Source-level smell detection
./gradlew spotbugsMain         # Bytecode bug detection
./gradlew jacocoGooTestReport  # Coverage report (testable code only)
```

Reports:
- Checkstyle: `build/reports/checkstyle/main.xml` (and `.html`)
- SpotBugs: `build/reports/spotbugs/main.xml` (and `.html`)
- JaCoCo: `build/reports/jacoco/jacocoGooTestReport/jacocoGooTestReport.csv` (and `.html`)
- ArchUnit: pass/fail in test output

JaCoCo excludes framework-coupled packages (client, command, tools, effect, mixin, fluid, registry, network, entity) so the coverage score reflects testable code only.

## Tools

- **Mockito**: mock BlockState, BlockEntity, Level, BlockHitResult - whatever you need. Mock interfaces, abstract classes, Minecraft types. If a seam exists, it's testable.
- **Instancio**: generate test data (UUIDs, GasketPartners, GooContents, etc). Use `Instancio.create(Class)` for random instances, `Instancio.of(Class).set(...)` for controlled generation.
- **ArchUnit**: enforce code conventions as tests. Rules live in `ConventionTest.java`. Violations fail the build.
- **Checkstyle**: source-level smell detection. Delegated smells from `$SMELLS`: IMPORT_DISORDER, LONG_METHOD, MAGIC, MISSING_DOCS, plus tripwires for GREEDY_PARAM, RAW_PREDICATE, TANGLED_CONCERNS. Config in `config/checkstyle/checkstyle.xml`.
- **SpotBugs**: bytecode analysis for real bugs (null deref, impossible casts, dead stores, resource leaks). Delegated smell: DEAD_CODE (partial).
- **JaCoCo**: coverage reporting. Delegated smell: UNTESTED (identifies gaps, judgment on what to test is still manual).

## Philosophy

- **Test the seam, not the orchestrator.** Pure functions and extracted logic methods are the primary test targets. If a method is worth extracting (SRP), it's worth testing. Orchestrators get tested indirectly: if every seam is proven correct, the orchestrator's job is just wiring.
- **Mock what you need.** Use Mockito to isolate the unit under test. Mock Minecraft types (BlockState, Level, BlockHitResult) freely - they're infrastructure, not behavior.
- **Instancio for data.** Use Instancio to generate test inputs instead of hand-crafting every UUID, record, or data object. Override specific fields when the test cares about them.
- **Test names describe behavior, not implementation.** `emptyContentsIsEmpty` not `testGetIsEmptyReturnsTrue`. Use `camelCase` method names, not `snake_case`.
- **Class-level Javadoc on test classes.** One sentence describing what the test covers and any important constraints.
- **Package-private test classes.** No `public` modifier on test classes or methods - JUnit 5 doesn't need it.
- **`@Nested` for logical grouping** when a test class covers multiple distinct behaviors of the same subject.
- **Test helpers are real classes** (e.g. `TestRecipeBuilder`), not inherited fixtures. Composition over inheritance.

## Constraints

- Tests must never touch the filesystem, network, or system clock.
- Tests must never depend on execution order.
- Tests must run in < 5 seconds total (currently ~1s). If a test is slow, the code under test needs refactoring, not the test.
- Never `@Disabled` a test to make the suite pass. Fix it or delete it.
