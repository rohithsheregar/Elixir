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
