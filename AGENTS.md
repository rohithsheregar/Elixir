# AGENTS.md: Elixir (read this first, every session, every tool)

**Project:** Elixir is a personal Android placement-application assistant built on the CellClaw repo in this folder. Flow: JD -> eligibility -> tailored resume PDF -> supervised Google Form fill + resume upload -> user-approved submit. Full product rules live in the PlacementOS skill file (find it with `git ls-files | grep -i skill`).

**Naming:** "Elixir" is the display name only. Keep the Kotlin package `com.cellclaw` until milestone M12 so the build is never broken by a rename.

## 0. Read this twice
You are one of several AI agents that will work on this repo (different tools, accounts, sessions). You can be cut off at any moment (credits, context limit, crash). **The repo is the memory, not the chat.** Anything not committed and written into `docs/HANDOFF.md` is lost.

Source-of-truth order when things disagree: (1) actual code, git history and test output; (2) the PlacementOS skill file; (3) `docs/HANDOFF.md`, `docs/PLAN.md`, `docs/DECISIONS.md`, `docs/TESTLOG.md`; (4) the prompt you were given. Notes can be stale. If notes and reality disagree, trust reality, fix the notes, and log the discrepancy in `docs/DECISIONS.md`.

## 1. Session start ritual (mandatory, in this order)
1. Read this file, then the PlacementOS skill file.
2. Read `docs/HANDOFF.md`, the unchecked items plus last 5 checked items of `docs/PLAN.md`, the last 40 lines of `docs/TESTLOG.md`, and the headings of `docs/DECISIONS.md`.
3. Run `git status --short --branch`, `git log --oneline -15`, `git tag --list 'cp-*' | tail -3`.
4. If the working tree is dirty, **never discard it**. Inspect `git diff --stat`. If it matches HANDOFF's IN PROGRESS step, continue it. Otherwise commit it as `WIP: recovered from previous session` and verify it before building on it.
5. Run the verify command listed in HANDOFF. If it fails but the last checkpoint was green, the failure is the first task.
6. Add a line to HANDOFF's session log: session number, tool/model name, date, start commit hash.
7. Start the loop (section 2). Do not ask the user for permission to continue.

## 2. The loop (repeat until done or a stop condition in section 6)
Take the first unchecked step in `docs/PLAN.md` whose dependencies are done.
1. **Intent:** write `IN PROGRESS: <step id>, plan, expected files` in HANDOFF and save it.
2. **Tests first or alongside:** the step's acceptance test is named in PLAN.
3. **Implement the smallest change** that satisfies the step.
4. **Verify:** run the step's tests, the full unit suite, and `./gradlew assembleDebug` if Android code changed. Record the exact command, exit status and a short output summary in `docs/TESTLOG.md`.
5. **Pass:** commit as `[<step id>] <summary>`, tick PLAN, update HANDOFF (last green commit, NEXT ACTION). If the step ends a milestone, tag it `cp-<milestone>-<n>`.
6. **Fail:** up to 3 attempts, each with a different hypothesis, each logged. Still failing: mark the step `BLOCKED` in PLAN with the evidence, restore the last green state without losing anything (commit the attempt to a `wip/<step id>` branch), then take the next independent step. If none exist, stop (section 6).
7. **Continue immediately** to the next step. Do not end your turn at the end of a step.

Every 3 steps, or whenever the conversation gets long, re-read HANDOFF and confirm it matches reality. Keep HANDOFF under 80 lines by moving old content to `docs/HISTORY.md`.

## 3. Checkpoint rules (cut-off safety)
- Never hold more than one step of uncommitted work. If a step runs past about 20 minutes or 10 files, commit `WIP: <step id> <what is done / what remains>` and update HANDOFF.
- HANDOFF's **NEXT ACTION** must be one concrete instruction a stranger can execute in 2 minutes (file, function, test name).
- Never force-push, rewrite published history, delete branches or tags, or run `git reset --hard` over work you did not make.
- Any non-obvious choice goes into `docs/DECISIONS.md` (one short paragraph: choice, why, alternatives).
- Never commit secrets: API keys, keystores, `local.properties`, real personal data. Test data is synthetic only.

## 4. Testing standards
- Pure logic (profile, JD parser, eligibility, resume builder, tracker state machine, field matcher) needs JVM unit tests with synthetic data.
- Android-dependent code goes behind interfaces so the logic is testable. Use Robolectric or instrumented tests only if the environment supports them.
- Accessibility, Chrome and real Google Form flows **cannot be verified without a physical device**. Write the steps into `docs/DEVICE_TESTS.md`, mark them `NEEDS_USER_DEVICE`, and never claim they pass.
- Allowed TESTLOG statuses: `PASS`, `FAIL`, `NOT RUN (reason)`, `NEEDS_USER_DEVICE`. Write `PASS` only for something you ran in this session and saw succeed.
- Before ticking any step, run the full unit suite to catch regressions.
- A step is done only when (a) reachable from the real UI path, (b) tested through the real call path not just a mock, and (c) a device result is recorded or marked NEEDS_USER_DEVICE.

## 5. Honesty and correctness
- "No mistakes" means **no false claims**. Say what you ran, what you did not, and what you do not know.
- Open a file before referencing its classes. Do not invent APIs. Check dependency versions in Gradle. If unsure about an Android API, read the current official docs.
- Do not refactor unrelated CellClaw code. For Elixir, disable (do not delete) risky tools: SMS, calls, shell, camera, location, contacts. Keep the MIT licence and attribution.
- Obey the skill file's hard rules: never fabricate profile facts, user approves the final Submit, no CAPTCHA/OTP bypass.

## 6. Stop and wait (write `WAITING_ON_USER` plus the exact ask in HANDOFF)
- Real API key, real profile data, device testing, or Google sign-in is needed.
- Before touching any real company form. It must work on a dummy form first and the user must approve.
- Any destructive or irreversible action, or a decision the skill file reserves for the user.

While waiting on the user, keep working on independent steps. Stop only when nothing independent is left.

## 7. Session end ritual (do it early if credits or context seem low)
1. Finish the step or make a `WIP:` commit.
2. Update HANDOFF: state, last green commit, IN PROGRESS, NEXT ACTION, blockers, verify command.
3. Final message, 10 lines max: what changed, test statuses, next action.

## 8. Tool differences
Sandboxes differ across Antigravity, Codex and Cursor. If the Android SDK is missing, still write code and JDK-runnable unit tests, and mark builds `NOT RUN (no SDK)`. Never fake a build result.
