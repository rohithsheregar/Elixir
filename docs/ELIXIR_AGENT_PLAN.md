# ELIXIR_AGENT_PLAN.md: From placement assistant to Jarvis-style phone agent

Written 2026-11-02 from a read of the codebase. Source of truth order in AGENTS.md still applies: code and test output beat this file.

## 1. Where the codebase stands

ELIXIR is a fork of CellClaw (Kotlin, Compose, Hilt, Room, WorkManager) with two layers:

- **Generic agent brain (CellClaw):** `AgentLoop` (tool-calling loop), `ProviderManager` (Anthropic, OpenAI, Gemini, OpenRouter), `ToolRegistry`, `SemanticMemory`, `SchedulerTool` + `ScheduledTaskWorker`, heartbeat system, overlay bubble, `VoiceManager`, markdown skills, and `CellClawAccessibility` (tap, type, swipe, scroll, read_screen, screenshot).
- **Deterministic placement pipeline (Elixir):** JdParser -> EligibilityEngine -> ResumeBuilder/PdfGenerator -> FieldClassifier/FieldFiller -> ResumeUploader -> ApprovalToken/SubmitSupervisor -> ApplicationTracker, coordinated by `AutomationOrchestrator`. It runs beside the agent loop and is not exposed to it as tools.

Status per docs: 235 unit tests pass; no device run has happened (R19 open); Audit 2 scored about 66%.

## 2. Findings that block the Jarvis goal

1. `Identity.kt` still says "You are CellClaw" and lists SMS, calls, camera and shell abilities that are disabled. The model will promise things it cannot do.
2. `AutonomyPolicy` initialises to FULL_AUTO and `getPolicy()` returns AUTO for unknown tools. Should be deny-by-default.
3. `accessibility_config.xml` has `typeAllMask` and no `packageNames`. The "Chrome only" scope exists only in code.
4. `browser.search` scrapes DuckDuckGo HTML with a regex. No page-fetch tool, no citations.
5. `MemoryDb` uses `fallbackToDestructiveMigration()`. A schema change would wipe the application tracker.
6. No alarm, timer or reminder tool. `settings.get` only reads battery, Wi-Fi, brightness. `schedule.manage` has a 15-minute minimum.
7. No 429 backoff, call budget, or context compaction (R14). `conversationHistory` grows unbounded.
8. Resume tailoring only reorders confirmed facts. No AI rewording yet.
9. 70 commits were unpushed at Audit 2.

## 3. Android reality check

- Alarms, timers, reminders: easy via `AlarmClock` intents and exact alarms.
- Battery "optimisation": monitor, advise, adjust brightness, deep-link to settings. Third-party apps cannot kill apps or flip most system toggles without root or an ADB-granted permission.
- Rule: official APIs and intents first, accessibility second (best-effort).

## 4. Principles

- Do not rewrite. CellClaw loop stays the brain; the placement pipeline becomes tools.
- Zero-fabrication stays for everything resume and form related. Confirmed profile = ground truth; agent memory = soft preferences, kept separate.
- Three risk tiers: READ, REVERSIBLE_WRITE, CONSEQUENTIAL_WRITE. Only consequential actions need a direct user tap, using the generalised ApprovalToken.
- Page text, JD text and tool output are data, never instructions.

## 5. Phases

### Phase 0: make it real (about 1 week)
Run the dummy-form device test (R19) and fix failures; push to GitHub; rewrite the system prompt to match the registered tools; deny-by-default autonomy; accessibility `packageNames`; real Room migrations.

### Phase 1: brain upgrade
Risk-tier model and generalised approval token; AiClient wrapper with 429 backoff, per-task call budget, JD-hash cache (R14); context compaction and streaming; memory v2 separate from CandidateProfile; expose placement pipeline as agent tools (`jd.parse`, `eligibility.evaluate`, `resume.generate`, `application.prepare_review`).

### Phase 2: internet research
Proper search API (or Gemini grounding); `web.fetch` with readability extraction; research skill returning summaries with source links; save findings to notes.

### Phase 3: phone control
`alarm.set`, `timer.set`, `reminder.create` (exact alarm + notification + optional calendar event); battery monitor with advice and charge triggers; notification triage; voice in/out; overlay bubble; re-enable SMS, calls, contacts one at a time, each with its own runtime permission and approval.

### Phase 4: universal browser agent
In-app WebView agent browser with DOM-level tools (snapshot of interactive elements, click by ref, type, select, upload). Chrome + accessibility kept only for signed-in flows. Generalise FieldClassifier beyond Google Forms (Greenhouse, Lever, Workday-style). Requires a decision (see section 6).

### Phase 5: career copilot
Role-specific resume tailoring with AI rewording constrained by the evidence map (R15 to R18); cover letters; approved-answers library; follow-up reminders from the tracker.

### Phase 6: proactive Jarvis feel
Morning briefing (skill exists), event triggers, optional wake word with explicit microphone consent.

## 6. Open decisions for Rohith

- **WebView (blocks Phase 4):** PlacementOS skill rule forbids embedded WebViews. Recommendation: allow for research and public forms; keep Chrome for signed-in flows. Status: PENDING.
- **Default AI provider and monthly budget:** PENDING.
- **Re-enabling SMS/calls/contacts:** recommended only in Phase 3, behind approval. PENDING.

## 7. Known limits

Android will not allow arbitrary system control. Accessibility automation is fragile on custom widgets. Play Store accessibility-policy limits do not matter for personal sideloading but would matter if ever published.
