# NOTES.md — Elixir Phase 0 Audit

## Repo identity
- Remote: `origin https://github.com/frido22/cellclaw.git`
- Confirmed repo: github.com/frido22/cellclaw ✓
- Latest commit: `40e019e Revise README with enhanced details and formatting`
- Licence: MIT (from README; attribution to be preserved)

## Working tree at session 1 start
- `AGENTS.md` — newly added (staged)
- `PlacementOS_SKILL.md` — newly added (staged)
- `gradle/wrapper/gradle-wrapper.properties` — modified (Gradle 9.7.1 upgrade)

## Existing source structure (Main)
All Kotlin files in `app/src/main/kotlin/com/cellclaw/`:
- `CellClawApp.kt` — Application class
- `agent/` — AgentLoop, AutonomyPolicy, HeartbeatDetector/Manager/Prompt, TaskQueue
- `approval/` — ApprovalPolicy, ApprovalQueue, BiometricGate
- `config/` — AppConfig, Identity, SecureKeyStore
- `di/` — AppModule (Hilt)
- `memory/` — ConversationStore, MemoryDb, SemanticMemory
- `provider/` — Anthropic, Gemini, OpenAI, OpenRouter providers + Provider interface + ProviderManager + ToolSchema
- `scheduler/` — ScheduledTaskDao/Entity/Worker
- `service/` — AccessibilityBridge, CellClawAccessibility, Notification/Tile/Main service, overlay/, receivers/
- `skills/` — SkillParser, SkillRegistry
- `tools/` — 25+ tool files including ScriptExecTool (RISKY), SmsTool, PhoneTool, CameraTool, LocationTool, ContactsTool
- `ui/` — MainActivity, screens/ (Approval, Chat, Settings, Setup, Skills), theme/, viewmodel/
- `voice/` — VoiceManager
- `wakeword/` — MfccExtractor, WakeWordDetector

## Accessibility service
- `CellClawAccessibility.kt` — present; uses `android.permission.BIND_ACCESSIBILITY_SERVICE`
- Runs in separate `:a11y` process

## Tool registry (AppModule.kt)
All tools registered (line 98-119). Risky tools for Elixir profile to REMOVE from registry:
- SMS: `smsRead`, `smsSend`
- Phone: `phoneCall`, `phoneLog`
- Camera: `cameraSnap`, `cameraRecord`
- Location: `location`
- Contacts: `contactsSearch`, `contactsAdd`
- Shell: `scriptExec` (uses ProcessBuilder — confirmed dangerous)

## Permissions in manifest
Has: READ_SMS, SEND_SMS, RECEIVE_SMS, READ_PHONE_STATE, CALL_PHONE, READ_CALL_LOG, READ_CONTACTS, WRITE_CONTACTS, READ_CALENDAR, WRITE_CALENDAR, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, CAMERA, RECORD_AUDIO, BODY_SENSORS.
Decision: Keep in manifest for now (CellClaw compatibility); remove from ToolRegistry in M0.3. Will revisit at M12 hardening.

## Security scan
- `ScriptExecTool.kt:30` — `ProcessBuilder("sh", "-c", command)` — will be removed from registry (M0.3)
- No Firebase analytics SDKs found in build.gradle.kts
- No hardcoded API keys in source (key loaded from local.properties via BuildConfig)
- `usesCleartextTraffic` not found in manifest (good)
- No trust-all TLS code found

## Build result
- `.\gradlew.bat assembleDebug` → EXIT 1
- Gradle 9.7.1 incompatibility: deprecated kotlinOptions, aaptOptions, unresolved exec/commandLine in enableAccessibility task
- Fix required: M0.2

## Java environment
- Java 23 present at `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`
- Android SDK presence unconfirmed (build fails at Kotlin compilation, before SDK tools run)
