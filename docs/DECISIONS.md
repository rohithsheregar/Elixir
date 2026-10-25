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

---

## D8 — Fix 6 stale pre-existing CellClaw test failures (M0.4 prerequisite)
**Discrepancy:** 6 tests were failing before any Elixir code was written — stale assertions against old model names and old AutonomyPolicy defaults.
- Provider model names updated upstream: Gemini `gemini-3-flash-preview`, Anthropic `claude-sonnet-4-6`, OpenAI `gpt-5.2`
- `AutonomyPolicy` now inits with `FULL_AUTO` (all AUTO); test expected BALANCED behavior → fixed by calling `applyProfile(BALANCED)` in test
- `app.automate` is in `appControl` set → gets `AUTO` under BALANCED, not ASK
- `getPolicy()` returns `AUTO` for unknown tools under FULL_AUTO
**Fix:** Updated tests to match actual production code. Zero production code changed.
**Source of truth:** actual code per AGENTS.md §0.

---

## D9 — Session 3 verify found a Gradle daemon environment failure
**Discrepancy:** HANDOFF described `testDebugUnitTest` as running, but the required verification command exited 1 before executing tests because Gradle could not establish a loopback connection for its daemon.
**Choice:** The `--no-daemon` retry and a retry with the project JVM arguments overridden failed identically. M0.4 is blocked rather than changing production code or claiming a test pass.
**Source of truth:** Session 3 verification output.

---

## D10 — Work around the Windows Java selector pipe failure per invocation
**Evidence:** JDK 17, 21, 23 and 25 all reproduced `UnixDomainSockets.connect0` with the process's default temporary directory. A selector opened successfully when the JVM received `-Djdk.net.unixdomain.tmpdir=C:\Temp`.
**Choice:** Set `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Temp` only for Gradle commands on this host. This is a build-host workaround, not a project Gradle setting or product-code change, so other environments retain their normal temporary-directory policy.

---

## D11 — Session 8 discrepancy: HANDOFF's last green commit lagged behind M2.1
**Discrepancy:** HANDOFF listed `284fdbe` (M1.2) as the last green commit, although git contains the completed M2.1 commit `87c0b0f` and TESTLOG T12 records its successful focused test, full unit suite, and debug build.
**Fix:** Updated HANDOFF to name `87c0b0f` as the last green commit after the required session-start verification also passed.
**Source of truth:** git history and the successful Gradle verification output, per AGENTS.md §0.

---

## D12 — Use stable Security Crypto 1.0.0 for the planned encrypted preference store
**Choice:** Use `androidx.security:security-crypto:1.0.0` with `MasterKeys` and `EncryptedSharedPreferences` for M2.2.
**Why:** The current 1.1 release deprecates the entire wrapper in favour of direct platform Keystore APIs, but the committed M2.2 plan specifically requires AndroidX Security Crypto and encrypted preferences. Version 1.0.0 is the stable, non-deprecated API line and provides the required Keystore-backed encrypted JSON store.
**Alternatives:** Security Crypto 1.1.0 — rejected because it immediately introduces deprecated APIs. Direct Android Keystore encryption — deferred because it would depart from the current plan; reconsider when the plan is revised.

---

## D13 — Audit remediation added to plan (2026-10-24)
**Choice:** Added steps S1–S5, A2, A3 to PLAN.md in a new "Audit remediation" section inserted before M3.2, and updated existing step deps (M3.2 now depends on S4; M3.4 on A3; M8.1 on S3).
**Why:** Independent audit (Antigravity / Claude Sonnet 4.6, 2026-10-24) found issues not tracked in the plan:
- SmsReceiver + PhoneStateReceiver still active in manifest; PhoneStateReceiver logs real phone numbers.
- app.launch / app.automate (in registry) call Runtime.exec() — shell access via registered tool.
- BrowserOpenTool in registry without URL validation (accepts file:, javascript:, etc.).
- allowBackup=true with no exclusion rules — profile ciphertext could be ADB-extracted.
- No prompt-injection test despite skill file §6 requiring one.
- ApprovalPolicyTest and ApprovalQueueTest never appeared in TESTLOG with real execution counts.
- Profile export and full wipe (skill file §3.1) not implemented.
**Steps added:** S1 (manifest + PII log), S2 (shell exec tools), S3 (BrowserOpenTool deregister), S4 (force test re-run), A2 (backup exclusion), A3 (prompt injection test), S5 (export + wipe).
**Not added as duplicate:** A1 (phone-number log) — merged into S1.
**Pre-existing steps not duplicated:** M3.2–M3.4, M4.1–M4.3, M8.1 — only their deps updated.

---

## D14 — Session 13 state sync and verification
**Choice:** Resume loop at M3.4 following completed M3.3 commit `38b4dd2`.
**Why:** Git history and test log confirm S1–S4, A2, M3.2, A3, and M3.3 passed. HANDOFF state section was updated to align with `38b4dd2` as the last green commit. Full unit suite was verified at session start with exit code 0.

---

## D15 — ResumePreviewScreen uses blank JdRequirement (JD text not persisted)
**Choice:** In `ResumePreviewViewModel`, build the resume using `JdRequirement()` (all defaults) since the raw JD text is not stored in `ApplicationRecord` — only `jdHash` is stored.
**Why:** `ApplicationRecord` was designed with a minimal footprint; storing the full JD text would add unbounded storage and was not part of the M6 spec. The resume preview still shows all confirmed profile facts with their evidence IDs; skill ranking is alphabetical rather than JD-matched, which is safe and honest.
**Alternatives:** Store JD text in `ApplicationRecord.notes` alongside the EligibilityReport JSON — considered but notes field is already used for the eligibility JSON and mixing two JSON blobs in one column is fragile. Proper solution (M8+): add a dedicated `jd_text` column in a future DB migration if JD-ranked resume preview becomes a priority.

---

## D16 — M12.4 Package identity: retain com.cellclaw for stability and safety
**Choice:** Retain the Kotlin package and application namespace as `com.cellclaw` while keeping the user-facing application name and display identity as `Elixir`.
**Why:** M12.4 is explicitly marked "Optional: rename package from com.cellclaw to com.elixir (only if user approves)". AGENTS.md explicitly states: "Keep the Kotlin package com.cellclaw until milestone M12 so the build is never broken by a rename." Renaming across 100+ files, Hilt dependencies, Room entities, manifest bindings, and AIDL interfaces without an explicit user mandate introduces major risk of regression and breaks Android component stability for zero user-facing benefit. The display name is `Elixir` everywhere in the UI and manifests.
**Alternatives:** Perform global rename to `com.elixir` — rejected under the conservative decision-making rule because user approval is explicitly required by the plan and no user request was made.

---

## D17 — Audit 2 remediation: milestones reopened, definition of done changed
**Choice:** Audit 2 found the automation pipeline unwired; milestones reopened; definition of done changed.
**Why:** Independent second audit (2026-10-25) revealed that while 235 unit tests pass, the automation pipeline (M8.4, M9.4, M10.3, M11.3) was completely unwired to the UI, the entry-point was trapped on ChatScreen, ReviewScreen submission was an empty lambda, and lintDebug failed with 4 errors. A unit test with mocks does not constitute completion. Milestones M7, M8.4, M9.4, M10.3, M11.3, M12.1-M12.5 are reopened. Definition of done updated in AGENTS.md: a step is done only when (a) reachable from the real UI path, (b) tested through the real call path not just a mock, and (c) a device result is recorded or marked NEEDS_USER_DEVICE.
**Remediation plan:** Added steps R1–R19 (functional & wiring) and U1–U10 (UI/UX design system & screens) to PLAN.md.

---

## D18 — Remove WakeWordService from active build and scope browser queries (R2)
**Choice:** (1) Relocated `com.cellclaw.wakeword` package to `app/src/legacy/kotlin/com/cellclaw/wakeword/` (disabling from active compilation and lint while keeping source files intact for attribution under MIT license). (2) Removed `WakeWordService` from `AndroidManifest.xml` and removed wake-word startup logic from `BootReceiver.kt` and `SettingsScreen.kt`. (3) Replaced broad `QUERY_ALL_PACKAGES` permission in `AndroidManifest.xml` with scoped `<queries>` for Chrome and HTTPS browser intent. (4) Used `ContextCompat.registerReceiver` with `RECEIVER_NOT_EXPORTED` in `CellClawAccessibility.kt`.
**Why:** Fixes all 4 fatal lint errors that caused `lintDebug` to fail with exit code 1. PlacementOS is an application assistant that must not require continuous background microphone audio recording (`RECORD_AUDIO`) or broad package query (`QUERY_ALL_PACKAGES`). Internal accessibility broadcast commands across `:a11y` process share the app UID; `RECEIVER_NOT_EXPORTED` secures the receiver from external app broadcast injection and resolves the Android 14+ lint requirement.




