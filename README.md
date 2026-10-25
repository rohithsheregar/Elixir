# Elixir

**Personal Android Placement-Application Assistant**

Elixir is an on-device personal placement and job application assistant built on the CellClaw Android core. It automates repetitive placement tasks—evaluating job descriptions, generating truthfully tailored resume PDFs, filling multi-field Google Forms via accessibility services, and tracking application statuses—while enforcing an uncompromising zero-fabrication privacy and approval model.

> **Attribution:** Elixir is developed on the open-source [CellClaw](https://github.com/jordanthejet/cellclaw) repository. Built and inspired by members of the Harvard, MIT, and Sundai.Club communities, with inspiration from ZeroClaw. Retains the MIT License.

---

## What Does Elixir Do?

1. **Candidate Profile Ground Truth:**  
   Maintains confirmed facts about your education, degree, branch, CGPA, graduation year, technical skills, projects, and portfolio links. Every bullet point and form field is strictly grounded in these facts. **Zero hallucinations or fabricated claims.**

2. **Job Description & Eligibility Ingestion:**  
   Parses pasted job descriptions, extracts eligibility criteria (minimum CGPA, graduation years, accepted degrees and branches), evaluates candidate profile facts, and generates an eligibility report (`ELIGIBLE`, `NOT_ELIGIBLE`, or `UNKNOWN`).

3. **Tailored Resume PDF Generation:**  
   Pure JVM, text-selectable A4 PDF generator that orders sections and bullets to highlight relevant skills for the target company/role while maintaining an on-screen **Evidence Integrity Map** linking each line back to a verified profile fact.

4. **Supervised Google Form Automation:**  
   Uses an Android Accessibility Service (`CellClawAccessibility`) to identify, classify, and fill form fields in Google Chrome (text, dropdowns, radios, checkboxes, dates). Sensitive or unmatched questions are routed to a "Needs your input" list rather than guessed.

5. **Narrowly Scoped Resume Upload:**  
   Exposes only the generated company-specific PDF via a scoped `FileProvider` (`content://com.cellclaw.fileprovider/resumes/...`). Pre-upload checks ensure the file exists, has valid `%PDF-` magic bytes, and matches the tracker record's SHA-256 hash.

6. **One-Time Cryptographic Approval & Submission:**  
   Submission requires an explicit user tap on the Review screen. This issues an ephemeral `ApprovalToken` cryptographically bound to the company, role, form domain, form state hash, and resume hash. The token is consumed on single tap and automatically revokes on navigation, field edit, timeout, or cancellation.

7. **Application Tracker:**  
   Maintains reactive application states: `DRAFT` &rarr; `READY_FOR_REVIEW` &rarr; `SUBMITTING` &rarr; `SUBMITTED_CONFIRMED` / `SUBMISSION_UNKNOWN` / `FAILED`. On submission, reads post-submit confirmation pages and records response reference IDs. Never performs unattended automatic re-submissions.

---

## Security & Privacy Policy

- **Zero-Fabrication Contract:** No synthetic claims or inflated facts are ever placed on your resume or entered into job forms.
- **The Final Tap Belongs to You:** The assistant never submits an application without explicit user review and approval.
- **HTTPS Only & Cleartext Prohibited:** Cleartext HTTP traffic is explicitly disallowed via `network_security_config.xml`.
- **Restricted Permissions:** Risky permissions (SMS, phone calls, camera, contacts, microphone, location) are removed from the active manifest. Only internet, accessibility service, and scoped file sharing are declared.
- **Private Encrypted Storage:** API keys and profile facts are encrypted on-device via the Android Keystore (AES-256-GCM). `android:allowBackup="false"` prevents cloud backup extraction.
- **No Telemetry:** Zero external tracking, analytics, or background telemetry.

---

## Setup & Getting Started

### Requirements
- Android 8.0+ (API level 26+)
- Google Chrome installed (default browser for supervised form filling)
- (Optional) AI Provider API key for JD extraction (Anthropic, OpenAI, Google Gemini, or OpenRouter), encrypted on-device.

### Building from Source

```powershell
# In PowerShell on Windows:
$env:JDK_JAVA_OPTIONS='-Djdk.net.unixdomain.tmpdir=C:\Temp'
.\gradlew.bat --no-daemon assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Initial Device Configuration

1. **Install APK** onto your physical Android device or emulator (`adb install -r app-debug.apk`).
2. **Profile Setup:** Open Elixir &rarr; Profile tab. Add your confirmed profile facts: Name, Email, College, Degree, Branch, CGPA, Year of Graduation, Skills, and Portfolio Links.
3. **Accessibility Permission:** Navigate to Android Settings &rarr; Accessibility &rarr; Enable `CellClawAccessibility` (labeled **Elixir Accessibility Service**).
4. **Test Harness:** Run through the end-to-end dummy form checklist in [`docs/DEVICE_TESTS.md`](docs/DEVICE_TESTS.md) using the packaged static mock form at `docs/dummy_google_form.html`.

---

## How to Apply

1. **New Application:** Paste the company name, role, and job description text (or link).
2. **Review Eligibility:** View the criterion-by-criterion breakdown card. Confirm eligibility status.
3. **Inspect Tailored Resume:** Preview the generated PDF, verify the SHA-256 hash, and check the Evidence Integrity Map.
4. **Supervised Form Fill:** Tap "Start Form Session". Elixir opens the link in Chrome, inspects DOM nodes, classifies questions, and populates answers from confirmed facts.
5. **Final Review & Submit:** Review all filled inputs, affirm declarations, and tap "Approve & Submit". The one-time token authorizes a single submit tap and confirms your response reference code.

---

## Testing & Verification

Elixir features a comprehensive pure JVM test suite alongside device testing specifications:
- **Unit Suite:** `.\gradlew.bat testDebugUnitTest` (tests profile facts, JD parser, eligibility engine, PDF generator, tracker state machine, accessibility session, field classifier, field filler, resume uploader, and approval token supervisor).
- **Device Tests:** Detailed in [`docs/DEVICE_TESTS.md`](docs/DEVICE_TESTS.md) covering accessibility node interactions, file picker integration, and confirmation parsing.

---

## License

This project is licensed under the [MIT License](LICENSE).
Portions copyright (c) original CellClaw contributors.
