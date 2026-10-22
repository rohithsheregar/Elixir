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
**Exit code:** (running)
**Status:** NOT RUN (pending)
**Changes applied:**
- Reverted kotlinOptions (keep as-is with @Suppress; it's a warning not an error in AGP 8.7)
- Changed aaptOptions to androidResources { noCompress += listOf("tflite") }
- Replaced bare exec{} with abstract task class EnableAccessibilityTask injecting ExecOperations via @Inject

### T2 — testDebugUnitTest baseline
**Command:** NOT RUN (blocked by T1 build failure)
**Status:** NOT RUN (build fails before test task can run)

---

<!-- Future sessions add rows below -->
