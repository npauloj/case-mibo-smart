# ADR-007. Honest Java interop: one small Java module consumed from Kotlin

Status: Proposed (2026-09-20) — scheduled for wave 3; first item to cut if time runs out

## Context

Java appears twice in the brief: "Utilização da linguagem de programação Java" (★, lowest-weight
deliverable) and "interoperabilidade com Java" inside the 25% technical-knowledge axis. The stack is
otherwise 100% Kotlin/KMP (★★★). Writing production code in Java for its own sake would lower code
quality and contradict the KMP choice; ignoring Java leaves a named sub-criterion unanswered.

## Decision Drivers

- Demonstrate real Kotlin↔Java interop knowledge with minimal surface.
- Keep `:shared:domain` and `:shared:app` pure Kotlin/multiplatform (Java cannot be in `commonMain`).
- Fit the "múltiplos parceiros" narrative.

## Considered Options

1. No Java at all; answer the interop question verbally.
2. Rewrite a platform piece (e.g. the Android player wrapper) in Java.
3. A small JVM-only Java module `:legacy-catalog` simulating a legacy partner SDK — e.g. a device
   model-code catalogue or a lock-event formatter — consumed by `:shared:data`'s `androidMain`/JVM
   source set through a Kotlin adapter that implements a domain contract.

## Decision

Option 3, if wave 3 is reached.

- `:legacy-catalog` (plain `java-library`): 2–3 classes with a checked exception, a static factory,
  overloaded methods and a builder — the shapes that exercise interop.
- Kotlin side: an adapter in `:shared:data/androidMain` using `@JvmStatic`, `@JvmOverloads`,
  `@Throws`, `@JvmField` where Kotlin exposes API back to Java, and null-safety handling of platform
  types; a unit test written **in Java** calling the Kotlin adapter proves the reverse direction.
- Used for one visible feature only: friendly model names / event labels on the lock history screen.

## Consequences

- (+) Both directions of interop are demonstrated and testable; the ★ item is earned honestly.
- (−) A JVM-only dependency means the iOS build takes a different path for that feature (an `expect`
  with a Kotlin `actual` on iOS) — small, but it must be kept in mind.
- (−) One more module; cut first under time pressure.

## Confirmation

- `./gradlew :legacy-catalog:test` runs a Java test against Kotlin code; `:shared:data` Android tests
  call the Java classes through the adapter.
- `rg "@JvmStatic|@JvmOverloads|@Throws" shared/data/src/androidMain` matches.

## Guardrail

- Guardrail: module rule — `legacy-catalog is only consumed by the Android app` in `:konture-test`
  (`Konture.modules { that().haveNamePath(":legacy-catalog") … }` asserting that no module other
  than `:androidApp` — and the JVM/Android adapter path it wires — declares a dependency on it, and
  that `:shared:domain` / `:shared:app` never do). Added to the suite when the module exists.
