# HISTORY.md — Elixir Project

Archive of old HANDOFF content (moved here to keep HANDOFF under 80 lines).

---

## Session 1 start — 2026-10-22

**Repo confirmed:** `origin https://github.com/frido22/cellclaw.git`
**Latest upstream commit:** `40e019e Revise README with enhanced details and formatting`
**Branch on session start:** master (with AGENTS.md and PlacementOS_SKILL.md staged/untracked)
**git status at start:** AGENTS.md and PlacementOS_SKILL.md were new/added; gradle-wrapper.properties was modified (Gradle 9.7.1 upgrade).
**No cp-* tags existed.**

**Phase 0 audit summary (see docs/NOTES.md for full detail):**
- Repo is indeed github.com/frido22/cellclaw — confirmed by `git remote -v`
- App package: com.cellclaw, compileSdk 35, minSdk 26, Java 17 target
- Risky tools present in ToolRegistry: SmsReadTool, SmsSendTool, PhoneCallTool, PhoneLogTool, ContactsSearchTool, ContactsAddTool, CameraSnapTool, CameraRecordTool, LocationTool, ScriptExecTool (uses ProcessBuilder)
- ScriptExecTool at line 30: `val process = ProcessBuilder("sh", "-c", command)` — MUST be disabled for Elixir profile
- Manifest has SMS, Phone, Camera, Location, Contacts permissions — these remain in manifest (CellClaw may still need them) but tools are removed from registry
- Build failure: app/build.gradle.kts deprecated APIs + unresolved exec/commandLine in enableAccessibility task
- No Firebase/analytics SDKs found
- Java 23 present at C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
