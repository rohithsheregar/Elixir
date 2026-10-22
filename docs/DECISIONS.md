# DECISIONS.md — Elixir Project

Non-obvious choices are recorded here with rationale and alternatives considered.

---

## D1 — Keep package name com.cellclaw until M12
**Choice:** Keep `com.cellclaw` as the applicationId and Kotlin package until milestone M12.
**Why:** Renaming the package mid-build breaks all Hilt DI wiring, manifest component references, Room DB migrations, and asset paths simultaneously. Doing it at the end in M12 (when all other code is stable) is far safer.
**Alternatives:** Rename at start — rejected because it would break the existing build before we even have a baseline.

---

## D2 — Disable risky tools by removing from ToolRegistry, not deleting source files
**Choice:** Remove SMS, calls, camera, location, contacts, and shell tools from AppModule's ToolRegistry registration for the Elixir profile. Keep the `.kt` source files intact.
**Why:** Per AGENTS.md §5: "disable (do not delete) risky tools." If the user ever wants the original CellClaw functionality back (or on a different profile), the code is still there. Only removing from registry means the AI agent simply cannot invoke them.
**Alternatives:** Delete source files — rejected (irreversible, violates AGENTS.md). Add a feature flag — would work but adds complexity; registry removal is simpler and sufficient at this stage.

---

## D3 — Build fix needed: app/build.gradle.kts uses deprecated Gradle 9 APIs
**Choice:** Fix deprecated `kotlinOptions`, `aaptOptions`/`noCompress`, and unresolved `exec`/`commandLine` in the `enableAccessibility` task as part of M0.2.
**Why:** The build fails entirely with Gradle 9.7.1. These are known deprecations. The `exec` / `commandLine` references inside a `tasks.register` block require the correct Gradle DSL (ExecOperations injection or use of `project.exec`).
**Alternatives:** Downgrade Gradle — rejected because gradle-wrapper.properties was recently updated to 9.7.1 by user. Fix the code to be compatible is the right path.
**Reality note:** Previous build_err.txt showed `JAVA_HOME is not set` but Java 23 is present. The gradle-wrapper.properties was modified (in git status) — this was the Gradle upgrade that likely exposed these deprecation errors.

---

## D4 — Profile encryption: EncryptedSharedPreferences over plain Room
**Choice:** Store candidate profile JSON in EncryptedSharedPreferences (backed by Android Keystore) rather than in a plain Room database.
**Why:** The profile contains personal data (CGPA, marks, contact info). EncryptedSharedPreferences uses the Keystore-backed AES-256-GCM key automatically; it's the simplest approach that gives hardware-backed encryption without needing to implement custom SQLCipher or Room encryption. Profile data is not large enough to need relational queries.
**Alternatives:** Room + SQLCipher — more complex, third-party dep; Room plain — unencrypted, violates skill file §7; Keystore + custom JSON file — more code for same result.

---

## D6 — kotlinOptions + aaptOptions: keep with @Suppress rather than migrate
**Choice:** Keep `kotlinOptions { jvmTarget = "17" }` and `androidResources { noCompress += listOf("tflite") }` with `@Suppress("DEPRECATION")` rather than migrating to `compilerOptions {}`.
**Why:** AGP 8.7.3 does NOT expose `compilerOptions {}` as a top-level block inside `android {}` in the Kotlin DSL. Using it caused "Unresolved reference 'compilerOptions'" compilation error. The correct migration path is either (a) `kotlin { compilerOptions { jvmTarget.set(...) } }` top-level block or (b) keep `kotlinOptions` — we chose (b) as it is the minimal-risk change and AGP 8.7 treats it as a deprecation warning, not an error.
**Alternatives:** Top-level `kotlin { compilerOptions {} }` — would work but adds complexity. Downgrade Gradle — rejected.

---

## D7 — Session 2 discrepancy: HANDOFF said "build running", reality was "build failed transiently"
**Discrepancy:** Session 1 HANDOFF said M0.2 build was running. Reality (from build log): build failed at `:app:kspDebugKotlin` — KSP dependency download failed with "No such host is known (dl.google.com)". This was a transient DNS failure, not a code error. The build script compiled correctly (tasks ran up to kspDebugKotlin). Network was confirmed up at session 2 start (Test-NetConnection to dl.google.com returned True).
**Fix:** Updated HANDOFF to reflect actual state. Retried build at session 2 start.
**Source of truth:** git log + build log file > HANDOFF claim.

