# DEVICE_TESTS.md — Elixir Project

Tests that require a physical Android device. These CANNOT be verified by the agent; they require the user to perform them manually.

Mark as `NEEDS_USER_DEVICE`. Never claim PASS without seeing the device output.

---

## M8 — Accessibility Session Framework

### DT-M8.1 — Dummy form fill end-to-end
**Status:** NEEDS_USER_DEVICE
**Prerequisites:**
- Device running Android 8.0+ (API 26)
- Elixir installed and accessibility service enabled
- Chrome installed on device
- A dummy Google Form (or static HTML mock) created with: text input, dropdown, radio buttons, checkboxes, date picker, file upload

**Steps:**
1. Open Elixir app
2. Create a test candidate profile with synthetic data (name: "Test User", email: "test@example.com", etc.)
3. Start a new application: paste the dummy form URL
4. Observe eligibility check (all synthetic, so all fields should match)
5. Generate dummy resume PDF — verify it is text-selectable
6. Tap "Start Form Fill Session" — app opens Chrome with the dummy form URL
7. Observe Elixir filling fields: verify each value matches the profile, read back is correct
8. Verify "Needs your input" list for unmatched fields
9. On the review screen: verify all filled values, resume filename and hash are shown
10. Tap Approve — verify one-time token is created
11. Tap Submit in Chrome (via Elixir) — verify tracker updates to SUBMITTED_CONFIRMED or SUBMISSION_UNKNOWN
12. Close session — verify foreground notification dismisses

**Pass criteria:** All fields filled correctly with no fabricated data; submission only after user approve; tracker updated.

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

---

## M12 — Hardening

### DT-M12.1 — Real Google Form (user must approve before testing)
**Status:** NEEDS_USER_DEVICE
**WAITING_ON_USER:** User must choose a safe test form and approve before agent touches any real company form.
**Steps:** (Same as DT-M8.1 but with a real Google Form against a real profile)
