# DEVICE_TESTS.md — Elixir Project

Tests that require a physical Android device. These CANNOT be verified by the agent; they require the user to perform them manually on an actual Android device or Google Chrome environment.

Mark as `NEEDS_USER_DEVICE`. Never claim `PASS` without seeing physical device output.

---

## Physical Device Testing Checklist

| Test ID | Milestone | Feature | Target Component | Status | Verified By |
|---------|-----------|---------|------------------|--------|-------------|
| **DT-M8.1** | M8.3 | Static Mock Google Form Fill End-to-End | Chrome + `CellClawAccessibility` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-M10.1** | M10.1 | Scoped System Document Picker | Android FileProvider (`resumes/`) | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-M10.2** | M10.2 | Post-Upload Attachment & Visual Confirmation | `PostUploadVerifier` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-M11.1** | M11.1 | Approval Token Binding & Direct User Tap | `ApprovalTokenManager` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-M11.2** | M11.2 | Supervised Submit & Receipt Detection | `SubmitSupervisor` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-R10** | R10 | RunScreen live status, notification Pause/Stop, CAPTCHA handoff | `RunScreen`, `CellClawService` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-R11** | R11 | ResultScreen confirmation receipt, manual confirmation, failure retry | `ResultScreen`, `ResultViewModel` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-R12** | R12 | Needs-Input modal and interactive sensitive/missing field resolution | `ReviewScreen`, `ReviewViewModel` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-R13** | R13 | Eligibility hard stop and explicit user override gate | `EligibilityReportScreen`, `EligibilityReportViewModel` | `NEEDS_USER_DEVICE` | User / Physical Device |
| **DT-M12.1** | M12 | Live Google Form Supervised Validation | User-Approved Test Form | `WAITING_ON_USER` | User / Physical Device |

---

## Test Execution Protocol

1. **Host Setup:** Start the local static mock harness:
   ```bash
   python -m http.server 8080 --directory docs
   ```
2. **Device Connection:** Connect Android device via Wi-Fi or USB adb reverse:
   ```bash
   adb reverse tcp:8080 tcp:8080
   ```
3. **Run Tests:** Follow the procedures below step-by-step. Record pass/fail notes for each test.

---

## Detailed Test Procedures
**Test Harness Location:** [docs/dummy_google_form.html](file:///c:/Users/Rohith%20Sheregar/OneDrive/Documents/cellclaw/docs/dummy_google_form.html) (also packaged in `app/src/main/assets/dummy_google_form.html`)

**Prerequisites:**
- Device running Android 8.0+ (API 26) with USB debugging or local network access
- Elixir installed and `CellClawAccessibility` service enabled in Accessibility settings
- Chrome installed and set as default browser
- Serve the test harness over local Wi-Fi or adb reverse port forwarding:
  - Option A: Run `python -m http.server 8080 --directory docs` and connect device to `http://<host-ip>:8080/dummy_google_form.html`
  - Option B: Use `adb reverse tcp:8080 tcp:8080` and open `http://localhost:8080/dummy_google_form.html` (or hosted HTTPS tunnel via ngrok / GitHub Pages for strict HTTPS enforcement)

**Steps:**
1. Open Elixir app
2. Create or verify candidate profile facts (name, email, phone, institution, degree, branch, CGPA, graduation year)
3. Start a new application: paste the test harness form URL
4. Observe eligibility report screen (all criteria verified against profile facts)
5. Generate tailored resume PDF — inspect preview and evidence map on ResumePreviewScreen
6. Tap "Approve Resume" to navigate to ReviewScreen
7. Verify ReviewScreen displays: target domain, attached resume name & SHA-256 hash, all synthesized field values with 100% fact badges, and zero hallucinations
8. Affirm declarations and tap "Approve & Submit" (starts supervised `AccessibilitySession`)
9. Elixir launches Chrome via `BrowserOpener` intent with the form URL
10. `AccessibilitySession` inspects Chrome DOM, verifies package scoping (`com.android.chrome`), and maps fields
11. Observe field filling: text, dropdown (Degree, Branch), radio (relocation), checkboxes (skills), date picker
12. For file upload: system file picker is triggered; verify only the generated PDF is exposed and selected
13. User supervises final Submit tap (or taps Submit directly in Chrome)
14. Confirmation card displays with generated Reference ID (e.g. `CONF-TEST-XXXXXX`)
15. Return to Elixir TrackerScreen: verify status transitions to `SUBMITTED_CONFIRMED` with reference number recorded

**Pass criteria:** All form fields filled accurately from confirmed facts; resume PDF attached cleanly; submission approved by user; tracker updated to `SUBMITTED_CONFIRMED`.

---

## M10 — Resume Upload

### DT-M10.1 — File picker and upload
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Reach file-upload field in dummy form
2. Elixir triggers system file picker showing only the generated company PDF
3. Select the file
4. Verify form shows correct filename attached
5. Verify hash in tracker matches the uploaded file

### DT-M10.2 — Post-upload verify (attached filename confirmation)
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Complete system file picker upload of the tailored resume PDF.
2. Observe Chrome DOM reading through AccessibilitySession / PostUploadVerifier.
3. If the form displays the uploaded filename, verify that Elixir confirms the attachment automatically without manual prompts.
4. If the form displays a generic indicator (e.g. icon or "1 file selected") without the filename text, verify that Elixir displays a visual confirmation prompt asking the user to confirm the attached file before proceeding.
5. If an unexpected file name is visible, verify that Elixir raises a mismatch error and halts the submission flow.

---

## M11 — Review and Submit

### DT-M11.1 — Approval flow
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Review screen shows: company, role, domain, every filled value, unanswered fields, resume name+hash
2. Tap Approve (one-time token created)
3. Submit tapped once
4. Confirmation page detected: tracker = SUBMITTED_CONFIRMED
5. No confirmation page: tracker = SUBMISSION_UNKNOWN, no auto-resubmit

### DT-M11.2 — Supervised Submit and Confirmation Receipt Detection (Dummy Form)
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Open Elixir and complete application review for the dummy Google form harness (`docs/dummy_google_form.html`).
2. Tap "Approve & Submit" on ReviewScreen to generate a cryptographically bound `ApprovalToken`.
3. In Chrome, observe `SubmitSupervisor` performing exactly one tap on the verified Submit button.
4. Verify `ConfirmationDetector` parses the post-submit DOM:
   - When the confirmation card (`Your response has been recorded.`) and reference (`CONF-TEST-XXXXXX`) are present, verify Elixir updates the tracker record to `SUBMITTED_CONFIRMED` with the reference number stored.
   - If the network is disconnected or the page times out without confirmation, verify Elixir updates the tracker to `SUBMISSION_UNKNOWN` and strictly refrains from auto-retrying.
5. Verify on TrackerScreen that the application record displays the confirmed status and reference code.

### DT-R10 — RunScreen Supervised Automation, Pause/Resume & Foreground Notification Controls
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Navigate from ReviewScreen to RunScreen after approving an application.
2. Verify RunScreen displays live steps (Opening browser, scanning form, filling fields, uploading resume, awaiting submit approval).
3. Verify persistent foreground notification "Elixir - Supervised automation active" appears with Pause and Stop action buttons.
4. Tap Pause in the notification or on RunScreen: verify automation halts and status displays Paused.
5. Tap Resume: verify automation continues.
6. Trigger CAPTCHA/login handoff: verify banner appears instructing manual completion, and resumes once dismissed.

### DT-R11 — ResultScreen Outcome, Confirmation Reference & Retry Flow
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Complete form submission run and observe automatic transition to ResultScreen (`result/{appId}`).
2. Verify display of confirmation reference receipt (e.g. `CONF-TEST-XXXXXX`).
3. For ambiguous or timed-out submissions (`SUBMISSION_UNKNOWN`), verify manual reference entry box and "Confirm Submission" CTA updates the application to `SUBMITTED_CONFIRMED`.
4. For failed runs, verify "Retry Application Review & Submit" button resets the application state to `READY_FOR_REVIEW` and routes back to ReviewScreen.
5. Tap "Return to Application Tracker": verify return to TrackerScreen with updated status chips.

### DT-R12 — Needs-Input Modal & Interactive Sensitive/Missing Field Resolution
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Open an application in ReviewScreen that has missing profile fields (e.g. phone or branch).
2. Verify "Missing Profile Fields" warning card displays with "Resolve Missing Questions" button.
3. Verify "Needs Your Input" card displays showing count of unresolved sensitive/missing questions.
4. Tap "Resolve Missing Questions" or "Review & Answer" to open NeedsInputModal dialog.
5. In the modal, provide answers for the questions and tap Save for each.
6. Verify status transitions to Resolved and the field is added to Form Values to Fill with 100% Fact badge.
7. Tap "Approve & Submit": verify ApprovalToken issues binding the user-resolved values.

### DT-R13 — Eligibility Hard Stop & Explicit User Override Gate
**Status:** NEEDS_USER_DEVICE
**Steps:**
1. Open an application where candidate does not meet mandatory criteria (status `NOT_ELIGIBLE`).
2. Verify EligibilityReportScreen renders "Eligibility Hard Stop" warning banner.
3. Verify "Proceed to Tailored Resume (Override Required)" CTA is strictly disabled.
4. Check the explicit override checkbox ("I understand the eligibility risks...").
5. Verify button unlocks as "Proceed to Tailored Resume (Overridden)" in red tone.
6. Tap the unlocked button: verify navigation proceeds to ResumePreviewScreen.

---

### DT-U1 — Elixir Design System Tokens (Theme & Palette)
**Status:** NEEDS_USER_DEVICE
**Goal:** Verify on physical device that Elixir theme applies cleanly in both light and system dark modes without visual regressions or unreadable contrast.
**Steps:**
1. Launch Elixir in system light mode: verify indigo brand primary, clean slate neutral backgrounds, and legible typography scale.
2. Toggle Android system dark mode: verify deep slate background, luminous indigo accent, and legible on-surface typography.
3. Navigate across screens: verify absence of glaring pure-white boxes or unreadable text.

---

## M12 — Hardening

### DT-M12.1 — Real Google Form (user must approve before testing)
**Status:** NEEDS_USER_DEVICE
**WAITING_ON_USER:** User must choose a safe test form and approve before agent touches any real company form.
**Steps:** (Same as DT-M8.1 but with a real Google Form against a real profile)

