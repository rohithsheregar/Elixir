# DEVICE_TESTS.md — Elixir Project

Tests that require a physical Android device. These CANNOT be verified by the agent; they require the user to perform them manually.

Mark as `NEEDS_USER_DEVICE`. Never claim PASS without seeing the device output.

---

## M8 — Accessibility Session Framework

### DT-M8.1 — Dummy form fill end-to-end (Static HTML Mock Harness)
**Status:** NEEDS_USER_DEVICE
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

---

## M12 — Hardening

### DT-M12.1 — Real Google Form (user must approve before testing)
**Status:** NEEDS_USER_DEVICE
**WAITING_ON_USER:** User must choose a safe test form and approve before agent touches any real company form.
**Steps:** (Same as DT-M8.1 but with a real Google Form against a real profile)
