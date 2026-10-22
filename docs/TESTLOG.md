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

---

<!-- Future sessions add rows below -->
