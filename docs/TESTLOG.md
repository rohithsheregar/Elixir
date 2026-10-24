# TESTLOG.md — Elixir Project

All test runs are recorded here. Only write PASS for something run in the current session that was seen to succeed.

Statuses: `PASS` | `FAIL` | `NOT RUN (reason)` | `NEEDS_USER_DEVICE`

---

## Session 1 — 2026-10-22 (Antigravity / Claude Sonnet 4.6)

### T1 — assembleDebug baseline (M0.2 prerequisite) — Attempt 1
**Command:** `.\gradlew.bat assembleDebug 2>&1 | Select-Object -First 50`
**Exit code:** 1
**Status:** FAIL
**Summary:**
```
Gradle 9.7.1. Build failed with script compilation errors in app/build.gradle.kts:
- Line 48: kotlinOptions deprecated (migrate to compilerOptions)
- Line 57: aaptOptions deprecated
- Line 58: noCompress deprecated
- Line 66: Unresolved reference 'exec' (inside tasks.register enableAccessibility)
- Line 67: Unresolved reference 'commandLine'
- Line 71: Unresolved reference 'exec'
```

### T1 — assembleDebug (M0.2) — Attempt 2
**Command:** `.\gradlew.bat assembleDebug 2>&1`
**Exit code:** 1
**Status:** FAIL
**Summary:**
```
Tried: compilerOptions (inside android block) → Unresolved reference 'compilerOptions'
Tried: project.exec → still Unresolved reference 'exec'
AGP 8.7.3 does NOT expose compilerOptions inside android{} — must use top-level kotlin{} or keep kotlinOptions.
project.exec is not available inside tasks.register { doLast {} } lambda in Gradle 9.
```

### T1 — assembleDebug (M0.2) — Attempt 3
**Command:** `.\gradlew.bat assembleDebug 2>&1`
**Exit code:** 1
**Status:** FAIL
**Summary:** Build script compiled but KSP dependency download failed — "No such host is known (dl.google.com)" — transient DNS failure. Not a code error.

### T1 — assembleDebug (M0.2) — Attempt 4 (Session 2)
**Command:** `.\gradlew.bat assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:**
```
BUILD SUCCESSFUL in 3m 1s
41 actionable tasks: 21 executed, 20 up-to-date
Warnings only (no errors): deprecated EXTRA_INCOMING_NUMBER, deprecated getFromLocation,
deprecated menuAnchor — all pre-existing in CellClaw code, not Elixir additions.
Strip warnings for .so libs (tensorflow etc) — non-fatal.
```

### T2 — testDebugUnitTest (M0.4)
**Command:** `.\gradlew.bat testDebugUnitTest 2>&1`
**Exit code:** 1
**Status:** FAIL
**Summary:** Tests did not start. Gradle failed while creating its daemon: `java.io.IOException: Unable to establish loopback connection`.

### T3 — testDebugUnitTest (M0.4) — Attempt 2
**Command:** `.\gradlew.bat --no-daemon testDebugUnitTest 2>&1`
**Exit code:** 1
**Status:** FAIL
**Summary:** The required single-use daemon failed identically before tests started: `java.io.IOException: Unable to establish loopback connection`.

### T4 — testDebugUnitTest (M0.4) — Attempt 3
**Command:** `.\gradlew.bat --no-daemon -Dorg.gradle.jvmargs= testDebugUnitTest 2>&1`
**Exit code:** 1
**Status:** FAIL
**Summary:** Overriding the project JVM arguments did not avoid daemon creation; it failed identically before tests. The daemon log identifies `java.net.SocketException: Invalid argument: connect` from Java 23's NIO loopback pipe initialization.

### T5 — testDebugUnitTest (M0.4) — Session 4
**Command:** `$env:JAVA_HOME='<temporary JDK 17>'; $env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 29m 16s`; 31 actionable tasks (10 executed, 21 up-to-date). The scoped Java setting directs the JDK's Unix-domain selector pipe to `C:\Temp`, avoiding the host loopback error. Three existing source deprecation warnings were reported; no test failures.

### T6 — assembleDebug (M0.4 verification) — Session 4
**Command:** `$env:JAVA_HOME='<temporary JDK 17>'; $env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 1m 23s`; 41 actionable tasks (3 executed, 38 up-to-date).

### T7 — testDebugUnitTest (M1.1) — Session 4
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 21s`; 31 actionable tasks (9 executed, 22 up-to-date). Existing TensorFlow duplicate-namespace warning only.

### T8 — assembleDebug (M1.1) — Session 4
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 15s`; 41 actionable tasks (1 executed, 40 up-to-date).

---

<!-- Future sessions add rows below -->
### T36 — M5.4 milestone verification and tag cp-m5-1 — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 35s` (50 tasks up-to-date). Tagged milestone `cp-m5-1`.

### T35 — M5.3 ResumeDiff against master resume — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.resume.ResumeDiffTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ResumeDiffTest` passed (4 tests: identical resumes yield no material change, reordered lines trigger material change and require approval, added/removed lines detected, added/removed sections detected); `BUILD SUCCESSFUL in 51s` (31 tasks, 9 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 33s` (50 tasks, 4 executed, 46 up-to-date).

### T34 — M5.2 PDF generation for tailored resume — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.resume.PdfGeneratorTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `PdfGeneratorTest` passed (3 tests: valid text-selectable PDF with Name_Company_Role_vN naming, SHA-256 and JD hash, text extractable via DefaultPdfTextExtractor, valid link annotations, unicode rendering, clean pagination without overflow); `BUILD SUCCESSFUL in 37s` (31 tasks, 7 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 24s` (50 tasks, 4 executed, 46 up-to-date).

### T33 — M5.1 ResumeContent, EvidenceMap, and ResumeBuilder — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.resume.ResumeBuilderTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ResumeBuilderTest` passed (5 tests: build resume + verify evidence integrity, unconfirmed facts strictly excluded, skills ranked required -> preferred -> other, projects ranked by JD skill overlap, serialization round-trip of ResumeContent and EvidenceMap); `BUILD SUCCESSFUL in 24s` (31 tasks, 4 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 32s` (50 tasks, 5 executed, 45 up-to-date).

### T32 — M4.3 milestone verification and assembleDebug — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 14s` (50 tasks up-to-date). Tagged milestone `cp-m4-1`.

### T31 — M4.2 EligibilityEngine implementation and tests — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.eligibility.EligibilityEngineTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `EligibilityEngineTest` passed (9 tests: all criteria pass -> ELIGIBLE, preferred skill missing never fails overall, exact/below/above border CGPA, percentage border, never guess CGPA/percentage equivalence, never guess CGPA scale conversions, never guess degree/branch equivalence, unconfirmed profile facts -> UNKNOWN, backlog evaluation); `BUILD SUCCESSFUL in 34s` (31 tasks, 4 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 36s` (50 tasks, 4 executed, 46 up-to-date).

### T30 — M4.1 EligibilityResult and EligibilityReport data models — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.eligibility.EligibilityModelTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `EligibilityModelTest` passed (8 tests: mandatory PASS -> ELIGIBLE, mandatory FAIL -> NOT_ELIGIBLE, mandatory FAIL precedence over UNKNOWN, mandatory UNKNOWN -> UNKNOWN, preferred FAIL/UNKNOWN do not fail overall, empty criteria -> ELIGIBLE, serialization round-trip); `BUILD SUCCESSFUL in 40s` (31 tasks, 11 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 32s` (50 tasks, 5 executed, 45 up-to-date).

### T29 — S5 Profile export and full wipe — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ProfileManagerTest --tests com.cellclaw.profile.ProfileRepositoryTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ProfileManagerTest` (6 tests: UNKNOWN on unconfirmed/missing, proposal diff, export null on empty, export full profile round-trip, export confirmedOnly filter, fullWipe clears repository and facts) and `ProfileRepositoryTest` (4 tests: save/get, replace/delete, round-trip, wipeAll) passed; `BUILD SUCCESSFUL in 41s` (31 tasks, 9 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 24s` (50 tasks, 4 executed, 46 up-to-date).

### T28 — M3.4 milestone suite and assembleDebug — Session 13
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 14s` (50 tasks up-to-date). Tagged milestone `cp-m3-1`.

### T27 — M3.3 JdDocumentReader document text extraction — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.jd.JdDocumentReaderTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `JdDocumentReaderTest` passed (7 tests: plain text extraction, synthetic DOCX XML parsing, empty file fallback, scanned PDF fallback, PDF text extraction, corrupted DOCX fallback, unsupported file fallback); `BUILD SUCCESSFUL in 41s` (31 tasks, 10 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 27s` (50 tasks, 4 executed, 46 up-to-date).

### T26 — A3 prompt injection test in JdParser — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.jd.JdParserTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `JdParserTest` passed (8 tests: added prompt injection tests for system prompt override, policy tampering, candidate profile spoofing, and markdown/json injection payloads — all confirmed inert and treated as literal text); `BUILD SUCCESSFUL in 24s` (31 tasks, 4 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 16s` (50 tasks, 1 executed, 49 up-to-date).

### T25 — M3.2 JdParser text ingestion and parsing — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.jd.JdParserTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `JdParserTest` passed (5 tests: campus placement JD, internship JD with percentage, empty/null graceful handling, huge/malformed input, prompt injection literal handling); `BUILD SUCCESSFUL in 39s` (31 tasks, 10 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 23s` (50 tasks, 4 executed, 46 up-to-date).

### T24 — A2 backup disabled and profile data protection verified — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.ElixirManifestTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ElixirManifestTest` passed (4 tests: allowBackup=false explicitly asserted to prevent ADB data extraction, forbidden permissions excluded, receivers removed, PII phone logging absent); `BUILD SUCCESSFUL in 24s` (31 tasks, 4 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 17s` (50 tasks, 1 executed, 49 up-to-date).

### T23 — S4 full unit suite rerun with --rerun-tasks — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --rerun-tasks; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite re-executed with `--rerun-tasks` (31 tasks executed, 0 cached); 25 test suites, 107 total tests, 0 failures, 0 errors, 0 skipped. Confirmed `ApprovalPolicyTest` (4 tests) and `ApprovalQueueTest` (4 tests) both executed fresh and passed; `BUILD SUCCESSFUL in 1m 30s`.

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Debug assembly passed; `BUILD SUCCESSFUL in 17s` (41 tasks, 4 executed, 37 up-to-date).

### T22 — S3 BrowserOpenTool deregistered until M8.1 — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.tools.ElixirDisabledToolsTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ElixirDisabledToolsTest` passed (4 tests: browser.open verified disabled in registry stub, safe tools verified, AppModule confirmed not registering browserOpen); `BUILD SUCCESSFUL in 42s` (31 tasks, 11 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 19s` (50 tasks, 4 executed, 46 up-to-date).

### T21 — S2 shell execution tools removed and intent launch secured — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.tools.ElixirDisabledToolsTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ElixirDisabledToolsTest` passed (4 tests: app.launch and app.automate verified disabled in registry stub, safe tools verified, AppModule confirmed not registering shell/automate tools, AppControlTool confirmed containing zero Runtime.exec calls); `BUILD SUCCESSFUL in 1m 5s` (31 tasks, 12 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 28s` (50 tasks, 5 executed, 45 up-to-date).

### T20 — S1 manifest clean-up and PII log removal — Session 12
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.ElixirManifestTest; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ElixirManifestTest` passed (4 tests: forbidden permissions excluded, receivers removed, allowBackup=false, no PII logged in PhoneStateReceiver); `BUILD SUCCESSFUL in 29s` (31 tasks up-to-date).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug; exit $LASTEXITCODE`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 39s` (50 tasks, 4 executed, 46 up-to-date).

### T19 — M3.1 tests and build — Session 9
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.jd.JdRequirementModelTest`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `JdRequirementModelTest` passed; `BUILD SUCCESSFUL in 1m 5s` (31 tasks, 11 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 51s` (50 tasks, 5 executed). Existing Gradle 10 deprecation warning only.

### T18 — M2.5 milestone suite — Session 9
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest`
**Exit code:** 0
**Status:** PASS
**Summary:** M2 milestone unit suite passed; `BUILD SUCCESSFUL in 20s` (31 tasks, all up-to-date).

### T17 — M2.4 tests and build — Session 9
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ResumeImporterTest`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ResumeImporterTest` passed; `BUILD SUCCESSFUL in 2m 17s` (31 tasks, 9 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 1m 16s` (50 tasks, 4 executed). Existing Gradle 10 deprecation warning only.

### T16 — M2.3 tests and build — Session 9
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ProfileManagerTest`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ProfileManagerTest` passed; `BUILD SUCCESSFUL in 1m 2s` (31 tasks, 9 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 50s` (50 tasks, 4 executed). Existing Gradle 10 deprecation warning only.

### T15 — M2.2 tests and build — Session 8
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ProfileRepositoryTest`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `ProfileRepositoryTest` passed after the transient Maven DNS retry; `BUILD SUCCESSFUL in 51s` (31 tasks, 12 executed).

**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug`
**Exit code:** 0
**Status:** PASS
**Summary:** Full unit suite and debug assembly passed; `BUILD SUCCESSFUL in 2m 11s` (50 tasks, 23 executed). Existing Gradle 10 deprecation warning and native-library strip notice only.

### T13 — ProfileRepositoryTest (M2.2 attempt 1) — Session 8
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ProfileRepositoryTest`
**Exit code:** 1
**Status:** FAIL
**Summary:** `security-crypto:1.0.0` does not provide `MasterKey`; `ProfileRepository.kt` used the 1.1 API and failed at `:app:compileDebugKotlin`. Next hypothesis: use the stable 1.0.0 `MasterKeys` factory and its matching `EncryptedSharedPreferences.create` overload.

### T14 — ProfileRepositoryTest (M2.2 attempt 2) — Session 8
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest --tests com.cellclaw.profile.ProfileRepositoryTest`
**Exit code:** 1
**Status:** FAIL
**Summary:** Kotlin compilation passed with the corrected 1.0.0 API, but Gradle could not resolve `repo.maven.apache.org` to download `com.google.crypto.tink:tink-android:1.5.0` for Hilt aggregation. Next hypothesis: retry the identical verified source after the transient Maven DNS failure.

### T12 — M2.1 tests and build — Session 6
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused CandidateProfileModelTest passed in 42s; full unit suite plus debug assembly passed in 33s (50 tasks).
### T11 — assembleDebug (M1.3) — Session 5
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 14s`; 41 actionable tasks, all up-to-date.
### T9 — M1StringsTest and full unit suite (M1.2) — Session 5
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** Focused `M1StringsTest` passed in 33s; full suite passed in 19s (31 tasks).

### T10 — assembleDebug (M1.2) — Session 5
**Command:** `$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon assembleDebug 2>&1`
**Exit code:** 0
**Status:** PASS
**Summary:** `BUILD SUCCESSFUL in 25s`; 41 tasks (3 executed, 38 up-to-date).
