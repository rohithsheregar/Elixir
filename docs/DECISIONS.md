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

---

## D19 — Persist JdRequirement inside ApplicationPayload in notes column (R3)
**Choice:** Store `ApplicationPayload` (containing `EligibilityReport`, `PersistedJdRequirement`, and `rawJdText`) serialized in `ApplicationRecord.notes`. `ResumePreviewViewModel` deserializes `JdRequirement` to build the resume with tailored skill and project ranking.
**Why:** Fixes Audit 2 Hop 3 finding where `ResumePreviewViewModel` was building resumes using an empty `JdRequirement()`, degrading resume skill ranking to un-tailored alphabetical ordering. Using the existing nullable `notes` column avoids a Room database schema migration while keeping the application payload fully structured. `EligibilityReportViewModel` and `ResumePreviewViewModel` support both `ApplicationPayload` and legacy raw JSON fallback.

---

## D20 — Wire ACTION_SEND share intent handling directly into NewApplicationScreen (R4)
**Choice:** Add `ACTION_SEND` intent filters to `MainActivity` in `AndroidManifest.xml` for `text/plain`, `application/pdf`, docx, and `text/*`. In `MainActivity`, extract shared text or stream URI and navigate to `NewApplicationScreen` via `LaunchedEffect(isShareIntent)` while preserving `startDestination = NavigationRoutes.START_DESTINATION` (Tracker).
**Why:** Fulfills PlacementOS §3.2 requirement ("Inputs, in order: pasted text, Android share-sheet text, user-picked PDF/DOCX/TXT through the system document picker"). Resolves Audit 2 Hop 1 gap where external apps had no share target into Elixir. Using `LaunchedEffect` preserves the backstack so popping back returns to TrackerScreen, while keeping `NavigationWiringTest` assertions intact.

---

## D21 — State persistence across process death with SavedStateHandle in ViewModels (R5)
**Choice:** Inject `SavedStateHandle` into `NewApplicationViewModel` and `ReviewViewModel` with `= SavedStateHandle()` default parameter for seamless testing. In `NewApplicationViewModel`, persist entered `jdText`, `sourceUrl`, `company`, and `role` to `SavedStateHandle` and initialize `_uiState` from it. In `ReviewViewModel`, persist `appId` and `actionSuccessMessage`, automatically reloading the application and review item synthesis in `init` if `appId` is present in `SavedStateHandle` (supporting navigation arguments and process death).
**Why:** Addresses Audit 2 Section 2 finding on process death and activity recreation ("State across rotation and process death"). User inputs into `NewApplicationScreen` (such as lengthy pasted JDs or custom company/role entries) and active review state in `ReviewScreen` are no longer wiped if the Android OS kills the process in the background. Pure JVM unit tests in `SavedStateHandleProcessDeathTest` verify complete state restoration without requiring instrumentation.

---

## D22 — Centralize DI bindings in AppModule and isolate FakeResumeUriProvider to test sources (R6)
**Choice:** Add Hilt `@Provides` methods in `AppModule.kt` for `ResumeBuilder`, `PdfGenerator`, `FieldClassifier`, `NeedsInputCollector`, `AccessibilityFormNodeOperator`, `FieldFiller`, `FileProviderResumeUriProvider`, `ResumeUploader`, `PostUploadVerifier`, `AccessibilitySession`, and `ConfirmationDetector`. Relocate `FakeResumeUriProvider` from `app/src/main/` to `app/src/test/` and remove its default instantiation from `ResumeUploader`.
**Why:** Resolves Audit 2 Section 2 finding on DI bindings where core resume and automation components were tested as standalone pure JVM classes but orphaned from the dependency graph. Moving `FakeResumeUriProvider` to `src/test/` ensures production builds strictly use Android's `FileProviderResumeUriProvider`, preventing fake test URIs from leaking into production uploads. Verified by `DiBindingsTest`.

---

## D23 — ReviewViewModel cryptographically bound ApprovalToken and state gating (R7)
**Choice:** Inject `ApprovalTokenManager` into `ReviewViewModel` and issue an `ApprovalToken` upon `approveSubmission(userDirectTapConfirmed = true)`. Cryptographically bind the token to `app.company`, `app.role`, `urlOrigin`, deterministic `formStateHash` (computed from filled review fields), and `resumeHash`. Transition state in `ApplicationTracker` only from `DRAFT` to `READY_FOR_REVIEW`, explicitly prohibiting a premature transition to `SUBMITTING`.
**Why:** Directly addresses Audit 2 Hop 7 & 8 gap where `ReviewViewModel` bypassed token issuance and transitioned to `SUBMITTING` immediately on review approval rather than when foreground automation actually begins. Enforces PlacementOS hard rule §7 ("Submit approval token is one-time use, expiring, and bound to the exact form state"). Verified by `ReviewApprovalWiringTest`.

---

## D24 — AutomationOrchestrator pipeline and synthetic dummy form verification (R8)
**Choice:** Implement `AutomationOrchestrator` orchestrating `BrowserOpener` -> `AccessibilitySession` -> `FieldClassifier` / `FieldFiller` -> `NeedsInputCollector` -> `ResumeUploader` -> `PostUploadVerifier` -> explicit stop before submit (`AwaitingUserFinalSubmitApproval`) -> `SubmitSupervisor`. Open `BrowserOpener` for extension so JVM tests can run without unmocked `android.net.Uri.parse`. In `FieldFiller`, route `FillResult.NeedsUserApproval` (free-text questions) into `NeedsInputCollector` and support `YES_NO`, `SKILLS`, and `CHECKBOX` categories from confirmed profile answers. Skip submit button during form field filling to strictly preserve final submission for `executeFinalSubmit`.
**Why:** Bridges the central automation gap identified in Audit 2 Hop 3-8 where individual components operated in isolation without an end-to-end execution coordinator. Enforces the non-negotiable PlacementOS rule: automation must pause before Submit for direct user confirmation, consuming the single-use `ApprovalToken` only when the user approves final submission. Tested and verified on the synthetic accessibility tree for `docs/dummy_google_form.html` in `OrchestratorFlowTest`.

---

## D25 — Enable-Accessibility service check and prompt in Review flow (R9)
**Choice:** Implement `AccessibilityChecker` (implementing `AccessibilityInspector`) to check whether the `CellClawAccessibility` service is enabled via `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and produce settings navigation intents (`ACTION_ACCESSIBILITY_DETAIL_SETTINGS` on API 36+ or `ACTION_ACCESSIBILITY_SETTINGS`). Inject `AccessibilityChecker` into `ReviewViewModel`. When the user initiates review approval/submission, if accessibility is disabled, block token generation, preserve the current state, and raise `showAccessibilityPrompt = true`, displaying an `AlertDialog` in `ReviewScreen` that guides the user to Android Accessibility Settings.
**Why:** Directly resolves Audit 2 Section 4 finding ("zero checks or enablement prompts exist in any Elixir placement screen"). Without this check, a user attempting supervised automation with the service disabled would experience silent browser launch and failed accessibility node parsing. Enforces clear user feedback and explicit opt-in. Verified by `AccessibilityEnablePromptTest`.

---

## D26 — RunScreen, RunViewModel, and Foreground Service Notification Controls (R10)
**Choice:** Implement `RunScreen` and `RunViewModel` observing `AutomationOrchestrator.step` StateFlow. Wire into `MainActivity` `NavHost` (`run/{appId}`) and route to it from `ReviewScreen` upon approval. Add live step progression, explicit user Pause/Resume buttons, Stop/Cancel button, and CAPTCHA/login manual intervention handoff banner. Wire `CellClawService` notification actions (`ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_STOP`) directly to `AutomationOrchestrator`. Add `@Inject constructor()` and `@Provides` binding for `UrlValidator` to satisfy Hilt compilation.
**Why:** Directly fulfills Audit 2 Section 2 and Section 4 requirements for live automation observability and user supervision ("Foreground service with Pause/Stop", "user can always tell what the app is doing"). Guarantees the user retains full control at all times via UI controls and persistent notification actions, honoring PlacementOS rules §5 & §6 (no bot bypass, hand control to user for CAPTCHA/login). Verified by `RunScreenTest` and `DiBindingsTest`.

---

## D27 — ResultScreen, Confirmation Reference, and Supervised Retry State Flow (R11)
**Choice:** Implement `ResultScreen` and `ResultViewModel` displaying application outcome banners (`SUBMITTED_CONFIRMED`, `SUBMISSION_UNKNOWN`, `FAILED`, `CANCELLED`), persistent confirmation reference receipts, and details (resume version/hash, submission timestamp). For `SUBMISSION_UNKNOWN`, provide an explicit manual verification input that consumes user-verified reference receipts to transition the application to `SUBMITTED_CONFIRMED` in `ApplicationTracker`. For `FAILED` and `SUBMISSION_UNKNOWN`, implement `retrySubmission` resetting the application state through `DRAFT` to `READY_FOR_REVIEW` and returning to `ReviewScreen`. Connect `RunScreen`'s `onComplete` to `NavigationRoutes.resultRoute(id)`.
**Why:** Addresses Audit 2 Journey Hop 7 ("Run -> Result -> Tracker") and Phase 1 §3.5 / Phase 3 §5 rules. Enforces that a tap on Submit is not treated as guaranteed success, prevents automatic re-submissions on ambiguous timeouts while giving the user full supervised agency to confirm or retry after manual inspection in Chrome. Verified by `ResultScreenTest`.

---

## D28 — Needs-Input Modal and Interactive Sensitive/Missing Field Resolution (R12)
**Choice:** Add `NeedsInputReviewItem` model and interactive resolution in `ReviewViewModel` (`resolveFieldInput`) and `ReviewScreen` (`NeedsInputModalDialog`). Missing profile facts and sensitive placement questions (salary expectations, bond agreements, legal commitments) are explicitly catalogued as needs-input items. In `ReviewScreen`, provide direct access via "Resolve Missing Questions" on the warning card and a dedicated "Needs Your Input" review card. Upon user resolution, update `filledFields`, clear `unansweredFields`, and persist resolution to `SavedStateHandle` so `ApprovalToken` cryptographically binds the user-entered answers.
**Why:** Enforces PlacementOS Hard Rule 5 ("Pause and ask on: declarations/terms/consent boxes, salary, bond, backlog, work authorisation, disability or demographic questions, anything legal, and any question with no matching confirmed fact"). Eliminates dead ends where missing fields simply blocked review without recourse, giving the candidate interactive agency to review and supply verified answers directly before supervised automation. Verified by `NeedsInputResolutionTest`.

---

## D29 — Eligibility Hard Stop and Explicit User Override Gate (R13)
**Choice:** Add `isNotEligible`, `userOverrideConfirmed`, and `canProceed` properties to `EligibilityReportUiState` in `EligibilityReportViewModel`. When an application's overall eligibility evaluates to `NOT_ELIGIBLE`, `EligibilityReportScreen` displays an "Eligibility Hard Stop" warning banner and disables the primary "Proceed to Tailored Resume" CTA. Progression is strictly gated until the user actively confirms an explicit override checkbox ("I understand the eligibility risks and explicitly confirm my override to proceed anyway").
**Why:** Enforces PlacementOS §3.3 hard rules and resolves Audit 2 Section 2 finding where candidates who failed mandatory criteria could advance through resume tailoring and form submission without friction. Prevents accidental non-compliant applications from reaching campus recruiters while preserving user sovereignty through an intentional, affirmative override mechanism. Verified by `EligibilityGateTest`.

---

## D30 — Elixir Design System Tokens and Semantic Colors (U1)
**Choice:** Implement custom semantic design tokens (`Color.kt`, `Typography.kt`, `Shape.kt`, `Spacing.kt`, and `Theme.kt`) with `ElixirColors` for light/dark themes and `Spacing` exposed via `LocalElixirColors` and `LocalSpacing`, while maintaining backward-compatible `CellClawTheme` forwarding to `ElixirTheme`.
**Why:** Audit 2 Section 4 identified that the app lacked design tokens, used raw hex literals across composables, lacked custom typography, and degraded in dark mode. Providing dedicated semantic tokens for application status (eligible, not eligible, unknown), criteria states (pass, fail, warning), card borders, and spacing scales establishes a foundation for modern UX and dark mode compliance across all screens. Verified by `ThemeTest`.

---

## D31 — Full Dark Mode Support & Elimination of Hardcoded Hex Colors (U5)
**Choice:** Audit and replace every hardcoded `Color(0xFF...)` literal in all placement screens (`TrackerScreen`, `EligibilityReportScreen`, `ResumePreviewScreen`, `ProfileScreen`, `ReviewScreen`, `RunScreen`, and `ResultScreen`) with semantic tokens from `MaterialTheme.elixirColors` and `MaterialTheme.colorScheme`.
**Why:** Addresses Audit 2 Section 4 finding where screens used raw pastel hex literals that rendered glaring white-on-black or unreadable low-contrast boxes when Android system dark mode was activated. All status badges, eligibility banners, warnings, and diff cards now adapt between light and dark modes with compliant WCAG contrast ratios. Verified by `DarkThemeContrastTest`.

---

## D32 — Brand Identity: Bespoke Vector Icon, Core SplashScreen & Brand Rebranding (U9)
**Choice:** Adopt `androidx.core:core-splashscreen:1.0.1` with a dedicated starting theme (`Theme.Elixir.Starting` inheriting from `Theme.SplashScreen` and delegating to `Theme.Elixir`), initialize splash transitions via `installSplashScreen()` in `MainActivity.onCreate()` before `super.onCreate()`, author a bespoke 108dp adaptive vector launcher foreground icon (`ic_elixir_launcher_foreground.xml` featuring a faceted hexagonal shield, stylized 'E' monogram, and ascending career trajectory starburst), and rebrand remaining user-facing system notifications and QuickSettings tile labels from legacy "CellClaw" to "Elixir".
**Why:** Audit 2 Section 4 reported that the application presented generic placeholder icons and lacked cold-start branding, while legacy strings still surfaced in system QuickSettings and notifications. Using the Android 12+ backwards-compatible SplashScreen API eliminates cold-start blank screens and establishes an intentional, polished brand experience from first launch. Verified by `SplashThemeTest` and full build/test suites.

---

## D33 — Multi-step ProgressStepper component (U2)
**Choice:** Implement ProgressStepper composable in com.cellclaw.ui.components with PlacementStep enum (JD_INGESTION, ELIGIBILITY, TAILORED_RESUME, SUPERVISED_REVIEW), monotonic state transitions (COMPLETED, CURRENT, UPCOMING), and wire it into NewApplicationScreen, EligibilityReportScreen, ResumePreviewScreen, and ReviewScreen.
**Why:** Addresses Audit 2 Section 4 finding where candidates lost orientation across multi-screen application workflows. A visual progress stepper gives clear stage orientation and progress feedback without intrusive modals or extra dependencies. Verified by ProgressStepperPreviewTest and full test suites.

---

## D34 — ReviewScreen Boarding-Pass Style Redesign (U3)
**Choice:** Introduce BoardingPassCard composable in com.cellclaw.ui.components featuring flight-ticket visual semantics: company/role headline, flight header, dashed/perforated divider canvas with custom PathEffect, and a two-column metadata grid showing target form domain, resume file name with version, SHA-256 fingerprint, and current application state status stamp.
**Why:** Addresses Audit 2 Section 4 finding where ReviewScreen used plain stacked rectangular cards lacking visual hierarchy and status emphasis. Boarding pass layout consolidates pre-submission metadata into a cohesive, easily scannable pass, establishing a clear milestone feeling before supervised form submission. Verified by ReviewScreenUiTest and full test suites.

---

## D35 — Calm Illustrated Empty States with Primary CTAs (U6)
**Choice:** Implement EmptyState composable in com.cellclaw.ui.components with soft concentric circular illustration badges, clear typography, optional guidance tips, and primary/secondary action buttons. Integrate into TrackerScreen (distinct states for unfiltered empty list vs filtered/search empty list with Clear Filter action) and ProfileScreen (empty confirmed facts with Add Profile Fact CTA and zero-fabrication reassurance).
**Why:** Addresses Audit 2 Section 4 finding where empty application lists and uninitialized profiles displayed stark, unstyled plain text blocks with dead ends. Illustrated empty states reduce friction, clarify next steps, and maintain high engagement for candidates beginning their placement workflow. Verified by EmptyStateComponentTest and full test suites.

---

## D36 — Animated Screen Transitions & List Reordering Motion (U7)
**Choice:** Configure NavHost in MainActivity.kt with standard 300ms tween transitions: horizontal slide transitions paired with fade in/out (slideIntoContainer / slideOutOfContainer towards Start and End). Apply Modifier.animateItem() across LazyColumn items in TrackerScreen.kt (keyed on pplication.id) and ProfileScreen.kt (keyed on proposal.proposed.id and act.id). Update NavigationWiringTest.kt to robustly assert startDestination = startDest alongside transition parameters.
**Why:** Addresses Audit 2 Section 4 finding where screen transitions were abrupt cuts and list item deletions/updates caused jarring visual pops. Standardized 300ms motion transitions establish smooth spatial orientation without introducing latency or frame drops on low-end devices. Verified by MotionTransitionTest and full unit suite.


---

## D37 — Visual Resume Diff Viewer and Source-Tag Transparency (U4)
**Choice:** Implement `ResumeDiffView` in `com.cellclaw.ui.components` and integrate a segmented toggle in `ResumePreviewScreen` switching between "Formatted View" and "Visual Diff". `ResumePreviewViewModel` computes baseline master resume (`ResumeBuilder().build(profile, JdRequirement())`) alongside tailored resume, diffing them via `ResumeDiffer().diff(...)`. The view renders summary status badges, delta counters (`+Added`, `-Omitted`, `~Reordered`), grouped sectional diff items styled with semantic theme containers, strikethroughs for omissions, and provenance source tags (`#profile`, `#jd-tailored`, `#omitted`) guaranteeing zero fabrication.
**Why:** Addresses Audit 2 Section 4 finding where candidates could not inspect why or how their tailored resume differed from their base profile before giving submission approval. Providing an interactive side-by-side diff with explicit provenance tags builds trust, surfaces alterations transparently, and ensures candidates retain total agency over what gets submitted to recruiters. Verified by `ResumeDiffViewTest` and full test suites.


---

## D38 — Collapsible Accordion Groups for Profile Facts (U8)
**Choice:** Implement `ProfileFactCategory` (PERSONAL, ACADEMICS, EXPERIENCE, SKILLS) and `CategoryAccordionCard` in `ProfileScreen.kt`. Group confirmed profile facts into the 4 primary placement categories, each with its own count badge, descriptive subtitle, category icon, and rotating chevron indicator animating smoothly with `AnimatedVisibility`. Provide a view mode switcher between "Grouped (Accordion)" and "All Facts (Flat)" to preserve full flexibility and complete backward compatibility with `MotionTransitionTest`.
**Why:** Addresses Audit 2 Section 4 finding where a large flat list of profile facts degraded readability and made it difficult for candidates to verify specific academic or technical details before submitting applications. Organizing facts into domain-aligned collapsible categories streamlines profile review, highlights missing category data, and maintains high visual structure. Verified by `ProfileScreenUiTest` and full test suites.


---

## D39 — Compose Screen Previews & Roborazzi Screenshot Test Suite (U10)
**Choice:** Implement dedicated preview suite in `com.cellclaw.ui.previews.ScreenPreviews` providing `@Preview` composables for all 6 placement screens (`TrackerScreen`, `NewApplicationScreen`, `EligibilityReportScreen`, `ResumePreviewScreen`, `ReviewScreen`, `ProfileScreen`) and core bespoke components (`ProgressStepper`, `BoardingPassCard`, `EmptyState`, `ResumeDiffView`) in both Light Mode (`showBackground = true`) and Dark Mode (`Configuration.UI_MODE_NIGHT_YES`), strictly enclosed within `ElixirTheme` without hardcoded colors. Author `RoborazziScreenshotTest` to assert preview presence, theme configuration, and crash-free composable rendering.
**Why:** Addresses Audit 2 Section 4 finding where screens lacked `@Preview` annotations, hindering rapid visual verification in Android Studio and automated UI regression testing. Decoupling preview rendering state from Hilt ViewModels enables instant UI previewing in IDE tooling and headless screenshot verification. Verified by `RoborazziScreenshotTest` and full test suites.


---

## D40 — Resilient AiClient Wrapper with 429 Backoff & Fallback Failover (R14)
**Choice:** Implement `ResilientAiClient` in `com.cellclaw.ai` wrapping cloud providers (`ProviderManager` / `GeminiProvider`), bound in `AppModule`. The wrapper implements: (1) exponential backoff retries with jitter upon encountering HTTP 429 (Rate Limit Exceeded); (2) per-application call budget enforcement (max 5 calls per application ID); (3) response caching indexed by JD hash and prompt to prevent redundant network invocations; (4) automatic failover to the fallback provider if primary provider quota is exhausted; and (5) failsafe rollback to ensure application state reverts to or remains in `DRAFT` upon terminal error, preventing orphaned `SUBMITTING` or `FAILED` states.
**Why:** Addresses Audit 2 Section 3 finding where unhandled 429 rate limit errors or network drops left applications stranded in inconsistent states and exhausted user API quotas. A resilient wrapper guarantees clean failure isolation, budget protection, and deterministic state transitions. Verified by `AiClientRetryTest` and full test suites.
