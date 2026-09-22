# ADR-023. The Kotlin bridge lives inside `:legacy-catalog`, which is Java *and* Kotlin

Status: Accepted (2026-09-22) — amends [ADR-007](ADR-007-java-interop-module.md)

## Context

ADR-007 asks for two things at once: a **plain** `java-library` module, and a unit test **written in
Java** that calls the Kotlin adapter. Those two cannot both hold.

The adapter ADR-007 describes lives in `:shared:data`'s `androidMain`. That source set is a variant
of a KMP Android library: a plain JVM module cannot put its classes on a test classpath, and it
should not want to — the dependency runs `:legacy-catalog` → `:shared:data`, which is the exact edge
ADR-007's own guardrail exists to forbid. A Java file cannot live in the KMP Android module either:
`com.android.kotlin.multiplatform.library` compiles Kotlin source sets only.

So the Java test can only call Kotlin that is compiled into `:legacy-catalog` itself.

## Decision Drivers

- The reverse direction of the interop (Java calling Kotlin) has to be proven by a compiler, not by
  a comment: that is the whole point of writing the test in Java.
- The guardrail must stay true: nothing may depend on `:legacy-catalog` except `:shared:data`.
- The domain contract must keep its Kotlin adapter where the architecture puts partner code —
  `:shared:data`, never in a module that stands in for a partner's own library.

## Considered Options

1. **Java test inside `:shared:data`'s `androidHostTest`.** Not possible: the KMP Android plugin has
   no Java source sets.
2. **`:legacy-catalog`'s test depends back on `:shared:data`.** Rejected: it inverts the boundary
   ADR-007 guards, and an Android variant is not resolvable from a JVM module anyway.
3. **Give `:shared:domain` a JVM target so the legacy module can implement the contract directly.**
   Rejected: a new target on the purest module of the build, to serve one test.
4. **`:legacy-catalog` carries both plugins.** Java holds the simulated partner SDK; Kotlin holds
   the single bridge the app calls, which is also what the Java test calls back into.

## Decision

Option 4. `:legacy-catalog` applies `java-library` **and** `org.jetbrains.kotlin.jvm`:

- `src/main/java` — `PartnerCatalog`, `UnknownCodeException`: the library a partner ships (static
  factory, builder, overloads, checked exception).
- `src/main/kotlin` — `CatalogBridge`: the one type the app calls, and the Kotlin API the Java test
  calls back into (`@JvmStatic`, `@JvmOverloads`, `@JvmField`, `@Throws`, platform-type nullability
  handled where it enters Kotlin).
- `src/test/java` — `LegacyCatalogInteropTest`, in Java, so dropping any of those annotations breaks
  the build rather than a convention.

`LegacyModelCatalog`, the adapter that implements the domain's `ModelCatalog`, stays in
`:shared:data`'s `androidMain` exactly as ADR-007 specifies. It is also still the kill switch:
deleting the module and that class restores the raw codes everywhere.

## Consequences

- (+) Both directions of the interop are proven by compilation and by tests that run on the JVM in
  seconds — `./gradlew :legacy-catalog:test`, now a CI step of its own.
- (+) The module boundary is unchanged: `:legacy-catalog` still depends on nothing of this app's.
- (−) A module named "legacy Java catalogue" contains one Kotlin file. The plugin block and this ADR
  are what explain why; the Java SDK it wraps is still pure Java.
- (−) The bridge's `label(code, fallback)` overload exists for Java callers rather than for the app,
  which uses `strictLabel` and maps the exception itself. That is the interop being demonstrated,
  and the Java test is what exercises it.

## Confirmation

- `./gradlew :legacy-catalog:test` runs a Java test against Kotlin code (5 tests).
- `LegacyCatalogBoundaryTest` in `:konture-test` fails if `:shared:domain` or `:shared:app` ever
  declares a dependency on `:legacy-catalog`, or if `:legacy-catalog` declares one on this app.
- `:shared:app:compileKotlinIosSimulatorArm64` proves the iOS path still links without the module
  (ADR-013).
