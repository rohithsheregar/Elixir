# HANDOFF.md — Elixir Project

## State
M3 in progress — M2 complete and awaiting the `cp-m2-1` milestone checkpoint. M3.1 is next.

## Session Log
| # | Tool/Model | Date | Start Commit |
|---|-----------|------|-------------|
| 1 | Antigravity / Claude Sonnet 4.6 (Thinking) | 2026-10-22 | 40e019e |
| 2 | Antigravity / Claude Sonnet 4.6 (Thinking) | 2026-10-22 | d9b2c9b |
| 3 | Codex / GPT-5.6 | 2026-10-22 | f3e9cbb |
| 4 | Codex / GPT-5.6 | 2026-10-22 | f3e9cbb |
| 5 | Codex / GPT-5.6 | 2026-10-22 | d6caf43 |
| 6 | Codex / GPT-5.6 | 2026-10-22 | 87c0b0f |
| 7 | Codex / GPT-5.6 | 2026-10-22 | e14fea5 |
| 8 | Codex / GPT-5.6 | 2026-10-22 | 0b9d73d |
| 9 | Codex / GPT-5.6 | 2026-10-24 | afb0f48 |

## Last Green Commit
4b4629f — [M2.5] verify candidate profile milestone

## IN PROGRESS
M3.1 — Define typed JD requirement models with source snippets and confidence, plus JVM model tests.

## NEXT ACTION
Create `app/src/main/kotlin/com/cellclaw/jd/JdRequirement.kt` and `JdRequirementModelTest.kt` with only synthetic facts.

## Blockers
None. Gradle must be invoked with the scoped `JDK_JAVA_OPTIONS` setting in the Verify Command on this host.

## Verify Command
`$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'; .\gradlew.bat --no-daemon testDebugUnitTest`

## WAITING_ON_USER
- Real profile data (name, email, CGPA, etc.) — needed for M2.
- Device testing — needed for M8+.
- Real company form approval — needed before M9.
